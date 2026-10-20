"""Builds the deployable site into _site/: index.html wrapped in a full HTML document, plus img/.

index.html is written as a page body (that is how it is previewed as a Claude artifact);
Netlify needs a complete document. Deploy with: netlify deploy --prod --dir _site
"""
import pathlib
import shutil

here = pathlib.Path(__file__).parent
out = here / "_site"
shutil.rmtree(out, ignore_errors=True)
shutil.copytree(here / "img", out / "img")

page = (here / "index.html").read_text(encoding="utf-8")
head_end = page.index("</style>") + len("</style>")
head, body = page[:head_end], page[head_end:]
doc = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<link rel="icon" type="image/png" href="img/icon.png">
<meta property="og:title" content="MTG Reforged">
<meta property="og:description" content="Magic: The Gathering for Android and Windows 10/11, on the Forge rules engine.">
<meta property="og:image" content="https://mtg-reforged.netlify.app/img/board.webp">
{head}
</head>
<body>
{body}
</body>
</html>
"""
(out / "index.html").write_text(doc, encoding="utf-8")
print("built", out)
