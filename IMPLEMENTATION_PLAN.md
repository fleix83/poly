# Implementierungsplan: MarkView (Android)

Für die Umsetzung mit Claude Code. Bezieht sich auf PRD.md v1.0.

## Arbeitsweise

- Jede Phase endet mit einem lauffähigen, testbaren Zustand (Build grün, App startbar).
- Nach jeder Phase: Commit mit aussagekräftiger Message, kurzer manueller Smoke-Test auf Emulator oder Gerät.
- Bibliotheksentscheide aus dem PRD gelten als Vorschlag. Vor Einbau jeder Bibliothek: aktuelle Version, Wartungszustand und Lizenz prüfen (nur Apache/MIT/BSD).
- Sprache im Code Englisch, UI-Strings Deutsch (mit values/strings.xml, englischer Fallback in values-en).

## Projektsetup

```
Projektname: markview
Package: ch.weissheimer.markview
minSdk 26, targetSdk aktuell, Kotlin, Jetpack Compose, Material 3
Single-Activity (MainActivity), Navigation Compose
Module: :app (v1 monolithisch, aber Paketstruktur modulfähig)
```

Paketstruktur:

```
ch.weissheimer.markview
├── ui/            // Screens, Theme, gemeinsame Composables
├── viewer/        // Renderer pro Format (gemeinsames Interface)
│   ├── text/  markdown/  pdf/  office/  web/  image/
├── annotation/    // Datenmodell, Overlay, Gesten, Re-Anchoring
├── export/        // PDF-Export, Bild-Flattening, Share
├── data/          // Room (Recents, Annotationen), SAF-Zugriff, Hashing
└── core/          // Utils, Dispatcher, Result-Typen
```

---

## Phase 1: Gerüst und Datei-Öffnen

**Ziel:** App startet, kann Dateien via SAF und Intent-Filter entgegennehmen, zeigt Recents.

1. Neues Projekt mit Compose, Material 3, Navigation, Room, Hilt (oder manuelle DI, wenn schlanker).
2. `MainActivity` mit Intent-Handling: `ACTION_VIEW` und `ACTION_SEND` für alle MIME-Types aus dem PRD (Manifest-Intent-Filter inkl. Datei-Endungs-Fallback über `content://`-URIs).
3. SAF-Integration: `OpenDocument`-Launcher, `takePersistableUriPermission` für Recents.
4. `FileRepository`: URI → Metadaten (Name, Grösse, MIME), SHA-256-Hashing (gestreamt), Format-Erkennung (MIME zuerst, Endung als Fallback, Magic Bytes für Bilder/PDF).
5. Recents-Screen: Room-Entity `RecentFile`, LazyColumn mit Name, Datum, Format-Icon. Noch keine Thumbnails.
6. Routing: Recents → ViewerScreen(uri, format) mit Platzhalter "Format X noch nicht unterstützt".

**Abnahme:** txt-Datei aus dem Dateimanager mit "Öffnen mit → MarkView" landet im Platzhalter-Viewer; Recents füllt sich.

## Phase 2: Viewer-Framework und einfache Formate (txt, md, Bilder)

**Ziel:** Vollbild-Viewer mit einheitlichem Rahmen; drei Formatgruppen darstellbar.

1. `ViewerScreen`-Rahmen: Immersive Mode, Tap toggelt minimale Toolbar (Titel, Teilen, Overflow), FAB-Slot für Markierungsmodus (noch inaktiv).
2. Gemeinsames Interface:
   ```kotlin
   interface DocumentRenderer {
       @Composable fun Content(state: ViewerState, modifier: Modifier)
       val capabilities: RendererCapabilities  // textSelectable, pageable, zoomable...
   }
   ```
3. **TextRenderer (.txt):** gestreamtes Einlesen (Charset-Erkennung UTF-8/Latin-1), monospace-Option, Zoom über Schriftgrösse (Pinch), LazyColumn für grosse Dateien.
4. **MarkdownRenderer (.md):** Markwon in `AndroidView` oder Compose-Markdown-Lib (prüfen: Wartung, Tabellen-Support). Rendering inkl. Tabellen, Code-Blöcke, Links (Links öffnen extern via Chooser).
5. **ImageRenderer (.jpg/.png/.gif):** Coil mit GIF-Decoder, Zoom/Pan (telephoto `ZoomableAsyncImage` evaluieren, sonst eigene Transformable-Composable). Grosse Bilder: Subsampling beachten.
6. Fehlerzustände: defekte Datei, fehlende Berechtigung, zu gross → saubere Fehler-Composable mit Retry.

**Abnahme:** txt, md, jpg, png, gif öffnen im Vollbild, Zoom und Scroll funktionieren, Toolbar toggelt.

## Phase 3: PDF-Anzeige

