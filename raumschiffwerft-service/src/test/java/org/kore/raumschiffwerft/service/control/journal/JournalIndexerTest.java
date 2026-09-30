package org.kore.raumschiffwerft.service.control.journal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JournalIndexerTest {

    private final JournalIndex index = mock(JournalIndex.class);
    private final JournalIndexer indexer = new JournalIndexer(index);

    @Test
    void erfolgSpeichertDasDokumentUnterDerAuftragsId() {
        String payload = "{\"auftragsId\": \"1\"}";

        assertDoesNotThrow(() -> indexer.indexiere("id-1", payload));

        verify(index).speichern("id-1", payload);
    }

    @Test
    void indexierfehlerWirdWeitergereichtFuerKeinenCommit() {
        doThrow(new IllegalStateException("OpenSearch weg")).when(index).speichern(anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> indexer.indexiere("id-1", "{\"auftragsId\": \"1\"}"));
    }
}
