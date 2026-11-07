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
import ADMIN_PAGE from "./admin.html";

const ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const MAX_GUESTS = 6; // tunnels per room: 3 friends, with room for a reconnect each
const REPORT_GAP = 2 * 60000; // between two bug reports from one person
const REPORTS_PER_DAY = 5; // from one person
const REPORTS_PER_DAY_ALL = 100; // from everyone: the last line against a flood

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
    this.sql.exec(`CREATE TABLE IF NOT EXISTS installs (
      id TEXT PRIMARY KEY, platform TEXT, version TEXT, first_seen INTEGER, last_seen INTEGER)`);
    this.sql.exec("CREATE TABLE IF NOT EXISTS reports (who TEXT, at INTEGER)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS pings (day TEXT, id TEXT, PRIMARY KEY (day, id))");
    this.sql.exec(`CREATE TABLE IF NOT EXISTS messages (
      id TEXT PRIMARY KEY, title TEXT, body TEXT, link TEXT, target TEXT, created INTEGER)`);
    this.sql.exec("CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, name TEXT, email TEXT, os TEXT, updated INTEGER)");
    // Throws once the column exists, which is the normal case.
    for (const column of ["country", "region", "city", "timezone"]) {
      try { this.sql.exec(`ALTER TABLE installs ADD COLUMN ${column} TEXT`); } catch (e) {}
    }
  }

  /** The Google name and email of a player who agreed to share them, linked to their install. */
  saveProfile(id, name, email, os) {
    this.sql.exec(`INSERT INTO profiles (id, name, email, os, updated) VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET name = excluded.name, email = excluded.email, os = excluded.os, updated = excluded.updated`,
      id, name, email, os, Date.now());
    return { ok: true };
  }

  forgetProfile(id, email) {
    this.sql.exec("DELETE FROM profiles WHERE id = ? OR email = ?", id, email);
    return { ok: true };
  }

  newMessage(title, body, link, target) {
    const id = "m-" + randomString("abcdefghijklmnopqrstuvwxyz0123456789", 12);
    this.sql.exec("INSERT INTO messages (id, title, body, link, target, created) VALUES (?, ?, ?, ?, ?, ?)", id, title, body, link, target, Date.now());
    return { id };
  }

  messages() {
    return { messages: this.sql.exec("SELECT * FROM messages ORDER BY created DESC LIMIT 100").toArray() };
  }

  /** What an app on [platform] should show. */
  messagesFor(platform) {
    return { messages: this.sql.exec("SELECT id, title, body, link FROM messages WHERE target = 'all' OR target = ? ORDER BY created DESC LIMIT 20", platform).toArray() };
  }

  deleteMessage(id) {
    this.sql.exec("DELETE FROM messages WHERE id = ?", id);
    return { ok: true };
  }

  /** Why [who] (a hash of the Google account) can't send a bug report now, or null if they can. */
  reportBlocked(who) {
    const now = Date.now();
    this.sql.exec("DELETE FROM reports WHERE at < ?", now - 86400000);
    const mine = this.sql.exec("SELECT at FROM reports WHERE who = ? ORDER BY at DESC", who).toArray();
    if (mine.length >= REPORTS_PER_DAY) return "You've reached today's limit of reports. Try again tomorrow.";
    if (mine.length && now - mine[0].at < REPORT_GAP) {
      const minutes = Math.ceil((REPORT_GAP - (now - mine[0].at)) / 60000);
      return `You just sent a report. Try again in ${minutes} minute${minutes === 1 ? "" : "s"}.`;
    }
    const all = this.sql.exec("SELECT COUNT(*) AS n FROM reports").one().n;
    return all >= REPORTS_PER_DAY_ALL ? "Too many reports today. Try again tomorrow." : null;
  }

  reportSent(who) {
    this.sql.exec("INSERT INTO reports (who, at) VALUES (?, ?)", who, Date.now());
  }

  /** One anonymous install saying it's alive: a random id, never an IP or anything about the player. */
  /** [place]: where Cloudflare places the request's IP (country, region, city, time zone); the IP itself isn't kept. */
  ping(id, platform, version, place) {
    const now = Date.now();
    this.sql.exec(`INSERT INTO installs (id, platform, version, country, region, city, timezone, first_seen, last_seen)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET platform = excluded.platform, version = excluded.version, country = excluded.country,
        region = excluded.region, city = excluded.city, timezone = excluded.timezone, last_seen = excluded.last_seen`,
      id, platform, version, place.country, place.region, place.city, place.timezone, now, now);
    this.sql.exec("INSERT OR IGNORE INTO pings (day, id) VALUES (?, ?)", new Date(now).toISOString().slice(0, 10), id);
    this.sql.exec("DELETE FROM pings WHERE day < ?", new Date(now - 90 * 86400000).toISOString().slice(0, 10));
    return { ok: true };
  }

  installs() {
    const now = Date.now();
    return {
      platforms: this.sql.exec(`SELECT platform, COUNT(*) AS total,
        SUM(last_seen > ?) AS active30, SUM(last_seen > ?) AS active7, SUM(first_seen > ?) AS new7
        FROM installs GROUP BY platform ORDER BY total DESC`, now - 30 * 86400000, now - 7 * 86400000, now - 7 * 86400000).toArray(),
      versions: this.sql.exec(`SELECT version, platform, COUNT(*) AS total FROM installs WHERE last_seen > ?
        GROUP BY version, platform ORDER BY version DESC LIMIT 40`, now - 30 * 86400000).toArray(),
      places: this.sql.exec(`SELECT country, region, COUNT(*) AS total FROM installs
        GROUP BY country, region ORDER BY total DESC LIMIT 60`).toArray(),
      daily: this.sql.exec(`SELECT p.day, COUNT(*) AS active, SUM(date(i.first_seen / 1000, 'unixepoch') = p.day) AS fresh
        FROM pings p JOIN installs i ON i.id = p.id WHERE p.day >= ? GROUP BY p.day`, new Date(now - 30 * 86400000).toISOString().slice(0, 10)).toArray(),
      list: this.sql.exec(`SELECT i.id, i.platform, i.version, i.country, i.region, i.city, i.timezone, i.first_seen, i.last_seen,
          p.name, p.email, p.os
        FROM installs i LEFT JOIN profiles p ON p.id = i.id ORDER BY i.last_seen DESC LIMIT 200`).toArray(),
      shared: this.sql.exec("SELECT COUNT(*) AS n FROM profiles").one().n,
      reports: this.sql.exec("SELECT COUNT(*) AS n FROM reports WHERE at > ?", now - 86400000).one().n,
    };
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

/** The Google account behind a Drive token, asked of Google so the app can't claim to be someone else. */
async function googleUser(token) {
  const response = await fetch("https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress)", {
    headers: { authorization: "Bearer " + token },
  });
  if (!response.ok) return null;
  const { user } = await response.json();
  return user?.emailAddress ? user : null;
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
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

    if (path === "/ping" && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      const id = String(body.id || "");
      const platform = String(body.platform || "");
      if (!/^[0-9a-f]{32}$/.test(id) || !["Android", "Windows"].includes(platform)) return json({ error: "Bad ping." }, 400);
      return json(await registry.ping(id, platform, String(body.version || "").slice(0, 20), {
        country: String(request.cf?.country || ""), region: String(request.cf?.region || "").slice(0, 80),
        city: String(request.cf?.city || "").slice(0, 80), timezone: String(request.cf?.timezone || "").slice(0, 60),
      }));
    }

    // A player who agreed to share their account. Google says who they are, not the app.
    if ((path === "/profile" || path === "/profile/forget") && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      const id = String(body.id || "");
      if (!/^[0-9a-f]{32}$/.test(id)) return json({ error: "Bad request." }, 400);
      const user = await googleUser(String(body.token || ""));
      if (!user) return json({ error: "Couldn't verify your Google account." }, 401);
      if (path === "/profile/forget") return json(await registry.forgetProfile(id, user.emailAddress));
      return json(await registry.saveProfile(id, String(user.displayName || "").slice(0, 100), user.emailAddress, String(body.os || "").slice(0, 40)));
    }

    // Messages from /admin, read by the app next to the changelog.
    if (path === "/messages") {
      return json(await registry.messagesFor(String(url.searchParams.get("platform") || "")));
    }

    // Bug report: goes to the developer's Telegram with the sender's verified Google account.
    if (path === "/report" && request.method === "POST") {
      if (!env.TELEGRAM_TOKEN || !env.TELEGRAM_CHAT) return json({ error: "Bug reports aren't available right now." }, 503);
      const body = await request.json().catch(() => ({}));
      const text = String(body.text || "").trim().slice(0, 1000);
      if (!text) return json({ error: "Write what happened first." }, 400);
      const user = await googleUser(String(body.token || ""));
      if (!user) return json({ error: "Couldn't verify your Google account. Connect it from Backup and try again." }, 401);
      const who = await sha256(user.emailAddress.toLowerCase());
      const blocked = await registry.reportBlocked(who);
      if (blocked) return json({ error: blocked }, 429);
      const about = String(body.platform || "?").slice(0, 20) + " · v" + String(body.version || "?").slice(0, 20);
      const sent = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_TOKEN}/sendMessage`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          chat_id: env.TELEGRAM_CHAT,
          text: `🐞 Bug report\n${user.displayName || ""} <${user.emailAddress}>\n${about}\n\n${text}`,
          link_preview_options: { is_disabled: true },
        }),
      });
      if (!sent.ok) return json({ error: "Couldn't send the report. Try again later." }, 502);
      await registry.reportSent(who);
      return json({ ok: true });
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
        case "installs": return json(await registry.installs());
        case "messages": return json(await registry.messages());
        case "sendMessage": {
          const title = String(body.title || "").trim().slice(0, 120);
          const text = String(body.body || "").trim().slice(0, 2000);
          const link = String(body.link || "").trim().slice(0, 300);
          const target = ["all", "Android", "Windows"].includes(body.target) ? body.target : "all";
          if (!title || !text) return json({ error: "Write a title and a message." }, 400);
          if (link && !/^https:\/\//.test(link)) return json({ error: "The link must start with https://" }, 400);
          return json(await registry.newMessage(title, text, link, target));
        }
        case "deleteMessage": return json(await registry.deleteMessage(String(body.id)));
        case "new": return json({ codes: await registry.newCodes(Math.min(Math.max(1, body.count | 0), 200), Math.max(1, body.days | 0 || 7), String(body.note || "")) });
        case "revoke": return json(await registry.revoke(String(body.token)));
        case "deleteCode": return json(await registry.deleteCode(String(body.code)));
        default: return json({ error: "Unknown action." }, 400);
      }
    }

    return new Response("MTG Reforged relay", { status: 404 });
  },
};
