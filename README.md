# J2Droid

**J2Droid** convierte juegos Java ME (JAR/JAD) en APK directamente en Android, sin PC ni conexión durante la conversión. Comparte el motor **J2Legacy** con [JarNix para Windows](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## Una sola APK

El instalador distribuido es **J2Droid.apk**. La plantilla del motor se compila como dependencia interna y se empaqueta dentro de J2Droid; no es una segunda APK que debas instalar. Cada juego convertido genera su propia APK, que sí se instala aparte para jugar.

[Descargar la compilación de desarrollo](https://github.com/ZenithUzu/J2Droid/releases/tag/j2droid-latest) · [Ver compilaciones y registros](https://github.com/ZenithUzu/J2Droid/actions)

**Importante:** la APK de GitHub Actions usa firma de depuración; las compilaciones de desarrollo pueden no actualizarse entre sí sin reinstalar. La identidad usada para firmar los juegos convertidos se guarda por separado en la aplicación y puede exportarse en formato PKCS#12. Conserva su respaldo.

## Código y compilación

La fuente Android se sincroniza desde [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit) a este repositorio mediante el workflow [build.yml](.github/workflows/build.yml), que copia `android-offline/`, `runtime/`, `runtime-extra/` y `runtime-m3g/`, incorpora el icono de J2Droid, compila y publica **un único instalador**. Los cambios del motor deben hacerse en JarNix, origen compartido; las personalizaciones específicas de J2Droid se mantienen aquí en `branding/`.

Para compilar manualmente después de sincronizar: JDK 17, SDK 35, Build Tools 35, NDK 28.2.13676358; ejecutar `cd android-offline && ./gradlew :converter:assembleDebug`. La primera compilación necesita descargar dependencias.

## Icono adaptable

El icono combina un robot Android y una taza de Java. Incluye capas adaptativas y una capa monocromática para iconos temáticos en Android 13+ (cuando el lanzador y el usuario habiliten esa función). El icono normal conserva su propio color; no cambia automáticamente en todos los dispositivos.

## Compatibilidad

Android 8 o posterior. La compatibilidad con juegos Java ME depende de sus APIs, gráficos, audio y posibles extensiones propietarias. No todos los JAR están garantizados. El proyecto es experimental y sin fines de lucro.
