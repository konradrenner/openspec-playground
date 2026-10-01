package org.kore.raumschiffwerft.service.control.journal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalRelayTest {

    private static final String TRACEPARENT =
            "00-0af7651916cd43dd8448eb4c9cbbfda1-0af7651916cd43dd-01";
    private static final String TRACE_ID = "0af7651916cd43dd8448eb4c9cbbfda1";

    private final JournalOutboxRepository outboxRepository = mock(JournalOutboxRepository.class);
    private final JournalVersand versand = mock(JournalVersand.class);

    private final List<ReadableSpan> beendeteSpans = new CopyOnWriteArrayList<>();

    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(io.opentelemetry.sdk.trace.SdkTracerProvider.builder()
                    .addSpanProcessor(new SpanProcessor() {
                        @Override
                        public void onStart(Context parentContext, ReadWriteSpan span) {
                            // nur beendete Spans werden aufgezeichnet
                        }

                        @Override
                        public void onEnd(ReadableSpan span) {
                            beendeteSpans.add(span);
                        }

                        @Override
                        public boolean isStartRequired() {
                            return false;
                        }

                        @Override
                        public boolean isEndRequired() {
                            return true;
                        }
                    })
                    .build())
            .build();

    private JournalRelay relay;

    @BeforeEach
    void aufbauen() {
        relay = new JournalRelay(outboxRepository, versand, sdk, 50);
    }

    @AfterEach
    void sdkSchliessen() {
        sdk.close();
    }

    private Journaleintrag eintrag(long id, String traceparent) {
        return new Journaleintrag(id, "11111111-1111-1111-1111-111111111111",
                "{\"auftragsId\": \"%d\"}".formatted(id), traceparent);
    }

    /** Der Outbox-Stub ruft den Relay-Versand je Eintrag auf (wie die echte Implementierung). */
    private void relayMit(List<Journaleintrag> eintraege) {
        when(outboxRepository.versenden(anyInt(), any())).thenAnswer(aufruf -> {
            JournalOutboxRepository.Versand versand = aufruf.getArgument(1);
            for (Journaleintrag eintrag : eintraege) {
                versand.senden(eintrag);
            }
            return eintraege.size();
        });
        relay.relay();
    }

    @Test
    void versendetJedenEintragInEigenemRelaySpan() {
        Journaleintrag erster = eintrag(1, TRACEPARENT);
        Journaleintrag zweiter = eintrag(2, TRACEPARENT);

        relayMit(List.of(erster, zweiter));

        InOrder reihenfolge = inOrder(versand);
        reihenfolge.verify(versand).senden(erster.auftragsId(), erster.payload());
        reihenfolge.verify(versand).senden(zweiter.auftragsId(), zweiter.payload());
        assertEquals(2, beendeteSpans.stream()
                .filter(span -> span.getName().equals("journal.relay")).count());
    }

    @Test
    void relaySpanTraegtLinkAufDenGespeichertenTraceparent() {
        Journaleintrag eintrag = eintrag(1, TRACEPARENT);
        AtomicReference<SpanContext> kontextBeimVersand = new AtomicReference<>();
        doAnswer(aufruf -> {
            kontextBeimVersand.set(Span.current().getSpanContext());
            return null;
        }).when(versand).senden(anyString(), anyString());

        relayMit(List.of(eintrag));

        ReadableSpan relaySpan = beendeteSpans.stream()
                .filter(span -> span.getName().equals("journal.relay"))
                .findFirst().orElseThrow();
        assertEquals(1, relaySpan.toSpanData().getLinks().size());
        assertEquals(TRACE_ID,
                relaySpan.toSpanData().getLinks().get(0).getSpanContext().getTraceId());
        assertEquals(eintrag.auftragsId(), relaySpan.toSpanData().getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("durchlauferhitzer.auftrag.id")));
        // waehrend des Versands ist der Relay-Span aktuell (Basis fuer den Kafka-Header)
        assertEquals(relaySpan.getSpanContext(), kontextBeimVersand.get());
    }

    @Test
    void ohneGespeichertenTraceparentGibtEsKeinenLink() {
        assertDoesNotThrow(() -> relayMit(List.of(eintrag(1, null))));

        ReadableSpan relaySpan = beendeteSpans.stream()
                .filter(span -> span.getName().equals("journal.relay"))
                .findFirst().orElseThrow();
        assertTrue(relaySpan.toSpanData().getLinks().isEmpty());
    }

    @Test
    void sendefehlerWirftNicht() {
        when(outboxRepository.versenden(anyInt(), any()))
                .thenThrow(new IllegalStateException("Kafka weg"));

        assertDoesNotThrow(() -> relay.relay());

        verify(versand, never()).senden(anyString(), anyString());
    }

    @Test
    void leereOutboxVersendetNichtsUndWirftNicht() {
        relayMit(List.of());

        verify(versand, never()).senden(anyString(), anyString());
    }

    @Test
    void datenbankfehlerWirftNicht() {
        when(outboxRepository.versenden(anyInt(), any()))
                .thenThrow(new IllegalStateException("Datenbank weg"));

        assertDoesNotThrow(() -> relay.relay());

        verify(versand, never()).senden(anyString(), anyString());
    }
}
