package com.homehabit.app.server

/**
 * Minimal editing page served on /: a simple JSON text editor.
 * Sufficient to validate the principle (reading, editing, saving);
 * a visual drag & drop editor on the browser side is conceivable later
 * but not necessary for now since tactile editing already
 * exists directly in the app.
 *
 * The token is injected directly into the JS (TOKEN variable) so that
 * fetch() calls to /config carry the Authorization header without
 * the user having to re-enter it at each action. Restricted token
 * alphabet (see ConfigRepository.ensureHttpAuthToken) so no
 * risk of injection by interpolating it as is here.
 */
fun configEditorHtml(token: String): String = """
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>HomeHabit - Config</title>
<style>
  :root { color-scheme: dark; }
  body {
    background: #0b0c0e;
    color: #f2f2f0;
    font-family: -apple-system, Segoe UI, Roboto, sans-serif;
    margin: 0;
    padding: 20px;
  }
  h1 { font-size: 16px; font-weight: 500; margin-bottom: 4px; }
  p.sub { color: #7a7d85; font-size: 12px; margin-top: 0; margin-bottom: 16px; }
  
  .editor-container {
    position: relative;
    width: 100%;
    height: 65vh;
    background: #1b1c20;
    border: 1px solid #2a2b2f;
    border-radius: 10px;
    overflow: hidden;
  }
  
  #editor, #highlighting {
    margin: 0;
    padding: 16px;
    width: 100%;
    height: 100%;
    font-family: 'Fira Code', ui-monospace, Menlo, Consolas, monospace;
    font-size: 13px;
    line-height: 1.5;
    box-sizing: border-box;
    position: absolute;
    top: 0;
    left: 0;
    white-space: pre;
    overflow: auto;
    tab-size: 2;
  }

  #editor {
    z-index: 1;
    color: transparent;
    background: transparent;
    caret-color: #f2f2f0;
    resize: none;
    border: none;
    outline: none;
    -webkit-text-fill-color: transparent;
  }

  #highlighting {
    z-index: 0;
    color: #f2f2f0;
    pointer-events: none;
  }

  /* JSON Highlighting Colors */
  .hl-key { color: #e8b26a; }
  .hl-string { color: #a8d67a; }
  .hl-number { color: #4a90d9; }
  .hl-boolean, .hl-null { color: #e35b5b; }
  .hl-bracket { color: #7a7d85; }

  .actions { margin-top: 16px; display: flex; gap: 8px; align-items: center; }
  button {
    background: #4a90d9;
    color: #0b1a2b;
    border: none;
    border-radius: 8px;
    padding: 10px 18px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: opacity 0.2s;
  }
  button:hover { opacity: 0.9; }
  button.secondary {
    background: #1b1c20;
    color: #f2f2f0;
    border: 1px solid #2a2b2f;
  }
  #status { font-size: 12px; color: #7a7d85; margin-left: auto; }
  #status.ok { color: #a8d67a; }
  #status.error { color: #e35b5b; }
</style>
</head>
<body>
  <h1>Configuration du dashboard</h1>
  <p class="sub">Modifier le JSON puis "Enregistrer". Le dashboard se mettra à jour automatiquement sur l'appareil.</p>
  
  <div class="editor-container">
    <pre id="highlighting" aria-hidden="true"></pre>
    <textarea id="editor" spellcheck="false" oninput="updateView()" onscroll="syncScroll()"></textarea>
  </div>

  <div class="actions">
    <button onclick="save()">Enregistrer</button>
    <button class="secondary" onclick="load()">Recharger</button>
    <button class="secondary" onclick="beautify()">Formater</button>
    <span id="status"></span>
  </div>

<script>
const TOKEN = "$token";
const editor = document.getElementById('editor');
const highlighting = document.getElementById('highlighting');

function updateView() {
  const text = editor.value;
  highlighting.innerHTML = highlight(text) + "\n"; // Adding newline to fix scroll sync at end
  syncScroll();
}

function syncScroll() {
  highlighting.scrollTop = editor.scrollTop;
  highlighting.scrollLeft = editor.scrollLeft;
}

function highlight(text) {
  // Escape HTML
  let html = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  
  // Basic JSON highlighting via regex
  // 1. Strings (including keys)
  // 2. Numbers
  // 3. Booleans/Null
  // 4. Brackets/Braces
  return html.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?|[\[\]{}:,])/g, function (match) {
    let cls = '';
    if (/^"/.test(match)) {
      if (/:$/.test(match)) {
        cls = 'hl-key';
      } else {
        cls = 'hl-string';
      }
    } else if (/true|false/.test(match)) {
      cls = 'hl-boolean';
    } else if (/null/.test(match)) {
      cls = 'hl-null';
    } else if (/[0-9]/.test(match)) {
      cls = 'hl-number';
    } else if (/[\[\]{}:,]/.test(match)) {
      cls = 'hl-bracket';
    }
    return cls ? '<span class="' + cls + '">' + match + '</span>' : match;
  });
}

function beautify() {
  try {
    const obj = JSON.parse(editor.value);
    editor.value = JSON.stringify(obj, null, 2);
    updateView();
    setStatus('Formaté', 'ok');
  } catch (e) {
    setStatus('Erreur de formatage : ' + e.message, 'error');
  }
}

async function load() {
  const res = await fetch('/config', { headers: { 'Authorization': 'Bearer ' + TOKEN } });
  if (!res.ok) {
    setStatus('Non autorisé (token invalide ?)', 'error');
    return;
  }
  const text = await res.text();
  try {
    const obj = JSON.parse(text);
    editor.value = JSON.stringify(obj, null, 2);
    updateView();
    setStatus('Chargé', 'ok');
  } catch (e) {
    editor.value = text;
    updateView();
    setStatus('Chargé (JSON non valide)', 'error');
  }
}

async function save() {
  const value = editor.value;
  try {
    JSON.parse(value);
  } catch (e) {
    setStatus('JSON invalide : ' + e.message, 'error');
    return;
  }
  const res = await fetch('/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + TOKEN },
    body: value
  });
  if (res.ok) {
    setStatus('Enregistré', 'ok');
  } else {
    const err = await res.text();
    setStatus('Erreur serveur : ' + err, 'error');
  }
}

function setStatus(text, cls) {
  const el = document.getElementById('status');
  el.textContent = text;
  el.className = cls;
  if (cls === 'ok') {
    setTimeout(() => { if (el.textContent === text) el.textContent = ''; }, 3000);
  }
}

// Handle tab key
editor.addEventListener('keydown', function(e) {
  if (e.key === 'Tab') {
    e.preventDefault();
    const start = this.selectionStart;
    const end = this.selectionEnd;
    this.value = this.value.substring(0, start) + "  " + this.value.substring(end);
    this.selectionStart = this.selectionEnd = start + 2;
    updateView();
  }
});

load();
</script>
</body>
</html>
"""
