Exportación JSON real escrita por el código de la etiqueta v1.0.2 (VaultStore.exportText) con KdfParams(1024, 1, 1).
Contraseña maestra: maestra-de-prueba. Entradas: «Banco» (LOGIN, usuario ana, contraseña clave-1.0.2) y «Correo» (TOTP JBSWY3DPEHPK3PXP).
Bóveda real escrita por el código de la etiqueta v1.1.1 (el vault.bin de VaultStore, formato seguridad-vault v2) con KdfParams(1024, 1, 1): vault-1.1.json.
Contraseña maestra: maestra-de-prueba. Entradas: «Banco» (LOGIN, usuario ana, contraseña clave-1.1, con el adjunto hola.txt, texto «hola») y «Correo» (TOTP JBSWY3DPEHPK3PXP). Reloj fijo: masterCheckedAt = 1790000000000 (los tests que desbloquean con clave inyectan un `now` cercano).
Copia real escrita por el código de la etiqueta v1.1.1 (VaultStore.exportBackup, seguridad-backup v1) de esa misma bóveda: backup-1.1.sgbak. Lleva el mismo vault.json (idéntico byte a byte a vault-1.1.json) y el adjunto cifrado en att/.
