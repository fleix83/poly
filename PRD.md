# PRD: MarkView – Android File Viewer mit Markierungsfunktion

**Version:** 1.0
**Datum:** 08.08.2026
**Autor:** Felix Weissheimer
**Status:** Draft

---

## 1. Zusammenfassung

MarkView ist eine schlanke Android-App zum schnellen Öffnen, Anzeigen und Markieren gängiger Dateiformate. Kernidee: Datei antippen, sofort im Vollbild lesen, bei Bedarf einen Markierungsmodus einschalten, Textstellen highlighten, Datei mit Markierungen sichern und teilen. Keine Cloud, kein Account, keine Bearbeitung von Inhalten – nur Anzeigen, Markieren, Teilen.

## 2. Problem und Motivation

Bestehende Viewer-Apps sind entweder auf ein Format spezialisiert (PDF-Reader), überladen (Office-Suiten) oder werbefinanziert. Es fehlt eine schnelle, saubere App, die alle im Alltag relevanten Formate abdeckt und eine einfache, konsistente Markierungsfunktion über alle Formate hinweg bietet.

## 3. Zielgruppe

Einzelnutzer (zunächst der Autor selbst), die beruflich viele Dokumente sichten: Bewerbungsunterlagen, Verträge, Merkblätter, Tabellen, Screenshots. Primäres Nutzungsszenario: Dokument aus Dateimanager, E-Mail oder Messenger öffnen, Passagen markieren, markierte Version weitergeben oder ablegen.

## 4. Unterstützte Formate

| Format | Anzeige | Textmarkierung | Speichern der Markierung |
|---|---|---|---|
| .txt | Nativ (Textrendering) | Ja | Sidecar + Export |
| .md | Gerendert (Markwon) | Ja | Sidecar + Export |
| .pdf | Nativ (PdfRenderer/Pdfium) | Ja (textbasiert) | **Echt im PDF** (Highlight-Annotation) + Export |
| .docx | Konvertiert zu HTML, WebView | Ja | Sidecar + Export |
| .xlsx | Konvertiert zu HTML-Tabelle, WebView | Ja (Zellen/Text) | Sidecar + Export |
| .html | WebView | Ja | Sidecar + Export |
| .jpg / .png / .gif | Nativ (Coil), Zoom/Pan | Freihand-/Rechteck-Marker | Geflattetes Bild (PNG/JPG) |

**Speicherstrategie:**
- **PDF:** Markierungen werden als echte PDF-Highlight-Annotationen in eine Kopie der Datei geschrieben (Original bleibt unangetastet, ausser Nutzer wählt "Original überschreiben").
- **Alle textbasierten Formate:** Markierungen liegen in einer Sidecar-Datei (JSON) im App-Speicher, verknüpft über Datei-URI und Content-Hash. Beim erneuten Öffnen erscheinen sie wieder. Zum Teilen wird ein **Export als PDF** mit eingebrannten Markierungen erzeugt.
- **Bilder:** Markierungen (halbtransparente Rechtecke oder Freihand-Highlighter) werden beim Speichern in eine Bildkopie eingebrannt.

## 5. Kernfunktionen

### 5.1 Öffnen
- App registriert sich als Handler für alle unterstützten MIME-Types (Intent-Filter für VIEW und SEND).
- Öffnen via Storage Access Framework (Systemdatei-Picker) oder direkt aus anderen Apps ("Öffnen mit").
- Zieldauer bis zur ersten Darstellung: unter 1 Sekunde für Dateien bis 5 MB, unter 3 Sekunden für docx/xlsx bis 20 MB (Konvertierung mit Fortschrittsanzeige).
- Liste der zuletzt geöffneten Dateien als Startbildschirm (mit Thumbnail, Name, Datum, Markierungs-Badge).

### 5.2 Darstellung
- Vollbild als Standard: Statusbar/Navigationsleiste ausgeblendet (Immersive Mode), einfaches Tippen blendet eine minimale Toolbar ein/aus.
- Pinch-to-Zoom und Scrollen in allen Formaten.
- PDF: vertikales kontinuierliches Scrollen, Seitenanzeige (z. B. "3/12"), Sprung zu Seite.
- Dark Mode: Systemeinstellung wird übernommen; für Text/MD/HTML optional invertierte Darstellung.
- Landscape wird unterstützt.

### 5.3 Markierungsmodus
- **Ein/Aus über einen prominenten Floating Action Button** (Marker-Icon). Zustand klar sichtbar (FAB farbig aktiv, dezenter Hinweisbalken "Markieren aktiv").
- Bei ausgeschaltetem Modus verhalten sich alle Gesten normal (Scrollen, Zoomen, Tippen). Keine versehentlichen Markierungen möglich.
- Bei eingeschaltetem Modus:
  - Text: Finger über Text ziehen erzeugt Highlight (Standardfarbe Gelb; 4 Farben zur Auswahl: Gelb, Grün, Blau, Rot).
  - Bilder: Freihand-Highlighter oder Rechteck-Werkzeug.
  - Antippen einer bestehenden Markierung: Löschen oder Farbe ändern (kleines Kontextmenü).
  - Undo/Redo in der Toolbar.
- Scrollen im Markierungsmodus: mit zwei Fingern weiterhin möglich (Ein-Finger = Markieren, Zwei-Finger = Scrollen/Zoomen).

### 5.4 Sichern und Teilen
- **Speichern:** legt je nach Format die Annotation ab (siehe 4). Auto-Save der Sidecar-Daten bei jedem Verlassen des Viewers.
- **Exportieren:** "Als PDF mit Markierungen exportieren" (alle Formate) bzw. "Als Bild speichern" (Bilder). Ziel wählbar über SAF.
- **Teilen:** Android Share Sheet mit der exportierten Datei (PDF bzw. Bild). Optional: Original ohne Markierungen teilen.

