## Qué cambia

<!-- Qué hace este cambio y por qué. -->

## Comprobaciones

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` y `./gradlew -p jvm-check test` pasan.
- [ ] `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk` da OK: ningún componente exportado ni permiso nuevo.
- [ ] Los formatos guardados (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, `CP1:`, `.cpenc`, `criptopanel-vault`…) se siguen leyendo, con tests sobre datos reales anteriores si cambia alguno.
- [ ] Las dependencias nuevas son gratuitas y de licencia permisiva (Apache-2.0, MIT, BSD, ISC o CC BY para datos).

## Cambios de seguridad

- [ ] **`gradle/verification-metadata.xml` o `jvm-check/gradle/verification-metadata.xml`:** si este PR los cambia, el cambio se revisa como un cambio de seguridad. Cada entrada añadida o cambiada (un `<component` nuevo o una suma `<sha256` nueva o distinta en un componente que ya estaba) corresponde a una dependencia o un plugin que este PR añade o actualiza a propósito, y su versión y su origen están comprobados. El trabajo **dependency-metadata** de la CI dice en el resumen cuántas entradas `<component` y cuántas sumas se añaden, cambian o quitan.
- [ ] Si toca `.github/workflows/`, los secretos de firma solo se leen en los trabajos del entorno `firma` (y, mientras sigan en el repositorio, solo en ejecuciones desde `main` o desde una etiqueta `v*`), y las acciones nuevas están fijadas por SHA.
