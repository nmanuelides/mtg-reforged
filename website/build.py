"""Builds the deployable site into _site/: index.html wrapped in a full HTML document, plus img/.

index.html is written as a page body (that is how it is previewed as a Claude artifact);
Netlify needs a complete document. Deploy with: netlify deploy --prod --dir _site
"""
import datetime
import json
import pathlib
import shutil

SITE = "https://mtg-reforged.netlify.app"
# Google Search Console's HTML-tag verification code, if any (the content="..." value).
VERIFY_FILE = pathlib.Path(__file__).parent / "google-verification.txt"

here = pathlib.Path(__file__).parent
out = here / "_site"
shutil.rmtree(out, ignore_errors=True)
shutil.copytree(here / "img", out / "img")

page = (here / "index.html").read_text(encoding="utf-8")
head_end = page.index("</style>") + len("</style>")
head, body = page[:head_end], page[head_end:]
code = VERIFY_FILE.read_text(encoding="utf-8").strip() if VERIFY_FILE.exists() else ""
VERIFY = f'<meta name="google-site-verification" content="{code}">\n' if code else ""
LDJSON = json.dumps({
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    "name": "MTG Reforged",
    "alternateName": ["MTG Reforged app", "Forge for Android"],
    "url": SITE + "/",
    "image": SITE + "/img/board.webp",
    "description": "Free Magic: The Gathering app for Android and Windows 10/11 on the Forge rules engine: "
                   "Commander against up to three AI opponents, a deck builder with daily prices, a collection "
                   "tracker with a card scanner, offline play and Google Drive sync.",
    "applicationCategory": "GameApplication",
    "genre": "Card game",
    "operatingSystem": "Android, Windows 10, Windows 11",
    "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
    "license": "https://www.gnu.org/licenses/gpl-3.0.html",
    "sameAs": ["https://github.com/nmanuelides/mtg-reforged"],
    "downloadUrl": "https://github.com/nmanuelides/mtg-reforged/releases/latest",
})
doc = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<link rel="icon" type="image/png" href="img/icon.png">
<link rel="apple-touch-icon" href="img/icon.png">
<link rel="canonical" href="{SITE}/">
<link rel="alternate" hreflang="en" href="{SITE}/">
<link rel="alternate" hreflang="es" href="{SITE}/">
<link rel="alternate" hreflang="x-default" href="{SITE}/">
<meta name="robots" content="index, follow">
<meta name="keywords" content="MTG Reforged, Magic: The Gathering, MTG, Forge, Commander, EDH, MTG app, Android, Windows, deck builder, collection tracker, play Magic offline, AI opponents">
{VERIFY}<meta property="og:type" content="website">
<meta property="og:site_name" content="MTG Reforged">
<meta property="og:url" content="{SITE}/">
<meta property="og:title" content="MTG Reforged · Free Magic: The Gathering app for Android and Windows">
<meta property="og:description" content="Play Commander against AI opponents on the Forge rules engine, build decks with daily prices, track your collection and play offline. Free, no ads.">
<meta property="og:image" content="{SITE}/img/board.webp">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="MTG Reforged">
<meta name="twitter:description" content="Free Magic: The Gathering app for Android and Windows, on the Forge rules engine.">
<meta name="twitter:image" content="{SITE}/img/board.webp">
<script type="application/ld+json">{LDJSON}</script>
{head}
</head>
<body>
{body}
</body>
</html>
"""
(out / "index.html").write_text(doc, encoding="utf-8")

# Search engines: allow everything and point them at the sitemap.
(out / "robots.txt").write_text(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}/sitemap.xml\n", encoding="utf-8")
(out / "sitemap.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n'
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    f"  <url><loc>{SITE}/</loc><lastmod>{datetime.date.today().isoformat()}</lastmod></url>\n"
    "</urlset>\n",
    encoding="utf-8",
)
print("built", out)
