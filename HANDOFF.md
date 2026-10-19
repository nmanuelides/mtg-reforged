# MTG Reforged — traspaso de contexto

Resumen para seguir el trabajo desde otra sesión o cuenta de Claude. Leelo entero antes de empezar.
Estado al 2026-10-18, última versión publicada: **v0.60.0**.

## Qué es

App Android nativa (Kotlin + Jetpack Compose) de Magic: The Gathering sobre el motor de reglas de
Forge. El repo es un fork de Card-Forge/forge (`nmanuelides/mtg-reforged`, remoto `upstream` =
Card-Forge/forge). Todo lo nuestro vive en `android/` (y el workflow de CI); el resto es Forge sin
tocar, compilado tal cual.

- Estilo visual: libro de cuentos / pergamino (`ui/theme/Palette`, `ui/components/Ornaments.kt`),
  pensado primero para tablet, con layout de teléfono en vertical.
- Código: `android/app/src/main/java/io/github/nmanuelides/mtgreforged/`
  - `engine/`: arranque de Forge, mazos, colección (`CardCollection`), precios de Card Kingdom
    (`CardPrices`, con foil), backups (`Backup`, `DriveBackup`), noticias (`News`), favoritos, perfiles.
  - `match/ComposeGuiGame.kt`: el puente con Forge (implementa la GUI de Forge, arma el estado de la
    UI, undo, combate, elecciones). `MatchModels.kt`: el estado que dibuja la UI.
  - `ui/match/`: la partida. `ui/decks/`: constructor de mazos, colección, filtros, visor de cartas.
    `ui/home/`: inicio, backups, buscador de cartas, perfiles.

## Reglas de trabajo con el usuario

- Responder en el idioma en que escribe (casi siempre español rioplatense).
- **Presupuesto ajustado de tokens**: lecturas puntuales con grep, ediciones con scripts, un build por
  tanda. **No probar en pantalla** (nada de capturas en BlueStacks ni la tablet) salvo que lo pida:
  el usuario prueba y al final se le da una checklist de qué probar.
- No tocar el emulador ni la tablet del usuario mientras los usa. adb siempre por serial; BlueStacks:
  `HD-Adb -P 5038 -s 127.0.0.1:5555`.
- Pedir permiso antes de descargar archivos.

## Cada APK nuevo (obligatorio)

1. **Versión**: subir `apk=` en `android/version.properties` en 1. La versión sale de ahí:
   APK 59 → v0.59.0, APK 105 → v1.05.0 (ver `android/app/build.gradle.kts`). Si no se sube, CI cree que
   es un build solo de motor Forge y le agrega una letra (v0.59.0a, b…).
2. **Changelog**: agregar al final de `android/news.json` un mensaje
   `{"id": "v0.60.0", "title": "New in v0.60.0", "body": "• ...", "link": null}`. La app muestra cada id una vez.
3. **Compilar, commitear y pushear** a `master`; el push dispara el workflow que publica la release
   (las apps instaladas se actualizan solas desde las releases de GitHub).

### Cómo compilar y pushear

- Firma: la keystore está en `~/.android-keys/` (nunca se commitea). Variables:
  ```bash
  P=~/.android-keys/mtg-reforged-release.properties; g(){ grep "^$1=" $P | cut -d= -f2-; }
  export RELEASE_KEYSTORE="$(g storeFile)" RELEASE_KEYSTORE_PASSWORD="$(g password)" RELEASE_KEY_PASSWORD="$(g password)" RELEASE_KEY_ALIAS=mtgreforged
  ```
- Chequeo rápido: `cd android && ./gradlew -q :app:compileReleaseKotlin`. APK: `./gradlew -q :app:assembleRelease`
  (queda en `android/dist/mtg-reforged-v<versión>.apk`).
- GitHub: usar la cuenta personal `nmanuelides` **solo por comando**; nunca cambiar la cuenta global de
  `gh` (la activa es la del trabajo, nmanuelidesBanyan):
  ```bash
  T=$(gh auth token --user nmanuelides); H="credential.helper=!f() { echo username=nmanuelides; echo password=$T; }; f"
  git -c credential.helper= -c "$H" pull --no-rebase --no-edit
  git -c credential.helper= -c "$H" push origin master
  ```