**Ziel:** PDFs flüssig anzeigen.

1. Evaluation (kurz, mit Testcode): `PdfRenderer` (Framework) vs. Pdfium-Wrapper. Kriterien: Scroll-Performance bei 100+ Seiten, Zoom-Qualität, Zugriff auf Textpositionen. Entscheidung dokumentieren in `docs/decisions/pdf-engine.md`.
2. Kontinuierliches vertikales Scrollen: LazyColumn mit seitenweisem Rendering in Bitmaps, Auflösung abhängig vom Zoomlevel (Re-Render bei Zoom-Ende), Bitmap-Cache mit LRU und Recycling.
3. Seitenindikator ("3/12"), Tap auf Indikator → Seiten-Slider.
4. Passwortgeschützte PDFs: Passwort-Dialog, Fehlerbehandlung.

**Abnahme:** 100-seitiges PDF scrollt flüssig, Zoom scharf, Speicherverbrauch stabil.

## Phase 4: docx und xlsx via HTML + WebView

**Ziel:** Office-Formate lesbar darstellen.

1. Apache POI einbinden (nur ooxml-Module), ProGuard/R8-Regeln sofort testen (Release-Build in dieser Phase pflicht).
2. **DocxToHtml:** Absätze, Überschriften, Fett/Kursiv/Unterstrichen, Listen, einfache Tabellen, eingebettete Bilder (als Base64 oder temp files). Kein Anspruch auf Layouttreue; sauberes, lesbares CSS (max-width, Zeilenabstand, Dark-Mode-Variante).
3. **XlsxToHtml:** Blattauswahl (Chips oben), Tabelle mit fixierter Kopfzeile, Zahlenformate grob (Datum, Prozent, Dezimal), Zeilenlimit 500 mit "Weitere laden". Streaming-API von POI verwenden, um OOM zu vermeiden.
4. Konvertierung in Coroutine mit Fortschrittsanzeige; Ergebnis-HTML cachen (Key: fileHash), damit erneutes Öffnen sofort geht.
5. WebView-Setup: JavaScript aktiviert (wird für Markierung gebraucht), kein Netzwerkzugriff (`blockNetworkLoads = true`), Zoom via WebView-Settings.
6. **.html-Dateien:** gleiche WebView-Infrastruktur, lokales Laden via `loadDataWithBaseURL`, externe Ressourcen blockiert mit Hinweisbanner "Externe Inhalte blockiert".

**Abnahme:** Reale Bewerbungs-docx und eine Tabelle mit mehreren Blättern öffnen lesbar; Release-Build läuft ohne POI-Crashes.

## Phase 5: Markierungsmodus – Kern

**Ziel:** Einheitlicher Markierungsmodus mit sauberer Gestentrennung.

1. `AnnotationState` im ViewModel: Modus an/aus, aktive Farbe, Undo/Redo-Stack, Liste der Annotationen (Room-gestützt, Auto-Save).
2. FAB toggelt Modus; aktiver Modus: FAB gefüllt in Markerfarbe plus schmale Statusleiste "Markieren aktiv" mit Farbwahl (4 Farben) und Undo/Redo.
3. **Gestenkonvention überall gleich:** Modus aus → alle Gesten normal. Modus an → ein Finger markiert, zwei Finger scrollen/zoomen. Umsetzung in Compose über eigenen `pointerInput` mit PointerCount-Weiche; im WebView über Touch-Interception plus JS.
4. **Text-Highlights (txt/md):** Compose-Text mit eigener Selektionslogik: Zeichenoffsets via `TextLayoutResult.getOffsetForPosition`, Highlight als Hintergrund-Spans/`drawBehind`. Datenmodell wie PRD 9 (Offsets + quotedText + Kontext).
5. **Bild-Markierungen:** Canvas-Overlay über dem Bild, Rechteck- und Freihand-Werkzeug, halbtransparent, Koordinaten normiert auf Bildgrösse gespeichert.
6. Tap auf Markierung (Modus an): Popup mit Farbe ändern / Löschen.
7. Re-Anchoring beim Laden: Hash gleich → Offsets direkt; Hash anders → Suche über quotedText+Kontext, Treffer übernehmen, Rest als "verwaist" markieren mit Hinweis.

**Abnahme:** In txt und jpg markieren, App killen, Datei erneut öffnen → Markierungen wieder da; Scrollen mit zwei Fingern im Modus funktioniert.

## Phase 6: Markieren in PDF und WebView

**Ziel:** Markierung für die restlichen Formate.

1. **PDF:** Textpositionen der gerenderten Seite ermitteln (abhängig vom Engine-Entscheid aus Phase 3):
   - Pdfium: Text-API für Zeichen-Bounding-Boxes → echte Text-Highlights mit QuadPoints.
   - Nur PdfRenderer: Fallback Rechteck-Highlight auf Seitenkoordinaten (normiert), im UI als "Bereich markieren" benannt.
