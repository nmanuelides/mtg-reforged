# MTG Reforged — traspaso de contexto

Resumen para seguir el trabajo desde otra sesión o cuenta de Claude. Leelo entero antes de empezar.
Estado al 2026-10-19, última versión: **v0.68.0** (primera con versión de Windows).

## Qué es

App nativa de Magic: The Gathering sobre el motor de reglas de Forge, para Android (Kotlin + Jetpack
Compose) y, desde v0.68, Windows 10/11 (Kotlin + Compose for Desktop, mismo código). El repo es un fork de Card-Forge/forge (`nmanuelides/mtg-reforged`, remoto `upstream` =
Card-Forge/forge). Todo lo nuestro vive en `android/` (y el workflow de CI); el resto es Forge sin
tocar, compilado tal cual.

- Estilo visual: libro de cuentos / pergamino (`ui/theme/Palette`, `ui/components/Ornaments.kt`),
  pensado primero para tablet, con layout de teléfono en vertical.
- Código compartido (UI + lógica, el 95 %): `android/shared/src/main/kotlin/io/github/nmanuelides/mtgreforged/`.
  Se compila dentro de `android/app` (Android) y de `android/desktop` (Windows). Lo que depende del
  sistema va por el paquete `platform` (preferencias, imágenes, archivos, Drive, sonido, back, toasts…),
  que `app/.../platform/` y `desktop/.../platform/` definen **con los mismos nombres** (sin KMP:
  typealias `AppContext`/`Prefs`/`ResId`). `desktop/.../R.kt` imita el `R` de Android. Si se agrega una
  llamada nueva al sistema en `shared/`, hay que agregarla en las dos capas.
- Solo Android en `app/`: `MainActivity`, `AppUpdater` (APK), `ImageDownloadService` (servicio con
  notificación), `Connectivity`, `AndroidAudioClip`, `platform/`. Solo Windows en `desktop/`: `Main.kt`
  (ventana, Esc = back, F11, toasts), su `AppUpdater` (baja el .exe del release y lo ejecuta), etc.
- Código (rutas dentro de shared/):
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

## Cada versión nueva (obligatorio; sale APK y .exe)

1. **Versión**: subir `apk=` en `android/version.properties` en 1. La versión sale de ahí:
   APK 59 → v0.59.0, APK 105 → v1.05.0 (ver `android/app/build.gradle.kts`). Si no se sube, CI cree que
   es un build solo de motor Forge y le agrega una letra (v0.59.0a, b…).
2. **Changelog**: agregar al final de `android/news.json` un mensaje
   `{"id": "v0.60.0", "title": "New in v0.60.0", "body": "• ...", "link": null}`. La app muestra cada id una vez.
3. **Compilar, commitear y pushear** a `master`; el push dispara el workflow que publica la release
   (las apps instaladas se actualizan solas desde las releases de GitHub). Compilar **las dos**:
   `./gradlew -q :app:compileReleaseKotlin :desktop:compileKotlin` (cambios en `shared/` afectan a ambas).

### Cómo compilar y pushear

- Firma: la keystore está en `~/.android-keys/` (nunca se commitea). Variables:
  ```bash
  P=~/.android-keys/mtg-reforged-release.properties; g(){ grep "^$1=" $P | cut -d= -f2-; }
  export RELEASE_KEYSTORE="$(g storeFile)" RELEASE_KEYSTORE_PASSWORD="$(g password)" RELEASE_KEY_PASSWORD="$(g password)" RELEASE_KEY_ALIAS=mtgreforged
  ```
- Chequeo rápido: `cd android && ./gradlew -q :app:compileReleaseKotlin :desktop:compileKotlin`. APK:
  `./gradlew -q :app:assembleRelease` (queda en `android/dist/mtg-reforged-v<versión>.apk`).
- Windows: `./gradlew -q :desktop:packageReleaseExe` → `android/dist/mtg-reforged-v<versión>-windows.exe`
  (instalador por usuario, sin admin, se actualiza encima). Probar sin abrir ventana:
  `./gradlew -q :desktop:createReleaseDistributable` y
  `"desktop/build/compose/binaries/main-release/app/MTG Reforged/MTG Reforged.exe" --smoke-test` (exit 0 = el
  motor arrancó). Datos y `log.txt` en `%LOCALAPPDATA%\MTG Reforged Data` (la app se instala en `%LOCALAPPDATA%\MTG Reforged`).
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
Después el job `windows` (windows-latest) hace el mismo merge, arma el instalador, corre el smoke test y
sube el .exe a esa misma release; la app de Windows busca el `.exe` del último release.
Cambios solo en `android/news.json` o `android/PRIVACY.md` no disparan build.

