package ch.weissheimer.poly.ui.viewer.renderers

/**
 * Injected highlighting runtime for WebView content (html/docx/xlsx).
 *
 * Uses the CSS Custom Highlight API when available (Chromium WebView ≥ 105);
 * without it, annotations are still recorded but not painted. Offsets are
 * global character positions over the concatenated text nodes of <body>
 * (script/style excluded) – the same text the Kotlin side receives via
 * Poly.onDocumentText for anchoring and re-anchoring.
 *
 * Gesture contract in annotation mode: one finger selects (native scroll
 * suppressed via preventDefault), a second finger aborts and returns the
 * gesture to scroll/zoom.
 */
const val HIGHLIGHT_SCRIPT = """
(function() {
  if (window.__polyInit) return;
  window.__polyInit = true;

  var COLORS = {
    YELLOW: 'rgba(255,235,59,0.5)',
    GREEN:  'rgba(129,199,132,0.5)',
    BLUE:   'rgba(100,181,246,0.5)',
    RED:    'rgba(229,115,115,0.5)'
  };
  var mode = false;
  var items = [];
  var useHighlightAPI = (typeof CSS !== 'undefined') && !!CSS.highlights &&
                        (typeof Highlight !== 'undefined');

  if (useHighlightAPI) {
    var style = document.createElement('style');
    var css = '';
    for (var key in COLORS) {
      css += '::highlight(poly-' + key.toLowerCase() + ') { background-color: ' +
             COLORS[key] + '; }';
    }
    style.textContent = css;
    document.head.appendChild(style);
  }

  function textWalker() {
    return document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function(n) {
        var p = n.parentNode && n.parentNode.nodeName;
        return (p === 'SCRIPT' || p === 'STYLE' || p === 'NOSCRIPT')
          ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
  }

  function docText() {
    var s = '';
    var w = textWalker();
    while (w.nextNode()) s += w.currentNode.nodeValue;
    return s;
  }

  function globalOffsetOf(node, offset) {
    if (!node || node.nodeType !== 3) return -1;
    var total = 0;
    var w = textWalker();
    while (w.nextNode()) {
      if (w.currentNode === node) return total + offset;
      total += w.currentNode.nodeValue.length;
    }
    return -1;
  }

  function rangeFromOffsets(start, end) {
    var range = document.createRange();
    var total = 0;
    var haveStart = false;
    var w = textWalker();
    while (w.nextNode()) {
      var n = w.currentNode;
      var len = n.nodeValue.length;
      if (!haveStart && start <= total + len) {
        range.setStart(n, Math.max(0, start - total));
        haveStart = true;
      }
      if (haveStart && end <= total + len) {
        range.setEnd(n, Math.max(0, end - total));
        return range;
      }
      total += len;
    }
    return null;
  }

  function applyAll() {
    if (!useHighlightAPI) return;
    ['yellow', 'green', 'blue', 'red'].forEach(function(c) {
      CSS.highlights['delete']('poly-' + c);
    });
    var byColor = {};
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      var r = rangeFromOffsets(it.start, it.end);
      if (!r) continue;
      if (!byColor[it.color]) byColor[it.color] = [];
      byColor[it.color].push(r);
    }
    for (var color in byColor) {
      var h = new Highlight();
      byColor[color].forEach(function(r) { h.add(r); });
      CSS.highlights.set('poly-' + color.toLowerCase(), h);
    }
  }

  function clearSelection() {
    var s = window.getSelection();
    if (s) s.removeAllRanges();
  }

  function caretAt(x, y) {
    if (document.caretRangeFromPoint) return document.caretRangeFromPoint(x, y);
    if (document.caretPositionFromPoint) {
      var p = document.caretPositionFromPoint(x, y);
      if (!p) return null;
      var r = document.createRange();
      r.setStart(p.offsetNode, p.offset);
      r.setEnd(p.offsetNode, p.offset);
      return r;
    }
    return null;
  }

  window.PolyHighlight = {
    setMode: function(active) {
      mode = !!active;
      if (!mode) clearSelection();
    },
    setItems: function(json) {
      try { items = JSON.parse(json); } catch (e) { items = []; }
      applyAll();
    }
  };

  var touching = false;
  var moved = false;
  var startCaret = null;
  var startXY = null;

  document.addEventListener('touchstart', function(e) {
    if (!mode || e.touches.length !== 1) { touching = false; return; }
    var t = e.touches[0];
    startXY = [t.clientX, t.clientY];
    startCaret = caretAt(t.clientX, t.clientY);
    touching = true;
    moved = false;
    e.preventDefault();
  }, { passive: false });

  document.addEventListener('touchmove', function(e) {
    if (!mode || !touching) return;
    if (e.touches.length !== 1) {
      touching = false;
      clearSelection();
      return;
    }
    var t = e.touches[0];
    var dx = t.clientX - startXY[0];
    var dy = t.clientY - startXY[1];
    if (!moved && (dx * dx + dy * dy) < 64) { e.preventDefault(); return; }
    moved = true;
    var current = caretAt(t.clientX, t.clientY);
    if (startCaret && current) {
      var sel = window.getSelection();
      sel.removeAllRanges();
      try {
        sel.setBaseAndExtent(
          startCaret.startContainer, startCaret.startOffset,
          current.startContainer, current.startOffset);
      } catch (err) {}
    }
    e.preventDefault();
  }, { passive: false });

  document.addEventListener('touchend', function(e) {
    if (!mode || !touching) return;
    touching = false;
    if (!moved) {
      if (startCaret) {
        var off = globalOffsetOf(startCaret.startContainer, startCaret.startOffset);
        for (var i = items.length - 1; i >= 0; i--) {
          if (off >= items[i].start && off < items[i].end) {
            Poly.onHighlightTapped(items[i].id);
            break;
          }
        }
      }
      clearSelection();
      e.preventDefault();
      return;
    }
    var sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && !sel.isCollapsed) {
      var r = sel.getRangeAt(0);
      var s = globalOffsetOf(r.startContainer, r.startOffset);
      var en = globalOffsetOf(r.endContainer, r.endOffset);
      if (s >= 0 && en > s) Poly.onHighlightCreated(s, en);
    }
    clearSelection();
    e.preventDefault();
  }, { passive: false });

  document.addEventListener('click', function(e) {
    if (mode) return;
    if (!e.target.closest('a') && !e.target.closest('button')) { Poly.onTap(); }
  });

  Poly.onDocumentText(docText());
})();
"""
