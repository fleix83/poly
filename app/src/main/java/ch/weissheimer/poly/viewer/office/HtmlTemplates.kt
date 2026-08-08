package ch.weissheimer.poly.viewer.office

/** Shared page wrapper: readable typography, dark-mode palette, sticky table head. */
object HtmlTemplates {

    fun page(body: String, wide: Boolean = false): String {
        val maxWidth = if (wide) "none" else "52rem"
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<style>
:root {
  color-scheme: light dark;
  --bg: #FFFFFF; --fg: #1B1B1F; --muted: #6B6B74;
  --border: #DDDDE3; --accent: #3B5BA9; --thead: #F2F2F7;
}
@media (prefers-color-scheme: dark) {
  :root { --bg: #131317; --fg: #E4E2E8; --muted: #97969F;
          --border: #3A3A42; --accent: #93A8FF; --thead: #1E1E24; }
}
html, body { margin: 0; padding: 0; background: var(--bg); color: var(--fg); }
body { font-family: sans-serif; line-height: 1.55; padding: 1.25rem;
       max-width: $maxWidth; margin: 0 auto; word-wrap: break-word; }
h1,h2,h3,h4,h5,h6 { line-height: 1.25; }
a { color: var(--accent); }
img { max-width: 100%; height: auto; }
table { border-collapse: collapse; margin: 0.75rem 0; }
th, td { border: 1px solid var(--border); padding: 0.35rem 0.6rem;
         text-align: left; vertical-align: top; white-space: nowrap; }
table:not(.sheet) th, table:not(.sheet) td { white-space: normal; }
thead th { position: sticky; top: 0; background: var(--thead); z-index: 1; }
mark { background: rgba(255, 213, 79, 0.55); color: inherit; }
.more { padding: 1rem 0; color: var(--muted); }
.more button { font: inherit; color: var(--accent); background: none;
               border: 1px solid var(--accent); border-radius: 1rem;
               padding: 0.3rem 1rem; margin-left: 0.5rem; }
</style>
</head>
<body>
$body
</body>
</html>
"""
    }
}