## Detalles técnicos que conviene saber

- **Hilos**: Forge corre en su hilo de juego; la UI en el principal. Leer objetos vivos del juego desde la
  UI solo mientras el juego espera al jugador (InputSelectTargets, InputSelectEntitiesFromList,
  InputAttack); durante el turno de la IA da ConcurrentModificationException.
- **Undo** (`ComposeGuiGame`, rehecho en v0.62): en cada prompt de prioridad del jugador se graba el juego
  entero con `GameMemento` de Forge (snapshot reflexivo in-place, incluye stack y habilidades), hasta 20.
  Undo restaura el prompt anterior **solo** mientras el hilo del juego está en `InputPassPriority`, y después
  `restartPrompt()` para que Forge vuelva a preguntar. En otro input: si es una jugada propia en curso
  (costes, objetivos, maná) cancela cada paso; atacando retira atacantes; si no, espera al próximo prompt
  (tocar de nuevo cancela). El `UndoManager` de Forge se apaga (pref `UI_UNDO_MODE=Off`). Partidas
  retomadas (v0.64): cada entrada guarda también su posición en texto (`SavedGames.position`: GameState +
  stack + jugadores elegidos) y el save lleva las últimas 20 (`history`); al retomar, el undo más allá del
  historial en memoria aplica esas posiciones con `GameState.applyToGame` (menos exacto que el memento).
  Saves viejos: solo `undoState` (spell propio en el stack). (`GameSnapshot` ya no se usa.)
- **Retomar**: `GameState.setupPlayerState` mete cada permanente con `moveToPlay` (se ve entrar carta por
  carta) y eso vuelve a correr los "As X enters, choose" (efectos de reemplazo; `|NoETBTrigs` NO los
  evita: `moveTo(Battlefield)` = `moveToPlay`). Desde v0.65, mientras se arma el tablero
  (`ComposeGuiGame.layingBoard`) cada pregunta se responde con el default sin mostrarse, y después
  `SavedGames.applyChoices` repone los jugadores elegidos/recordados guardados (`choices`, desde v0.63).
  Las opcionales se responden "no"/ninguna. Al retomar, el save se borra pero queda copia en
  `/sdcard/Android/data/io.github.nmanuelides.mtgreforged/files/last-resumed-save.json` (adb pull); cada save
  deja `last-save.json` ahí, y un `import-save.json` puesto ahí reemplaza el save al arrancar (v0.67).
  Tablet: adb inalámbrico (Samsung, 192.168.1.43, emparejada); el release no es debuggable (sin run-as).
- **Pendiente**: el usuario perdió una Rise of the Dark Realms de la mano al retomar (v0.65); causa sin confirmar.
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

- **Windows (v0.68.0)**: recién hecho; el usuario tiene que probarlo (instalar el .exe, partida completa,
  constructor, colección, imágenes offline, backup a archivo, sonido, actualización). En PC: Esc = atrás,
  clic derecho = mantener apretado (ver carta), arrastrar con el mouse = deslizar, rueda = zoom del avatar.
- **Drive en Windows**: usa OAuth "Desktop app" (loopback + PKCE, `desktop/.../platform/DriveAuth.kt`).
  Falta que el usuario cree ese cliente en el proyecto de Google Cloud y cargue `GOOGLE_DESKTOP_CLIENT_ID`
  / `GOOGLE_DESKTOP_CLIENT_SECRET` como secrets de GitHub (o `~/.android-keys/google-desktop-oauth.properties`
  con `clientId=`/`clientSecret=` para builds locales). Sin eso, el diálogo dice que no está configurado.

- **Backup en Google Drive**: el usuario creó el cliente OAuth de Android (proyecto de Google Cloud
  "MTG-Reforged", paquete `io.github.nmanuelides.mtgreforged`, SHA-1
  `AD:03:F3:55:A7:24:39:B2:7E:4F:D2:1B:98:B3:02:E4:DC:C9:AA:35`). Falta que confirme si ya funciona; el
  último error reportado fue "Google error 8: INTERNAL_ERROR". Si sigue, verificar que el APK de CI esté
  firmado con esa SHA-1 (requiere descargar el APK: pedir permiso).
- **Undo (v0.62.0)**: rehecho; esperando que pruebe varios pasos seguidos, undo pagando costes, spells en el stack y turno de la IA.
- **Colección (v0.58+)**: esperando que pruebe varias impresiones/foil de la misma carta por separado.
- Ideas sin hacer: brillo foil dentro de las partidas (CardView no trae el flag foil); importar foil desde ManaBox.