### 5.5 Sonstiges
- Keine Netzwerkzugriffe (Ausnahme: keine). Alles offline. Kein Tracking, keine Werbung.
- Dateien werden nie ohne explizite Nutzeraktion verändert.
- GIFs werden animiert abgespielt; Markierung bezieht sich auf das erste Frame (Export als statisches Bild).

## 6. Nicht-Ziele (v1)

- Kein Editieren von Dokumentinhalten (kein Texteingeben, kein Formeln ändern).
- Keine Cloud-Synchronisation, keine Konten.
- Keine OCR für gescannte PDFs (Markieren dort nur als Rechteck-Overlay möglich, wie bei Bildern).
- Keine Freitext-Notizen/Kommentare (Kandidat für v2).
- Kein Tablet-spezifisches Zwei-Spalten-Layout (v2).
- Keine .doc/.xls-Altformate, kein .pptx (v2-Kandidat).

## 7. UX-Leitlinien

- Material 3, Jetpack Compose, dynamische Farben (Material You), aber zurückhaltend: Inhalt steht im Vordergrund.
- Maximal eine Toolbar, maximal ein FAB. Keine verschachtelten Menüs für Kernfunktionen.
- Alle Kernaktionen (Öffnen, Markieren an/aus, Speichern, Teilen) in höchstens zwei Taps erreichbar.
- Deutsch als Standardsprache, Englisch als Fallback (strings ausgelagert).

## 8. Technische Rahmenbedingungen

- **Sprache/Stack:** Kotlin, Jetpack Compose, minSdk 26 (Android 8.0), targetSdk aktuell.
- **Architektur:** Single-Activity, MVVM, Repository-Schicht für Datei-/Annotationszugriff, Format-Renderer als austauschbare Module hinter gemeinsamem Interface.
- **Bibliotheken (Vorschlag, in Implementierungsplan verifizieren):**
  - PDF-Rendering: `android.graphics.pdf.PdfRenderer` (Basis) oder Pdfium-Wrapper für Performance; Textpositionen für Highlights via Pdfium/MuPDF-Alternative prüfen.
  - PDF-Schreiben: `pdfbox-android` (Apache 2.0) für Highlight-Annotationen und PDF-Export.
  - Markdown: `Markwon`.
  - docx/xlsx: `Apache POI` (poi-ooxml) zur Extraktion, eigenes HTML-Mapping; Grösse/ProGuard beachten.
  - Bilder: `Coil` (inkl. GIF-Decoder), Zoom via `telephoto` oder eigene Zoomable-Composable.
  - Persistenz: `Room` für Recents und Annotation-Sidecars.
- **Lizenz-Constraint:** Nur permissive Lizenzen (Apache/MIT/BSD). Kein AGPL (schliesst MuPDF-Standardlizenz aus, ausser bewusst entschieden).

## 9. Datenmodell (Annotationen)

```
Annotation {
  id: UUID
  fileHash: String          // SHA-256 des Dateiinhalts
  fileUri: String           // letzte bekannte URI
  format: Enum
  type: TEXT_HIGHLIGHT | RECT | FREEHAND
  color: Enum
  // TEXT_HIGHLIGHT:
  anchor: { startOffset, endOffset, quotedText, prefix, suffix }   // robustes Re-Anchoring
  // PDF zusätzlich: pageIndex, quadPoints
  // RECT/FREEHAND: pageIndex/imageRef, normierte Koordinaten [0..1]
  createdAt, updatedAt
}
```

Re-Anchoring über `quotedText` + Kontext (Prefix/Suffix), damit Markierungen auch nach kleinen Dateiänderungen möglichst wiedergefunden werden; bei Hash-Mismatch Hinweis "Datei wurde verändert, Markierungen könnten verschoben sein".

## 10. Erfolgskriterien

- Alle 9 Formate öffnen zuverlässig (Testsuite mit je mindestens 5 realen Beispieldateien).
- Kaltstart bis Inhalt sichtbar: < 1.5 s auf Mittelklassegerät.
- Markierung setzen, App schliessen, Datei erneut öffnen: Markierung ist wieder da.
- PDF mit Highlights lässt sich in Adobe Reader/Drive öffnen und zeigt die Highlights.
- Export-PDF eines .docx mit Markierungen ist les- und teilbar.

## 11. Risiken

| Risiko | Auswirkung | Mitigation |
|---|---|---|
| Textpositionen im PDF (QuadPoints) schwer zu ermitteln | PDF-Highlights ungenau | Pdfium-Text-API evaluieren; Fallback: Rechteck-Highlight auf gerenderter Seite, beim Speichern als Square-Annotation |
| Apache POI gross und ProGuard-empfindlich | APK-Grösse, Crashes | Nur benötigte Module einbinden, ProGuard-Regeln früh testen; Alternative: leichtgewichtige eigene OOXML-Parser für Basisfälle |
| WebView-Textselektion mit eigener Gestensteuerung kollidiert | Markieren in HTML/docx frickelig | JS-Bridge mit eigener Selektionslogik (Range API), Zwei-Finger-Scroll-Konvention |
| Grosse xlsx-Dateien | ANR/OOM | Streaming-Parsing, Zeilenlimit mit "weitere laden", Blattauswahl |
| GIF-Markierung konzeptionell unklar | Verwirrung | Klar kommunizieren: Markierung auf Standbild, Export statisch |
