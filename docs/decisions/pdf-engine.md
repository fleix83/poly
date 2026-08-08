# Entscheidung: PDF-Engine

**Datum:** 2026-08-08 · **Status:** Entschieden (Phase 3)

## Frage

Framework-`android.graphics.pdf.PdfRenderer` oder Pdfium-Wrapper für Anzeige
(und später Textpositionen für Highlights)?

## Kandidaten

| Engine | Lizenz | Wartung | Textpositionen | APK-Kosten |
|---|---|---|---|---|
| `android.graphics.pdf.PdfRenderer` | Teil des OS | von Google gepflegt | erst ab API 35 (Gerätefunktion) | 0 MB |
| Pdfium-Wrapper (z. B. `io.legere:pdfiumandroid`) | Apache 2.0 | Community, kleines Projekt | ja (Char-Bounding-Boxes) | ~8–16 MB Native-Libs |
| MuPDF | AGPL | gut | ja | Lizenz-Ausschluss (PRD 8) |

## Entscheidung

**Framework-`PdfRenderer`.** Begründung:

- Keine zusätzlichen Native-Bibliotheken: kein APK-Wachstum, kein
  ABI-/NDK-Risiko, keine Abhängigkeit von einem klein besetzten
  Community-Wrapper für die Kernfunktion der App.
- Rendering-Qualität und Performance des Systemrenderers (intern ebenfalls
  Pdfium) sind für kontinuierliches Scrollen mit Bitmap-Cache ausreichend.
- Der bekannte Nachteil – keine Textpositions-API unterhalb API 35 – ist im
  PRD ausdrücklich abgedeckt: Highlights in PDFs laufen über den
  Rechteck-Fallback auf normierten Seitenkoordinaten („Bereich markieren“),
  beim Speichern als Square-Annotation (PRD 11, Implementierungsplan 6.1).
- Passwortgeschützte PDFs kann der Framework-Renderer unterhalb API 35 nicht
  öffnen; das löst `pdfbox-android` (Apache 2.0, ohnehin ab Phase 7 für das
  Schreiben von Annotationen nötig): Mit Passwort entschlüsselte temporäre
  Kopie → Framework-Renderer.

## Revisionspfad

Wenn echte Text-Highlights in PDFs auf breiter Gerätebasis Pflicht werden:
`io.legere:pdfiumandroid` erneut evaluieren (Text-API, Wartungszustand) oder
androidx.pdf, sobald stabil.
