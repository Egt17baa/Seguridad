# Clavero 2.0: `security-review` de la rama (20-11-2026)

- **Objetivo:** la rama `ccr-0aa27f95-u038cd`, frente a `main`. HEAD `5492b76`; el código de `app/src/main` es el mismo que en `84f8820`.
- **Método:** el de la skill `security-review`. Un subagente busca vulnerabilidades, en solo lectura y sin Gradle, y después se filtran los falsos positivos. Solo se informa de lo que tenga una confianza de 0,8 o más.
- **Resultado:** ninguna vulnerabilidad alta ni media con confianza de 0,8 o más. Al no haber hallazgos, no hubo nada que filtrar.

## Qué se comprobó

- **Rutas y ids de bóveda:**
  - `VaultRegistry.dirOf` solo admite `principal` o `[0-9a-f]{32}`;
  - los ids nuevos salen de `SecureRandom`;
  - los adjuntos van en la carpeta de cada bóveda (`VaultStore.kt:188`);
  - `vaultTag` nunca se usa como nombre de archivo (`BackupCheck.kt:292`).
- **Aislamiento entre bóvedas:**
  - `switchTo` cierra la puerta antes de bloquear (`AppContainer.kt:554-586`);
  - `opened` rechaza abrir y pone la clave a cero si la bóveda ya no está activa o la época cambió (`VaultStore.kt:506-520`);
  - `active()` se vuelve a comprobar antes y después de Argon2id (`:591`, `:612`) y en `unlockWithKeyLocked` (`:707`, `:741`);
  - «Importar como bóveda nueva» sigue bloqueada hasta el cambio (`AppContainer.kt:611-624`);
  - la época del bloqueo de toda la app corta el portapapeles pendiente (A14) y la copia en dos pasos;
  - `deleteVault` pide la maestra y el nombre (`AppContainer.kt:715-760`).
- **Huella y Keystore:**
  - un alias por bóveda con `perVaultName`;
  - todas las llamadas pasan el `store.id` de su almacén (`VaultScreen.kt:239`, `:451`; los seis `disable`; `SeguridadApp.kt:41`);
  - la huella nunca escribe un archivo v2 ni v3 (`biometricRefusal`; `VaultCrypto.write` solo admite v4).
- **Datos hostiles (decisión (c)):**
  - `LimitedMarkup` y `MarkupText` solo dan estilos: sin enlaces ni `Intent`, sin recursión y con topes;
  - el QR de wifi no usa `Intent` ni `WifiManager`;
  - la copia con marca 3 tiene sus propios topes, y no hay *zip-slip* (`Backup.kt`);
  - la profundidad y `checkCopyShape` se comprueban antes de decodificar.
- **Exposición de datos:**
  - los `rememberSaveable` nuevos no guardan secretos: los colores, la página de la guía y una URI de carpeta;
  - el único `navArgument` nuevo es el id de un campo (`AppNavHost.kt:377-390`);
  - los textos del QR viven en memoria y se borran al bloquear y al salir (`MainActivity.kt`, `onStop`);
  - `FLAG_SECURE` cubre toda la ventana (`SecureWindow.kt:24`);
  - el manifiesto no cambia, y no hay nuevos `Log`, `WebView`, `Html`, `startActivity` ni `ACTION_VIEW`.
- **Criptografía y red:**
  - en `Crypto.kt` solo cambian mensajes de error;
  - la v4 ata el formato, la versión, el coste y la sal en el AAD;
  - un archivo sin versión nunca se prueba como v4;
  - no hay hosts nuevos.

## Lo que no se leyó línea a línea, para la auditoría completa

La búsqueda de API peligrosas no encontró nada en estos sitios, pero no se leyeron enteros:
- `AutoBackup.kt`: el alcance de los borrados de A6 por bóveda y `copyBeforeUpgrade`;
- `SettingsStore.kt`: la migración por bóveda, que no debe bajar el perfil de la derivación ni perder la marca del reloj de A11;
- las pantallas grandes de Compose: `VaultList`, `VaultOrganize`, `VaultPickerScreen` y `EntryFields`.

## Revisión experto-seguridad-cripto

- **Aislamiento:**
  - la puerta se cierra antes de bloquear;
  - se vuelve a comprobar después del trabajo lento;
  - la clave se pone a cero al rechazar.
- **Criptografía:**
  - solo primitivas estándar;
  - el AAD ata la versión;
  - Argon2id no cambia.
- **Sin rebaja de versión:** un archivo sin versión solo se prueba como 3 y 2, y la huella nunca escribe un archivo v2 ni v3.

## Revisión Ingeniero Móvil Senior

- **Keystore:**
  - un alias por bóveda;
  - todo va en `KeyOpsQueue`;
  - la clave se borra con su bóveda.
- **Sin secretos guardados en estado:** `onStop` borra lo pendiente, y no hay secretos en `rememberSaveable` ni en las rutas.
- **MASVS:**
  - `FLAG_SECURE` en toda la ventana;
  - ni componentes exportados ni permisos nuevos.
