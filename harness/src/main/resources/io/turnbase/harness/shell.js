// The harness shell: the platform shell's half of the game session protocol (websocket.md,
// "Platform shell relay contract"), against the harness's mock Orchestration. It relays STOMP <->
// postMessage and renders the game log; it holds no game state.
(() => {
  'use strict';
  const cfg = JSON.parse(document.getElementById('cfg').textContent);
  const frame = document.getElementById('game');
  const statusEl = document.getElementById('status');
  const logEl = document.getElementById('log');
  document.getElementById('viewer').textContent = 'viewing as ' + cfg.player;

  // ---- game log: the platform's locked-down Markdown (input-validation.md) --------------------
  // Emphasis, strong, strikethrough and code spans only. No raw HTML, no links, no images; the
  // text is only ever placed in the DOM through text nodes. Colour annotations are not rendered:
  // their syntax is still open (game-contracts.md OQ-001).
  const INLINE = [
    { re: /^\*\*(.+?)\*\*/s, tag: 'strong' },
    { re: /^__(.+?)__/s, tag: 'strong' },
    { re: /^~~(.+?)~~/s, tag: 's' },
    { re: /^\*(.+?)\*/s, tag: 'em' },
    { re: /^_(.+?)_/s, tag: 'em' },
    { re: /^`([^`]+)`/s, tag: 'code', literal: true },
  ];
  function renderMarkdown(text, into) {
    let plain = '';
    const flush = () => { if (plain) { into.appendChild(document.createTextNode(plain)); plain = ''; } };
    let rest = text;
    while (rest.length > 0) {
      const hit = INLINE.map((r) => ({ r, m: r.re.exec(rest) })).find((x) => x.m);
      if (!hit) { plain += rest[0]; rest = rest.slice(1); continue; }
      flush();
      const el = document.createElement(hit.r.tag);
      if (hit.r.literal) el.textContent = hit.m[1]; else renderMarkdown(hit.m[1], el);
      into.appendChild(el);
      rest = rest.slice(hit.m[0].length);
    }
    flush();
  }
  const seen = new Set();
  function addLog(sequence, index, entry) {
    const key = sequence + ':' + index;
    if (seen.has(key)) return;
    seen.add(key);
    const li = document.createElement('li');
    renderMarkdown(entry, li);
    logEl.appendChild(li);
  }

  // ---- STOMP over WebSocket --------------------------------------------------------------------
  const frameOf = (command, headers, body) =>
    command + '\n' + Object.entries(headers).map(([k, v]) => k + ':' + v).join('\n') + '\n\n' + (body || '') + '\0';
  function parse(text) {
    const end = text.indexOf('\n\n');
    if (end < 0) return null;
    const lines = text.slice(0, end).split('\n');
    const headers = {};
    lines.slice(1).forEach((l) => { const i = l.indexOf(':'); if (i > 0 && !(l.slice(0, i) in headers)) headers[l.slice(0, i)] = l.slice(i + 1); });
    return { command: lines[0], headers, body: text.slice(end + 2).replace(/\0.*$/s, '') };
  }

  let ws = null;
  let connected = false;
  let syncWanted = false;
  let synced = false;
  let buffer = [];

  function connect() {
    ws = new WebSocket('ws://localhost:' + cfg.wsPort + '/ws?player=' + encodeURIComponent(cfg.player));
    ws.onopen = () => ws.send(frameOf('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0' }));
    ws.onmessage = (ev) => {
      const f = parse(ev.data);
      if (!f) return;
      if (f.command === 'CONNECTED') onConnected();
      else if (f.command === 'MESSAGE') onMessage(f.headers.destination, JSON.parse(f.body));
      else if (f.command === 'ERROR') setStatus('protocol error: ' + f.headers.message, true);
    };
    ws.onclose = () => { connected = false; setStatus('disconnected', true); };
  }
  function subscribe(id, destination) {
    ws.send(frameOf('SUBSCRIBE', { id, destination }));
  }
  function onConnected() {
    // Subscribe first, sync second: the ordering that makes the catch-up race-free (websocket.md).
    subscribe('g', '/topic/game.' + cfg.sessionId);
    subscribe('u', '/user/queue/game');
    subscribe('e', '/user/queue/errors');
    subscribe('h', '/user/queue/harness');
    if (cfg.teamId) subscribe('t', '/topic/game.' + cfg.sessionId + '.team.' + cfg.teamId);
    connected = true;
    setStatus('connected', false);
    if (syncWanted) requestSync();
  }
  function requestSync() {
    synced = false;
    buffer = [];
    ws.send(frameOf('SEND', { destination: '/app/game.' + cfg.sessionId + '.sync', 'content-type': 'application/json' },
      JSON.stringify({ type: 'REQUEST_GAME_SYNC', correlationId: crypto.randomUUID() })));
  }
  function setStatus(text, bad) {
    statusEl.textContent = text;
    statusEl.classList.toggle('failed', !!bad);
  }

  // ---- shell -> iframe -------------------------------------------------------------------------
  function toGame(message) { frame.contentWindow.postMessage(message, cfg.gameOrigin); }

  function relay(m) {
    switch (m.type) {
      case 'GAME_EVENT': toGame({ type: 'game_event', payload: m.payload }); break;
      case 'GAME_LOG': addLog(m.sequence, m.index, m.payload.entry); break;
      case 'GAME_PLAYER_JOINED': toGame({ type: 'player_joined', player: m.payload.player }); break;
      // The reason is for the platform UI; the FE GDK's player_left carries only the player.
      case 'GAME_PLAYER_LEFT':
        toGame({ type: 'player_left', player: m.payload.player });
        addLog(m.sequence, m.index, '_' + m.payload.player.displayName + ' left (' + m.payload.reason + ')_');
        break;
      case 'GAME_OVER': toGame({ type: 'game_over', results: m.payload.results }); setStatus('game over', false); break;
      case 'SESSION_FAILED': setStatus('session failed: ' + m.payload.reason, true); break;
      default: break;
    }
  }
  const order = (a, b) => (a.sequence - b.sequence) || (a.index - b.index);

  function onMessage(destination, m) {
    if (destination === '/user/queue/harness') { if (m.type === 'HARNESS_RELOAD') frame.contentWindow.location.replace(cfg.gameOrigin + '/'); return; }
    if (destination === '/user/queue/errors') { document.getElementById('debug-result').textContent = m.payload.message; return; }
    if (m.type === 'GAME_SYNC') {
      toGame({ type: 'init', ...m.payload.init });
      toGame({ type: 'game_event', payload: m.payload.view });
      m.payload.log.forEach((l) => addLog(l.sequence, l.index, l.entry));
      synced = true;
      // Anything at or below asOfSequence is already in the view; the rest was fanned out after
      // this tab subscribed and is applied on top, in (sequence, index) order.
      buffer.filter((b) => b.sequence > m.asOfSequence).sort(order).forEach(relay);
      buffer = [];
      return;
    }
    if (m.type === 'SESSION_FAILED' || synced) relay(m); else buffer.push(m);
  }

  // ---- iframe -> shell: the parent's half of the origin check (frontend-gdk-api.md §9) --------
  window.addEventListener('message', (ev) => {
    if (ev.origin !== cfg.gameOrigin || ev.source !== frame.contentWindow) return;
    const m = ev.data;
    if (!m || typeof m !== 'object') return;
    if (m.type === 'ready') {
      syncWanted = true;
      if (connected) requestSync();
    } else if (m.type === 'action') {
      ws.send(frameOf('SEND', { destination: '/app/game.' + cfg.sessionId + '.action', 'content-type': 'application/json' },
        JSON.stringify({ type: 'SUBMIT_ACTION', correlationId: crypto.randomUUID(), payload: m.payload })));
    }
  });

  // ---- panel -----------------------------------------------------------------------------------
  fetch('/__harness/roster').then((r) => r.json()).then((roster) => {
    const viewers = document.getElementById('viewers');
    const as = document.getElementById('as');
    roster.participants.forEach((name) => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = '/?player=' + encodeURIComponent(name);
      a.target = '_blank';
      a.textContent = name;
      li.appendChild(a);
      viewers.appendChild(li);
      const opt = document.createElement('option');
      opt.textContent = name;
      as.appendChild(opt);
    });
    as.value = cfg.player;
    document.getElementById('parameters').textContent = JSON.stringify(roster.parameters, null, 2);
  });
  document.querySelectorAll('button[data-cmd]').forEach((b) => b.addEventListener('click', async () => {
    const cmd = { player: document.getElementById('as').value };
    const raw = document.getElementById('payload').value.trim();
    if (b.dataset.cmd === 'action') cmd.action = raw ? JSON.parse(raw) : {};
    if (b.dataset.cmd === 'join') cmd.join = true;
    if (b.dataset.cmd === 'leave') cmd.leave = document.getElementById('reason').value;
    if (b.dataset.cmd === 'resync') cmd.resync = true;
    const res = await fetch('/__harness/debug', { method: 'POST', headers: { 'X-Harness': '1', 'Content-Type': 'application/json' }, body: JSON.stringify(cmd) });
    document.getElementById('debug-result').textContent = res.ok ? 'delivered' : (await res.text());
  }));

  connect();
})();
