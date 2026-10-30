# MTG Reforged relay

Cloudflare Worker that lets friends play online without being on the same network: every app
connects out to it and it pipes the Forge connections through rooms with a 5-letter code.
Hosting needs a device activated with a one-use access code made on `/admin`.

## Deploy (once)

1. Create a free Cloudflare account.
2. From this folder:
   ```
   npx wrangler login
   npx wrangler deploy
   npx wrangler secret put ADMIN_KEY
   ```
   `deploy` prints the URL (`https://mtg-reforged-relay.<your-subdomain>.workers.dev`).
3. Put that URL in `RELAY_URL` in
   `android/shared/src/main/kotlin/io/github/nmanuelides/mtgreforged/engine/Relay.kt` and build the app.

## Access codes

Open `<relay URL>/admin`, type the admin key, and generate codes. Each code activates one
device (the host's) and expires if unused. Devices can be revoked from the same page.

## Limits (free plan)

100,000 Durable Object requests a day (incoming WebSocket messages count 1/20) and 13,000 GB-s
of compute a day. Past a limit, requests fail until 00:00 UTC; nothing is charged.
