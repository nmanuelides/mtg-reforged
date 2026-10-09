# MTG Reforged — Android nativo

Frontend Android nativo (Kotlin + Jetpack Compose) para el motor de reglas de
[Forge](https://github.com/Card-Forge/forge). Reemplaza la UI de libGDX y reutiliza
el motor de upstream sin modificarlo.

## Cómo está armado

```
forge-core / forge-game / forge-ai / forge-gui   ← upstream, sin tocar
android/
  buildSrc/forge-engine-module.gradle.kts         compila cada módulo upstream desde ../<módulo>/src
                                                  leyendo las dependencias de su pom.xml
  engine/<módulo>/build.gradle.kts                un proyecto Gradle por módulo upstream
  app/                                            la app Compose
```

- **Motor**: Gradle compila las fuentes de upstream en su lugar. Las dependencias salen de
  los `pom.xml`, así que si upstream agrega o cambia una dependencia el build la toma solo.
- **Datos de reglas** (cartas, ediciones, formatos, perfiles de IA): la tarea
  `packageEngineData` arma `engine-data.zip` desde `forge-gui/res` y lo mete en el APK.
  La app lo extrae en el primer arranque y después de cada actualización, así que código
  y datos vienen siempre del mismo commit.
- **Puente con Forge**: `engine/AndroidGui` implementa `IGuiBase` y `match/ComposeGuiGame`
  implementa `IGuiGame`. Las preguntas del motor (elegir, ordenar, repartir daño) bloquean
  el hilo de juego en `PromptHost` hasta que la UI responde.

## Actualización automática del motor

`.github/workflows/reforged-android.yml` corre todos los días:

1. mergea `Card-Forge/forge` master en el workspace,
2. compila el APK release firmado,
3. publica un GitHub Release `build-<versionCode>`, solo si cambió el motor o la app.

Al abrirse, la app consulta el último release, avisa si hay uno más nuevo y lo instala.
`versionCode` es la cantidad de commits, así que crece con cada merge de upstream.

Secrets que necesita el workflow: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Desarrollo

```bash
cd android
./gradlew :app:installDebug
```

Necesita JDK 17+ y el Android SDK (compileSdk 36) configurado en `local.properties`.
