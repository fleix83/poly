# Poly

Schlanke Android-App zum schnellen Öffnen, Anzeigen und Markieren gängiger
Dateiformate – offline, ohne Konto, ohne Tracking. Siehe [PRD.md](PRD.md) und
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## Formate

| Format | Anzeige | Markieren | Speichern |
|---|---|---|---|
| txt / md | nativ (Compose / Markwon) | Text-Highlights | Sidecar (Room) + PDF-Export |
| pdf | Framework-PdfRenderer | Bereich (Rechteck) | echte Square-Annotationen (pdfbox) |
| docx / xlsx | eigener OOXML-Parser → HTML → WebView | Text-Highlights (CSS Custom Highlight API) | Sidecar + PDF-Export |
| html | WebView (Netz blockiert) | Text-Highlights | Sidecar + PDF-Export |
| jpg / png / gif | Coil + telephoto (Zoom/Subsampling) | Rechteck + Freihand | geflattetes PNG/JPG |

Markierungen: FAB an/aus, 4 Farben, ein Finger markiert / zwei Finger
scrollen & zoomen, Undo/Redo, Auto-Save, Re-Anchoring über quotedText +
Kontext bei geänderten Dateien.

## Build

```
JAVA_HOME=<JDK 17+> ./gradlew assembleDebug      # Debug-APK
./gradlew testDebugUnitTest                      # Unit-Tests
./gradlew assembleRelease                        # R8-Release (unsigniert)
```

Stack: Kotlin, Jetpack Compose (Material 3), AGP 9.3 (built-in Kotlin),
Gradle 9.5, minSdk 26, compileSdk 37. Keine Netzwerk-Permission.

## Architektur

```
ch.weissheimer.poly
├── ui/            Screens (Recents, Viewer), Theme, Navigation, Chrome
├── ui/viewer/renderers/   ein Renderer pro Formatgruppe (DocumentRenderer)
├── viewer/pdf|office/     PdfPageStore, DocxToHtml, XlsxToHtml
├── annotation/    Datenmodell, AnnotationSession (Undo/Redo), ReAnchor
├── export/        pdfbox-Annotationen, Text/HTML→PDF, Bild-Flattening
├── data/          Room (Recents, Annotationen), SAF, Hashing, Caches
└── core/          Formaterkennung (MIME → Endung → Magic Bytes)
```

Entscheidungsdokumente: [docs/decisions/](docs/decisions/)
(PDF-Engine, OOXML-Parser statt Apache POI).

## Status

Phasen 1–8 des Implementierungsplans umgesetzt; Build und Unit-Tests grün.
Noch offen (benötigt Gerät/Emulator): manuelle Smoke-Tests je Format,
Instrumented Tests, Baseline Profile, Startup-Messung.
