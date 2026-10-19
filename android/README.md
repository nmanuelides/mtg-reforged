# MTG Reforged — Android nativo y Windows

Frontend nativo (Kotlin + Compose) para el motor de reglas de
[Forge](https://github.com/Card-Forge/forge), para Android (Jetpack Compose) y Windows 10/11
(Compose for Desktop). Reemplaza la UI de libGDX y reutiliza el motor de upstream sin modificarlo.

## Cómo está armado

```
forge-core / forge-game / forge-ai / forge-gui   ← upstream, sin tocar
android/
  buildSrc/forge-engine-module.gradle.kts         compila cada módulo upstream desde ../<módulo>/src
                                                  leyendo las dependencias de su pom.xml
  engine/<módulo>/build.gradle.kts                un proyecto Gradle por módulo upstream
  shared/                                         UI + lógica comunes a Android y Windows
  app/                                            la app Android (+ su capa platform/)
  desktop/                                        la app Windows (+ su capa platform/), instalador .exe
  engine-data.gradle.kts                          arma engine-data.zip (lo usan app y desktop)
```

- **Código compartido**: `shared/src/main/kotlin` se compila dentro de `app` (Jetpack Compose) y
  de `desktop` (Compose Multiplatform, mismas APIs `androidx.compose.*`). Lo que depende del
  sistema pasa por el paquete `io.github.nmanuelides.mtgreforged.platform`, que cada módulo
  define con los mismos nombres (preferencias, imágenes, archivos, Drive, sonido, back…).
  `desktop/.../R.kt` imita el `R` de Android para las imágenes y fuentes.
- **Windows**: `./gradlew :desktop:run` para probar, `:desktop:packageReleaseExe` para el
  instalador (queda en `dist/mtg-reforged-v<versión>-windows.exe`). `"MTG Reforged.exe" --smoke-test`
  arranca el motor sin ventana y sale con 0 si anda (lo corre CI). Datos del usuario en
  `%LOCALAPPDATA%\MTG Reforged Data` (log en `log.txt`).

- **Motor**: Gradle compila las fuentes de upstream en su lugar. Las dependencias salen de
  los `pom.xml`, así que si upstream agrega o cambia una dependencia el build la toma solo.
- **Datos de reglas** (cartas, ediciones, formatos, perfiles de IA): la tarea
  `packageEngineData` arma `engine-data.zip` desde `forge-gui/res` y lo mete en el APK.
  La app lo extrae en el primer arranque y después de cada actualización, así que código
  y datos vienen siempre del mismo commit.
- **Puente con Forge**: `engine/ReforgedGui` implementa `IGuiBase` y `match/ComposeGuiGame`
  implementa `IGuiGame`. Las preguntas del motor (elegir, ordenar, repartir daño) bloquean
  el hilo de juego en `PromptHost` hasta que la UI responde.

## Actualización automática del motor

`.github/workflows/reforged-android.yml` corre todos los días:

1. mergea `Card-Forge/forge` master en el workspace,
2. compila el APK release firmado,
3. publica un GitHub Release `build-<versionCode>`, solo si cambió el motor o la app,
4. (job `windows`) arma el instalador de Windows, corre el smoke test y lo agrega a ese release.

Al abrirse, la app consulta el último release, avisa si hay uno más nuevo y lo instala.
`versionCode` es la cantidad de commits, así que crece con cada merge de upstream.

Secrets que necesita el workflow: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Opcionales (backup en Drive desde Windows):
`GOOGLE_DESKTOP_CLIENT_ID`, `GOOGLE_DESKTOP_CLIENT_SECRET` (cliente OAuth tipo "Desktop app").

## Desarrollo

```bash
cd android
./gradlew :app:installDebug
```

Necesita JDK 17+ y el Android SDK (compileSdk 36) configurado en `local.properties`.
