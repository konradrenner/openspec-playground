package org.kore.raumschiffwerft.service.control;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZielsystemwahlTest {

    private final Client openFeatureClient = mock(Client.class);
    private final Zielsystemwahl zielsystemwahl = new Zielsystemwahl(openFeatureClient);

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final Kaufauftrag kaufauftrag =
            new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 2, 4);

    @Test
    void planet4LiefertRebellion() {
        when(openFeatureClient.getStringValue(eq("zielsystem"), eq("imperium"), any(EvaluationContext.class)))
                .thenReturn("rebellion");

        assertEquals(Zielsystemtyp.REBELLION, zielsystemwahl.waehlen(auftragsId, kaufauftrag));
    }

    @Test
    void imperiumWirdDurchgereicht() {
        when(openFeatureClient.getStringValue(eq("zielsystem"), eq("imperium"), any(EvaluationContext.class)))
                .thenReturn("imperium");

        assertEquals(Zielsystemtyp.IMPERIUM,
                zielsystemwahl.waehlen(auftragsId,
                        new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 2, 42)));
    }

    @Test
    void ungueltigerWertFaelltMitWarnungAufImperiumZurueck() {
        when(openFeatureClient.getStringValue(eq("zielsystem"), eq("imperium"), any(EvaluationContext.class)))
                .thenReturn("mandalore");

        assertEquals(Zielsystemtyp.IMPERIUM, zielsystemwahl.waehlen(auftragsId, kaufauftrag));
    }

    @Test
    void providerfehlerFaelltMitWarnungAufImperiumZurueck() {
        when(openFeatureClient.getStringValue(eq("zielsystem"), eq("imperium"), any(EvaluationContext.class)))
                .thenThrow(new RuntimeException("Provider nicht erreichbar"));

        assertEquals(Zielsystemtyp.IMPERIUM, zielsystemwahl.waehlen(auftragsId, kaufauftrag));
    }

    @Test
    void flagWirdGenauEinmalProAufrufAusgewertet() {
        when(openFeatureClient.getStringValue(eq("zielsystem"), eq("imperium"), any(EvaluationContext.class)))
                .thenReturn("rebellion");

        zielsystemwahl.waehlen(auftragsId, kaufauftrag);

        verify(openFeatureClient, times(1)).getStringValue(eq("zielsystem"), eq("imperium"),
                any(EvaluationContext.class));
    }
}
