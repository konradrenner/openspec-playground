package org.kore.raumschiffwerft.model.boundary;

/**
 * Signalisiert, dass die Zustellung eines Auftrags oder die Statusabfrage
 * gegenueber einem Zielsystem gescheitert ist (Nicht-2xx, SOAP-Fault, Timeout).
 */
public class ZustellungUngeklaert extends Exception {

    public ZustellungUngeklaert(String message) {
        super(message);
    }

    public ZustellungUngeklaert(String message, Throwable cause) {
        super(message, cause);
    }

    public ZustellungUngeklaert(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