2. **WebView (html/docx/xlsx):** JS-Bridge:
   - Injektion eines kleinen Skripts: bei aktivem Modus Touch-Events abfangen, `document.caretRangeFromPoint` für Range-Aufbau, Highlight via `<mark>`-Wrapping oder CSS Custom Highlight API (Verfügbarkeit in WebView prüfen).
   - Serialisierung der Range (XPath/Offsets plus quotedText) an Kotlin via `JavascriptInterface`; beim Laden Re-Injektion der Highlights.
3. Undo/Redo und Farbwechsel auch hier anbinden.

**Abnahme:** Highlight in PDF, docx-HTML und xlsx-Tabelle setzen, schliessen, wieder öffnen → vorhanden.

## Phase 7: Speichern, Export, Teilen

**Ziel:** Ergebnisse aus der App herausbekommen.

1. **PDF-Speichern:** pdfbox-android; Highlights als `PDAnnotationTextMarkup` (HIGHLIGHT) mit QuadPoints bzw. Square-Annotation beim Rechteck-Fallback. Standard: "Kopie speichern" (SAF `CreateDocument`), Option "Original überschreiben" mit Bestätigungsdialog.
2. **Export als PDF (txt/md/html/docx/xlsx):** Rendering nach PDF:
   - WebView-Formate: `PdfDocument` + WebView-Print-Pfad (`createPrintDocumentAdapter`) evaluieren; Highlights sind im DOM bereits als `<mark>` sichtbar und landen mit im Export.
   - txt/md: eigenes Paging auf `PdfDocument`-Canvas, Highlights als Rechtecke hinter dem Text.
3. **Bilder:** Flattening: Original-Bitmap + Overlay auf Canvas zeichnen, als PNG (verlustfrei) oder JPG speichern; GIF → PNG-Standbild mit Hinweis.
4. **Teilen:** `FileProvider`, Share Sheet; Auswahl "Mit Markierungen (PDF/Bild)" vs. "Original".
5. Auto-Save der Sidecar-Annotationen bei `onStop` des Viewers.

**Abnahme:** docx markieren → als PDF exportieren → in Drive/Adobe öffnen: Markierungen sichtbar. PDF-Highlights in Adobe Reader korrekt.

## Phase 8: Politur und Härtung

1. Recents mit Thumbnails (erste Seite/Bild, Coil-Cache), Markierungs-Badge, Swipe-to-Remove.
2. Dark Mode für alle Renderer prüfen (inkl. HTML-CSS-Variante), Landscape-Test.
3. Performance: Baseline Profile, Startup-Messung, Bitmap-Speicher unter 200 MB bei grossen PDFs.
4. Fehlerrobustheit: korrupte Testdateien je Format, 0-Byte-Dateien, falsche Endungen (Magic-Byte-Erkennung greift).
5. Accessibility: contentDescription, TalkBack-Grundfunktion, Mindest-Touchziele.
6. Tests:
   - Unit: Format-Erkennung, Re-Anchoring, Docx/Xlsx-HTML-Mapping, Annotation-Serialisierung.
   - Instrumented: Öffnen je Format (Testassets), Markieren-Roundtrip txt und PDF.
7. App-Icon, About-Screen mit Lizenzhinweisen (OSS-Lizenzen-Plugin).

**Abnahme:** Erfolgskriterien aus PRD Abschnitt 10 vollständig erfüllt.

---

## Offene Entscheidungen (während Umsetzung klären)

| # | Frage | Klären in |
|---|---|---|
| 1 | PdfRenderer vs. Pdfium (Textpositionen!) | Phase 3 |
| 2 | Markwon vs. Compose-Markdown-Bibliothek | Phase 2 |
| 3 | CSS Custom Highlight API im WebView verfügbar? Sonst `<mark>`-Wrapping | Phase 6 |
| 4 | POI-Alternative, falls APK-Grösse > 40 MB | Phase 4 |
| 5 | Hilt vs. manuelle DI | Phase 1 |

## Hinweise für Claude Code

- Vor jeder Bibliotheksintegration: Maven Central auf aktuelle Version prüfen, keine Versionen aus dem Gedächtnis verwenden.
- Release-Build (R8) spätestens ab Phase 4 in jeder Phase mitbauen.
- Testdateien früh anlegen: `app/src/androidTest/assets/` mit je 2 bis 3 Beispielen pro Format.
- Bei WebView-Markierung (Phase 6) zuerst einen isolierten Spike in einer Test-Activity bauen, bevor die Integration in den Viewer erfolgt.
- Keine Netzwerk-Permission ins Manifest aufnehmen.
