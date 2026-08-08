# Entscheidung: docx/xlsx-Konvertierung ohne Apache POI

**Datum:** 2026-08-08 · **Status:** Entschieden (Phase 4)

## Frage

Apache POI (poi-ooxml) für docx/xlsx-Extraktion oder eigener leichtgewichtiger
OOXML-Parser?

## Befund zu POI auf Android

- POI 5.x hängt an XMLBeans, das `javax.xml.stream` (StAX) benötigt. Android
  bringt kein StAX mit; man müsste StAX-API plus Implementierung mitliefern
  und pflegen.
- poi-ooxml + xmlbeans + commons-* bringen zweistellige MB in die APK und
  sind notorisch R8-empfindlich (Reflection, Service-Loader).
- Benötigt wird nur ein kleiner Ausschnitt: lesbarer Text mit
  Grundformatierung, keine Layouttreue (PRD 4: „Kein Anspruch auf
  Layouttreue").

## Entscheidung

**Eigener OOXML-Parser** (im PRD 11 als Mitigation ausdrücklich vorgesehen):
`java.util.zip.ZipFile` + Android-`XmlPullParser` über die relevanten
Package-Teile:

- **docx:** `word/document.xml` (Absätze, Überschriften, Fett/Kursiv/
  Unterstrichen/Durchgestrichen, Listen, Tabellen, Zeilenumbrüche),
  `word/_rels/document.xml.rels` + `word/media/*` für eingebettete Bilder
  (als data-URIs), Hyperlinks.
- **xlsx:** `xl/workbook.xml` (Blattliste), `xl/sharedStrings.xml`,
  `xl/styles.xml` (grobe Zahlenformate: Datum, Prozent, Dezimal),
  `xl/worksheets/sheetN.xml` streamend mit Zeilenlimit.

Vorteile: keine neuen Abhängigkeiten, volle R8-Kontrolle, streamendes Parsen
ohne OOM-Risiko. Nachteil: weniger Formatabdeckung als POI – für den
Anzeigezweck der App ausreichend; Erweiterung bei Bedarf gezielt möglich.
