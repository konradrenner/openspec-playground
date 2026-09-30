/**
 * Service der Raumschiffwerft.
 *
 * <p>Kernprojekt: bietet die REST-Schnittstelle (Auftraege anlegen und
 * anzeigen), konvertiert eingehende Daten ins kanonische interne Modell und
 * routet/transformiert via Apache Camel an die Ziele. Aggregiert die Adapter
 * und das Modell und ist die lauffaehige Quarkus-Anwendung; intern nach
 * Boundary-Control-Entity strukturiert.</p>
 */
package org.kore.raumschiffwerft.service;