- Hacer `pull` antes de compilar y otra vez antes de pushear: CI mergea upstream y pushea a master todos
  los días. Esos merges a veces rompen nuestro Kotlin por cambios de API de Forge (p. ej. `setSelection`
  reemplazó a `setSelectables`/`setHighlighted`): compilar justo después del pull y adaptar los overrides.
- Commits: no commitear `android/dist/`, `local.properties` ni nada de `~/.android-keys`.

### CI (`.github/workflows/reforged-android.yml`)

Diario y en cada push a `android/**`: mergea upstream, compila el APK firmado y publica una release con
tag `build-<versionCode>` (versionCode = cantidad de commits; la app se actualiza leyendo ese número).
Cambios solo en `android/news.json` o `android/PRIVACY.md` no disparan build.

## Detalles técnicos que conviene saber

- **Hilos**: Forge corre en su hilo de juego; la UI en el principal. Leer objetos vivos del juego desde la
  UI solo mientras el juego espera al jugador (InputSelectTargets, InputSelectEntitiesFromList,
  InputAttack); durante el turno de la IA da ConcurrentModificationException.
- **Undo** (`ComposeGuiGame`): `GameSnapshot(game).makeCopy()` antes de cada jugada con prioridad, hasta
  20 pasos. Restaura solo con `InputPassPriority`; antes saca del stack lo que no estaba en el punto
  guardado (el restore de Forge no lo hace). Eligiendo objetivos cancela; atacando retira atacantes y no
  ataca; si no, espera la próxima prioridad (tocar de nuevo cancela). Si copiar falla, el botón explica por qué.
  Desde v0.60 prefiere el `UndoManager` propio de Forge (upstream, `forge.game.undo`): repone el stack exacto,
  pero se corta cuando el rival actúa; ahí sigue nuestro GameSnapshot. Forge convierte Cancel (End Turn) en
  "Undo" cuando puede deshacer: `pressCancel`/`updateButtons` lo mantienen como End Turn.
- **Elecciones fuera de la mesa**: objetivos en cementerio/exilio/biblioteca se eligen en un carrusel
  (`ZoneChoiceDialog`); elecciones de permanentes en `BoardChoiceDialog`.
- **Colección**: cada impresión y acabado (foil) es una entrada separada. En la colección, buscando en
  "All cards" deslizar solo hojea impresiones y + agrega la que se ve; desde "My collection" o la lista,
  deslizar cambia esa copia (`DeckDraft.replacePrinting`). En mazos, cambiar impresión cambia todas las copias.
- **Foil**: port del brillo "holo-rare-ultra" de keldon-market (`CardTile.kt`, textura
  `res/drawable-nodpi/foil_illusion.png`), atenuado a 60 % / textura 55 %.
- **Precios**: lista de Card Kingdom con columna foil (`ck-prices-v2.tsv`).
- **Icono**: `android/branding/icon-source.png` → `res/drawable-nodpi/ic_launcher_art.png` (ícono adaptativo).

## Pendiente / a confirmar con el usuario

- **Backup en Google Drive**: el usuario creó el cliente OAuth de Android (proyecto de Google Cloud
  "MTG-Reforged", paquete `io.github.nmanuelides.mtgreforged`, SHA-1
  `AD:03:F3:55:A7:24:39:B2:7E:4F:D2:1B:98:B3:02:E4:DC:C9:AA:35`). Falta que confirme si ya funciona; el
  último error reportado fue "Google error 8: INTERNAL_ERROR". Si sigue, verificar que el APK de CI esté
  firmado con esa SHA-1 (requiere descargar el APK: pedir permiso).
- **Undo (v0.60.0)**: esperando que pruebe spell en el stack (propio y en respuesta al rival), End Turn, atacantes y objetivos.
- **Colección (v0.58+)**: esperando que pruebe varias impresiones/foil de la misma carta por separado.
- Ideas sin hacer: brillo foil dentro de las partidas (CardView no trae el flag foil); importar foil desde ManaBox.
