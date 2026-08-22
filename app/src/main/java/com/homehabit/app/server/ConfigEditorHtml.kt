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
  textarea {
    width: 100%;
    height: 60vh;
    background: #1b1c20;
    color: #f2f2f0;
    border: 1px solid #2a2b2f;
    border-radius: 10px;
    padding: 12px;
    font-family: ui-monospace, Menlo, Consolas, monospace;
    font-size: 13px;
    box-sizing: border-box;
    resize: vertical;
  }
  .actions { margin-top: 12px; display: flex; gap: 8px; align-items: center; }
  button {
    background: #4a90d9;
    color: #0b1a2b;
    border: none;
    border-radius: 8px;
    padding: 10px 16px;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
  }
  button.secondary {
    background: #1b1c20;
    color: #f2f2f0;
    border: 1px solid #2a2b2f;
  }
  #status { font-size: 12px; color: #7a7d85; }
  #status.ok { color: #a8d67a; }
  #status.error { color: #e35b5b; }
</style>
</head>
<body>
  <h1>Configuration du dashboard</h1>
  <p class="sub">Modifier le JSON puis "Enregistrer". Rechargé automatiquement sur l'appareil.</p>
  <textarea id="editor" spellcheck="false"></textarea>
  <div class="actions">
    <button onclick="save()">Enregistrer</button>
    <button class="secondary" onclick="load()">Recharger</button>
    <span id="status"></span>
  </div>

<script>
const TOKEN = "$token";

async function load() {
  const res = await fetch('/config', { headers: { 'Authorization': 'Bearer ' + TOKEN } });
  if (!res.ok) {
    setStatus('Non autorise (token invalide ?)', 'error');
    return;
  }
  const text = await res.text();
  document.getElementById('editor').value = JSON.stringify(JSON.parse(text), null, 2);
  setStatus('Chargé', 'ok');
}

async function save() {
  const value = document.getElementById('editor').value;
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
}

load();
</script>
</body>
</html>
"""
