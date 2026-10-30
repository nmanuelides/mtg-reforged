// MTG Reforged online relay (Cloudflare Worker + Durable Objects).
//
// Players behind routers / CGNAT can't reach each other, so every connection goes out to this
// relay and it pipes the bytes. The host keeps a "control" WebSocket open on its room; each
// friend that joins opens a "guest" WebSocket, the room tells the host, and the host opens an
// "accept" WebSocket bridged to its local Forge server. The relay never reads the game data.
//
// Hosting needs an activated device: an access code (made on /admin, one use, expires) is
// redeemed for a device token. Joining a room only needs the room code.

import { DurableObject } from "cloudflare:workers";

const ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const MAX_GUESTS = 6; // tunnels per room: 3 friends, with room for a reconnect each

function randomString(alphabet, length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

/** Access codes and activated devices, in one SQLite-backed object. */
export class Registry extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS codes (
      code TEXT PRIMARY KEY, note TEXT, created INTEGER, expires INTEGER, token TEXT, used INTEGER)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS devices (
      token TEXT PRIMARY KEY, name TEXT, code TEXT, created INTEGER, last_seen INTEGER, revoked INTEGER DEFAULT 0)`);
  }

  newCodes(count, days, note) {
    const now = Date.now();
    const codes = [];
    for (let i = 0; i < count; i++) {
      const code = randomString(CODE_ALPHABET, 4) + "-" + randomString(CODE_ALPHABET, 4);
      this.sql.exec("INSERT INTO codes (code, note, created, expires) VALUES (?, ?, ?, ?)", code, note || "", now, now + days * 86400000);
      codes.push(code);
    }
    return codes;
  }

  redeem(code, name) {
    const row = this.sql.exec("SELECT * FROM codes WHERE code = ?", code.toUpperCase().trim()).toArray()[0];
    if (!row) return { error: "That code doesn't exist." };
    if (row.token) return { error: "That code was already used." };
    if (row.expires < Date.now()) return { error: "That code has expired." };
    const token = randomString("abcdef0123456789", 40);
    const now = Date.now();
    this.sql.exec("UPDATE codes SET token = ?, used = ? WHERE code = ?", token, now, row.code);
    this.sql.exec("INSERT INTO devices (token, name, code, created, last_seen) VALUES (?, ?, ?, ?, ?)", token, name || "", row.code, now, now);
    return { token };
  }

  check(token) {
    const row = this.sql.exec("SELECT revoked FROM devices WHERE token = ?", token || "").toArray()[0];
    if (!row || row.revoked) return false;
    this.sql.exec("UPDATE devices SET last_seen = ? WHERE token = ?", Date.now(), token);
    return true;
  }

  list() {
    return {
      codes: this.sql.exec("SELECT code, note, created, expires, used FROM codes ORDER BY created DESC LIMIT 500").toArray(),
      devices: this.sql.exec("SELECT token, name, code, created, last_seen, revoked FROM devices ORDER BY created DESC LIMIT 500").toArray(),
    };
  }

  revoke(token) {
    this.sql.exec("UPDATE devices SET revoked = 1 WHERE token = ?", token);
    return { ok: true };
  }

  deleteCode(code) {
    this.sql.exec("DELETE FROM codes WHERE code = ? AND token IS NULL", code);
    return { ok: true };
  }
}

/**
 * One game room. Sockets are tagged so the room survives hibernation:
 *   "control" + "v:<app version>"   the host's control socket
 *   "guest" + "g:<id>"               a friend's tunnel
 *   "accept" + "h:<id>"              the host's end of that tunnel
 */
export class Room extends DurableObject {
  async fetch(request) {
    const url = new URL(request.url);
    const role = url.searchParams.get("role");
    const version = url.searchParams.get("v") || "";
    const { 0: client, 1: server } = new WebSocketPair();

    if (role === "control") {
      if (this.ctx.getWebSockets("control").length > 0) return new Response("taken", { status: 409 });
      this.ctx.acceptWebSocket(server, ["control", "v:" + version]);
      server.send(JSON.stringify({ room: url.searchParams.get("room") }));
    } else if (role === "guest") {
      const control = this.ctx.getWebSockets("control")[0];
      if (!control) return json({ error: "No game with that code." }, 404);
      const hostVersion = (this.ctx.getTags(control).find((t) => t.startsWith("v:")) || "v:").slice(2);
      if (hostVersion !== version) return json({ error: "The host has a different app version. Both of you need the latest update." }, 409);
      if (this.ctx.getWebSockets("guest").length >= MAX_GUESTS) return json({ error: "That game is full." }, 409);
      const id = randomString("abcdefghijklmnopqrstuvwxyz0123456789", 10);
      this.ctx.acceptWebSocket(server, ["guest", "g:" + id]);
      control.send(JSON.stringify({ open: id }));
    } else if (role === "accept") {
      const id = url.searchParams.get("id");
      const guest = this.ctx.getWebSockets("g:" + id)[0];
      if (!guest) return new Response("gone", { status: 404 });
      this.ctx.acceptWebSocket(server, ["accept", "h:" + id]);
      // The friend's app holds its bytes until the host's end is there.
      guest.send(JSON.stringify({ ready: true }));
    } else {
      return new Response("bad role", { status: 400 });
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  peerOf(ws) {
    const tags = this.ctx.getTags(ws);
    const g = tags.find((t) => t.startsWith("g:"));
    if (g) return this.ctx.getWebSockets("h:" + g.slice(2))[0];
    const h = tags.find((t) => t.startsWith("h:"));
    if (h) return this.ctx.getWebSockets("g:" + h.slice(2))[0];
    return null;
  }

  webSocketMessage(ws, message) {
    // Game data is binary; text frames are the app's own keep-alives.
    if (typeof message === "string") return;
    const peer = this.peerOf(ws);
    if (peer) peer.send(message);
  }

  webSocketClose(ws, code, reason) {
    this.closed(ws);
  }

  webSocketError(ws) {
    this.closed(ws);
  }

  closed(ws) {
    if (this.ctx.getTags(ws).includes("control")) {
      // The host left: the game is over for everyone.
      for (const other of this.ctx.getWebSockets()) if (other !== ws) other.close(1000, "host left");
      return;
    }
    const peer = this.peerOf(ws);
    if (peer) peer.close(1000, "peer left");
  }
}

function isAdmin(request, env) {
  return env.ADMIN_KEY && request.headers.get("x-admin-key") === env.ADMIN_KEY;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const registry = env.REGISTRY.get(env.REGISTRY.idFromName("main"));
    const upgrade = request.headers.get("Upgrade") === "websocket";

    if (path === "/redeem" && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      const result = await registry.redeem(String(body.code || ""), String(body.name || "").slice(0, 60));
      return json(result, result.error ? 403 : 200);
    }

    if (path === "/check") {
      return json({ ok: await registry.check(url.searchParams.get("token")) });
    }

    // Host: open a room with a fresh code.
    if (path === "/host" && upgrade) {
      if (!(await registry.check(url.searchParams.get("token")))) return json({ error: "This device isn't activated for online play." }, 403);
      for (let attempt = 0; attempt < 5; attempt++) {
        const room = randomString(ROOM_ALPHABET, 5);
        const target = new URL(request.url);
        target.searchParams.set("role", "control");
        target.searchParams.set("room", room);
        const response = await env.ROOM.get(env.ROOM.idFromName(room)).fetch(new Request(target, request));
        if (response.status !== 409) return response;
      }
      return json({ error: "Couldn't open a room, try again." }, 503);
    }

    // Friend: /join/ABCDE?v=<app version>
    let match = path.match(/^\/join\/([A-Za-z0-9]{5})$/);
    if (match && upgrade) {
      const room = match[1].toUpperCase();
      const target = new URL(request.url);
      target.searchParams.set("role", "guest");
      return env.ROOM.get(env.ROOM.idFromName(room)).fetch(new Request(target, request));
    }

    // Host's end of a friend's tunnel: /accept/ABCDE?id=...&token=...
    match = path.match(/^\/accept\/([A-Za-z0-9]{5})$/);
    if (match && upgrade) {
      if (!(await registry.check(url.searchParams.get("token")))) return new Response("forbidden", { status: 403 });
      const target = new URL(request.url);
      target.searchParams.set("role", "accept");
      return env.ROOM.get(env.ROOM.idFromName(match[1].toUpperCase())).fetch(new Request(target, request));
    }

    if (path === "/admin") {
      return new Response(ADMIN_PAGE, { headers: { "content-type": "text/html; charset=utf-8" } });
    }

    if (path === "/admin/api" && request.method === "POST") {
      if (!isAdmin(request, env)) return json({ error: "Wrong admin key." }, 401);
      const body = await request.json().catch(() => ({}));
      switch (body.action) {
        case "list": return json(await registry.list());
        case "new": return json({ codes: await registry.newCodes(Math.min(Math.max(1, body.count | 0), 200), Math.max(1, body.days | 0 || 7), String(body.note || "")) });
        case "revoke": return json(await registry.revoke(String(body.token)));
        case "deleteCode": return json(await registry.deleteCode(String(body.code)));
        default: return json({ error: "Unknown action." }, 400);
      }
    }

    return new Response("MTG Reforged relay", { status: 404 });
  },
};

const ADMIN_PAGE = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Reforged Relay Admin</title>
<style>
  :root { --bg:#f6efe1; --ink:#2b2418; --soft:#6d6250; --accent:#a8492f; --line:#d8ccb4; color-scheme: light; }
  @media (prefers-color-scheme: dark) { :root { --bg:#1c1915; --ink:#ece3d2; --soft:#a89c87; --accent:#e07a5a; --line:#3a342b; color-scheme: dark; } }
  body { background:var(--bg); color:var(--ink); font:15px/1.45 system-ui, sans-serif; margin:0 auto; max-width:900px; padding:16px; }
  h1 { font-size:20px; } h2 { font-size:16px; margin-top:28px; }
  input, button { font:inherit; padding:6px 10px; border:1px solid var(--line); border-radius:6px; background:transparent; color:var(--ink); }
  button { background:var(--accent); color:#fff; border:none; cursor:pointer; }
  button.small { padding:2px 8px; font-size:13px; }
  table { border-collapse:collapse; width:100%; font-size:13px; } td, th { border-bottom:1px solid var(--line); padding:6px 4px; text-align:left; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; } .muted { color:var(--soft); } code { font-size:15px; }
  #out { white-space:pre-wrap; font-family:ui-monospace, monospace; font-size:16px; }
  .scroll { overflow-x:auto; }
</style></head><body>
<h1>Online access codes</h1>
<div class="row"><input id="key" type="password" placeholder="Admin key" autocomplete="current-password"><button onclick="load()">Load</button></div>
<p class="muted">Set or change the admin key from the repo's <code>relay</code> folder:<br>
<code>npx wrangler@4 secret put ADMIN_KEY</code></p>
<h2>New codes</h2>
<div class="row">
  <input id="count" type="number" value="5" min="1" max="200" style="width:80px"> codes,
  valid for <input id="days" type="number" value="7" min="1" style="width:70px"> days,
  note <input id="note" placeholder="who they're for">
  <button onclick="make()">Generate</button>
</div>
<p id="out"></p>
<h2>Activated devices</h2><div class="scroll"><table id="devices"></table></div>
<h2>Codes</h2><div class="scroll"><table id="codes"></table></div>
<script>
const key = document.getElementById("key");
try { key.value = sessionStorage.getItem("k") || ""; } catch (e) {}
async function api(body) {
  try { sessionStorage.setItem("k", key.value); } catch (e) {}
  const r = await fetch("/admin/api", { method:"POST", headers:{ "content-type":"application/json", "x-admin-key":key.value }, body:JSON.stringify(body) });
  const j = await r.json(); if (j.error) { alert(j.error); throw new Error(j.error); } return j;
}
const day = (t) => t ? new Date(t).toLocaleString() : "";
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;" })[c]);
async function load() {
  const { codes, devices } = await api({ action:"list" });
  document.getElementById("devices").innerHTML = "<tr><th>Name</th><th>Code</th><th>Activated</th><th>Last seen</th><th></th></tr>" +
    devices.map(d => "<tr><td>"+esc(d.name)+"</td><td>"+esc(d.code)+"</td><td>"+day(d.created)+"</td><td>"+day(d.last_seen)+"</td><td>"+
      (d.revoked ? "<span class=muted>revoked</span>" : "<button class=small onclick=\\"revoke('"+d.token+"')\\">Revoke</button>")+"</td></tr>").join("");
  document.getElementById("codes").innerHTML = "<tr><th>Code</th><th>Note</th><th>Expires</th><th>Used</th><th></th></tr>" +
    codes.map(c => "<tr><td><code>"+c.code+"</code></td><td>"+esc(c.note)+"</td><td>"+day(c.expires)+"</td><td>"+(c.used ? day(c.used) : "<span class=muted>no</span>")+"</td><td>"+
      (c.used ? "" : "<button class=small onclick=\\"del('"+c.code+"')\\">Delete</button>")+"</td></tr>").join("");
}
async function make() {
  const { codes } = await api({ action:"new", count:+document.getElementById("count").value, days:+document.getElementById("days").value, note:document.getElementById("note").value });
  document.getElementById("out").textContent = codes.join("\\n"); load();
}
async function revoke(token) { if (confirm("Revoke this device?")) { await api({ action:"revoke", token }); load(); } }
async function del(code) { await api({ action:"deleteCode", code }); load(); }
</script></body></html>`;
