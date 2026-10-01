package org.kore.raumschiffwerft.service.control;

import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

/**
 * Waehlt das Zielsystem per OpenFeature-Flag "zielsystem" (flagd,
 * Datei-Modus). Erlaubt sind nur imperium und rebellion; jeder andere
 * Wert und jeder Providerfehler faellt mit einer Warnung auf imperium
 * zurueck.
 */
@ApplicationScoped
public class Zielsystemwahl {


    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Zielsystemwahl.class.getName());

    private final Client openFeatureClient;

    @Inject
    public Zielsystemwahl(Client openFeatureClient) {
        this.openFeatureClient = openFeatureClient;
    }

    public Zielsystemtyp waehlen(AuftragsId auftragsId, Kaufauftrag kaufauftrag) {
        try {
            var kontext = new ImmutableContext(auftragsId.wert().toString(),
                    Map.of("lieferplanet", new Value(kaufauftrag.lieferplanet())));
            String wert = openFeatureClient.getStringValue("zielsystem", "imperium", kontext);
            return abbilden(wert, null);
        } catch (RuntimeException e) {
            return abbilden(null, e);
        }
    }

    private Zielsystemtyp abbilden(String wert, Throwable ursache) {
        if ("imperium".equals(wert)) {
            return Zielsystemtyp.IMPERIUM;
        }
        if ("rebellion".equals(wert)) {
            return Zielsystemtyp.REBELLION;
        }
        if (ursache != null) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Flag 'zielsystem' konnte nicht ausgewertet werden, falle auf imperium zurueck",
                    ursache);
        } else {
            LOG.log(java.util.logging.Level.WARNING,
                    "Flag 'zielsystem' lieferte den ungueltigen Wert '{0}', falle auf imperium zurueck",
                    wert);
        }
        return Zielsystemtyp.IMPERIUM;
    }
}
