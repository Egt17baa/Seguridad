Exportación JSON real escrita por el código de la etiqueta v1.0.2 (VaultStore.exportText) con KdfParams(1024, 1, 1).
Contraseña maestra: maestra-de-prueba. Entradas: «Banco» (LOGIN, usuario ana, contraseña clave-1.0.2) y «Correo» (TOTP JBSWY3DPEHPK3PXP).
Bóveda real escrita por el código de la etiqueta v1.1.1 (el vault.bin de VaultStore, formato seguridad-vault v2) con KdfParams(1024, 1, 1): vault-1.1.json.
Contraseña maestra: maestra-de-prueba. Entradas: «Banco» (LOGIN, usuario ana, contraseña clave-1.1, con el adjunto hola.txt, texto «hola») y «Correo» (TOTP JBSWY3DPEHPK3PXP). Reloj fijo: masterCheckedAt = 1790000000000 (los tests que desbloquean con clave inyectan un `now` cercano).
Copia real escrita por el código de la etiqueta v1.1.1 (VaultStore.exportBackup, seguridad-backup v1) de esa misma bóveda: backup-1.1.sgbak. Lleva el mismo vault.json (idéntico byte a byte a vault-1.1.json) y el adjunto cifrado en att/.
Bóveda escrita por el código de la 1.4.3 (main en a3ac942: VaultCrypto.create y VaultCrypto.write, formato seguridad-vault v3) con KdfParams(1024, 1, 1): vault-1.4.3.json (2.0, tarea 1).
Contraseña maestra: maestra-de-prueba-143. Datos inventados. Reloj fijo: masterCheckedAt = 1800000000000, revision 1. Entradas, en este orden:
«Foro de ejemplo» (e1, LOGIN, usuaria / clave-actual-143, https://foro.example, nota «nota del acceso», favorita, etiqueta «trabajo», passwordHistory: clave-vieja-2 en 1799999998000 y clave-vieja-1 en 1799999997500),
«Nota de prueba» (e2, NOTE, «texto de la nota»), «Correo de ejemplo» (e3, TOTP JBSWY3DPEHPK3PXP, emisor Ejemplo, cuenta usuaria@example.org)
y «Cartera de prueba» (e4, SEED, la frase de prueba pública de BIP-39 «abandon … about»). Ninguna tiene adjuntos.
