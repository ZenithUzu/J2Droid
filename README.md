# J2Droid

**J2Droid** convierte juegos Java ME (JAR/JAD) en APK directamente en Android, sin PC ni conexión durante la conversión. Comparte el motor **J2Legacy** con [JarNix para Windows](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## Una sola APK

El instalador distribuido es **J2Droid.apk**. La plantilla del motor se compila como dependencia interna y se empaqueta dentro de J2Droid; no es una segunda APK que debas instalar. Cada juego convertido genera su propia APK, que sí se instala aparte para jugar.

[Descargar la compilación de desarrollo](https://github.com/ZenithUzu/J2Droid/releases/tag/j2droid-latest) · [Ver compilaciones y registros](https://github.com/ZenithUzu/J2Droid/actions)

## Cómo se usa

1. **Importar JAR** (y, si quieres, un JAD).
2. **Crear APK**.
3. **Instalar** o **Guardar** la APK.

**Llaves de firma:** usa **Exportar llaves** antes de borrar o reinstalar J2Droid, e **Importar llaves** después. No se pide contraseña. El mismo archivo `.p12` sirve en JarNix (PC), así los juegos se actualizan desde cualquiera de las dos herramientas sin perder partidas.

**Importante:** la APK de J2Droid de GitHub Actions usa firma de depuración; las compilaciones de desarrollo pueden no actualizarse entre sí sin reinstalar (tus llaves exportadas no se pierden).

## Código y compilación

La fuente Android se sincroniza desde [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit) a este repositorio mediante el workflow [build.yml](.github/workflows/build.yml), que copia `android-offline/`, `runtime/`, `runtime-extra/` y `runtime-m3g/` sin modificarlos, compila y publica **un único instalador**. Todo el código (pantalla, icono, motor y menús de los juegos) se edita en JarNix, así las APK de juegos salen iguales desde PC y desde Android.

Para compilar manualmente después de sincronizar: JDK 17, SDK 35, Build Tools 35, NDK 28.2.13676358; ejecutar `cd android-offline && ./gradlew :converter:assembleDebug`. La primera compilación necesita descargar dependencias.

## Icono adaptable

Taza de Java con la cabeza de Android recortada sobre un degradado violeta → azul → turquesa. Es adaptativo (se ajusta a la forma del lanzador) e incluye capa monocromática para los iconos temáticos de Android 13+. La pantalla de J2Droid usa los colores dinámicos de Material You en Android 12+.

Las APK de juegos usan el icono original que trae cada JAR, igual que las creadas con JarNix.

## Licencia, atribución y colaboración

El código original de J2Droid está disponible bajo la [licencia MIT](LICENSE). Esto **no convierte en MIT** las bibliotecas y fuentes ajenas incluidas: J2ME Loader, M3G, shaders, D8/R8, apksig y otros componentes conservan sus licencias. Lee [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [NOTICE-EXTRA](runtime-extra/NOTICE-EXTRA.md) y [NOTICE-M3G](runtime-m3g/NOTICE-M3G.md). Los juegos que conviertas siguen perteneciendo a sus respectivos titulares.

J2Droid es un proyecto personal y sin fines de lucro. El desarrollo y la documentación han recibido asistencia de IA, incluidos ChatGPT y Claude; las contribuciones deben revisarse y probarse antes de distribuirlas.

## Compatibilidad

Android 8 o posterior. La compatibilidad con juegos Java ME depende de sus APIs, gráficos, audio y posibles extensiones propietarias. No todos los JAR están garantizados. El proyecto es experimental y sin fines de lucro.
