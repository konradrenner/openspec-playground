package org.kore.raumschiffwerft.service.control;

import java.time.OffsetDateTime;
import java.util.List;

import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;

/**
 * Port des control auf die zustellung-Tabelle. Die Implementierung
 * (JDBC) liegt in boundary/persistence; das control kennt kein SQL.
 */
public interface ZustellungRepository {

    /** Legt die Zustellungszeile an (in der Annahme-Transaktion). */
    void anlegen(Zustellung zustellung);

    /**
     * Verbucht den Ausgang eines Zustell- oder Abgleichsschritts mit
     * GENAU EINEM konditionalen Update per Primaerschluessel. Rueckgabe
     * 0 heisst: die Zeile hatte nicht mehr den erwarteten Ausgangsstatus.
     */
    int verbuchen(Zustellung zustellung, Zustellungsstatus erwarteterAusgangsstatus);

    /**
     * Beansprucht bis zu batch faellige Zustellungen atomar (FOR UPDATE
     * SKIP LOCKED) und liefert sie samt kanonischem Auftrag zurueck;
     * versuche erhoehen sich dabei um eins, der Status geht auf IN_ABGLEICH.
     */
    List<Beanspruchung> faelligeBeanspruchen(int batch, OffsetDateTime leaseBis, String instanz);
}
