# Zustellungssteuerung Specification (Delta)

## ADDED Requirements

### Requirement: Zustell-Route prueft den kanonischen Auftrag per Bean Validation
Die Zustell-Route `direct:zustellen` MUSS den kanonischen Kaufauftrag per Bean Validation prüfen, BEVOR das Zielsystem angewählt wird. Ein Verstoß DARF NICHT an das Zielsystem gesendet werden und MUSS wie jeder technische Misserfolg der Zustellung behandelt werden: gemeldet als `ZustellungUngeklaert` und verbucht als UNGEKLAERT mit `naechster_versuch_um`, sodass Backoff und Abgleich wie bei Adapterfehlern greifen.

#### Scenario: Verletzender Auftrag wird nicht zugestellt
- **WHEN** die Zustell-Route einen kanonischen Kaufauftrag erhält, der die Bean-Validation-Regeln verletzt
- **THEN** ruft die Route das Zielsystem nicht auf, und die Zustellung wird als UNGEKLAERT mit naechster_versuch_um verbucht

#### Scenario: Gueltiger Auftrag wird normal zugestellt
- **WHEN** die Zustell-Route einen regelkonformen kanonischen Kaufauftrag erhält
- **THEN** wird er unverändert an das gewählte Zielsystem zugestellt
