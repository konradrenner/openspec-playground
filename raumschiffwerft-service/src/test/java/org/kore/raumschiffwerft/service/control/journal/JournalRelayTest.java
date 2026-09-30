package org.kore.raumschiffwerft.service.control.journal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.agroal.api.AgroalDataSource;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

    private final AgroalDataSource dataSource = mock(AgroalDataSource.class);
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
        relay = new JournalRelay(dataSource, outboxRepository, versand, sdk, 50);
    }

    @AfterEach
    void sdkSchliessen() {
        sdk.close();
    }

    private Journaleintrag eintrag(long id, String traceparent) {
        return new Journaleintrag(id, "11111111-1111-1111-1111-111111111111",
                "{\"auftragsId\": \"%d\"}".formatted(id), traceparent);
    }

    @Test
    void versendetJedenEintragUndMarkiertErstDanach() throws Exception {
        Connection verbindung = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(verbindung);
        Journaleintrag erster = eintrag(1, TRACEPARENT);
        Journaleintrag zweiter = eintrag(2, TRACEPARENT);
        when(outboxRepository.ungesendeteLesen(any(), anyInt()))
                .thenReturn(List.of(erster, zweiter));

        relay.relay();

        InOrder reihenfolge = inOrder(versand, outboxRepository, verbindung);
        reihenfolge.verify(versand).senden(erster.auftragsId(), erster.payload());
        reihenfolge.verify(outboxRepository).gesendetMarkieren(any(), anyLong(), any());
        reihenfolge.verify(versand).senden(zweiter.auftragsId(), zweiter.payload());
        reihenfolge.verify(outboxRepository).gesendetMarkieren(any(), anyLong(), any());
        reihenfolge.verify(verbindung).commit();
    }

    @Test
    void relaySpanTraegtLinkAufDenGespeichertenTraceparent() throws Exception {
        Connection verbindung = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(verbindung);
        Journaleintrag eintrag = eintrag(1, TRACEPARENT);
        when(outboxRepository.ungesendeteLesen(any(), anyInt()))
                .thenReturn(List.of(eintrag));
        AtomicReference<SpanContext> kontextBeimVersand = new AtomicReference<>();
        doAnswer(aufruf -> {
            kontextBeimVersand.set(Span.current().getSpanContext());
            return null;
        }).when(versand).senden(anyString(), anyString());

        relay.relay();

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
    void ohneGespeichertenTraceparentGibtEsKeinenLink() throws Exception {
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        when(outboxRepository.ungesendeteLesen(any(), anyInt()))
                .thenReturn(List.of(eintrag(1, null)));

        assertDoesNotThrow(() -> relay.relay());

        ReadableSpan relaySpan = beendeteSpans.stream()
                .filter(span -> span.getName().equals("journal.relay"))
                .findFirst().orElseThrow();
        assertTrue(relaySpan.toSpanData().getLinks().isEmpty());
    }

    @Test
    void sendefehlerRolltZurueckOhneCommit() throws Exception {
        Connection verbindung = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(verbindung);
        when(outboxRepository.ungesendeteLesen(any(), anyInt()))
                .thenReturn(List.of(eintrag(1, TRACEPARENT)));
        doThrow(new IllegalStateException("Kafka weg")).when(versand).senden(anyString(), anyString());

        assertDoesNotThrow(() -> relay.relay());

        verify(verbindung).rollback();
        verify(verbindung, never()).commit();
        verify(outboxRepository, never()).gesendetMarkieren(any(), anyLong(), any());
    }

    @Test
    void leereOutboxCommittetOhneVersand() throws Exception {
        Connection verbindung = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(verbindung);
        when(outboxRepository.ungesendeteLesen(any(), anyInt())).thenReturn(List.of());

        relay.relay();

        verify(versand, never()).senden(anyString(), anyString());
        verify(verbindung).commit();
    }

    @Test
    void datenbankfehlerWirftNicht() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Datenbank weg"));

        assertDoesNotThrow(() -> relay.relay());

        verify(versand, never()).senden(anyString(), anyString());
    }
}
