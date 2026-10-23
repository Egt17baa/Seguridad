# Criptografía moderna aplicada

## Índice
1. Elección rápida por necesidad
2. Cifrado simétrico
3. Contraseñas y derivación de claves
4. Asimétrica: intercambio de claves y firmas
5. Hashes y MAC
6. Aleatoriedad
7. Tokens, JWT y sesiones
8. TLS
9. Poscuántica
10. Antipatrones frecuentes
11. Recetas de código
12. Referencias

## 1. Elección rápida por necesidad

| Necesidad | Primera opción | Alternativa |
|---|---|---|
| Cifrar datos con una clave | AES-256-GCM | ChaCha20-Poly1305; XChaCha20-Poly1305 si los nonces son aleatorios y hay muchos mensajes |
| Guardar contraseñas | Argon2id | scrypt; bcrypt; PBKDF2-HMAC-SHA256 si exigen FIPS |
| Derivar claves a partir de un secreto de alta entropía | HKDF-SHA256 | — |
| Clave a partir de una contraseña, para cifrar | Argon2id o scrypt y después AEAD | PBKDF2 con 600 000 iteraciones o más |
| Integridad con una clave compartida | HMAC-SHA256 | BLAKE2b o BLAKE3 en modo con clave |
| Firmas | Ed25519 | ECDSA P-256 con nonce determinista (RFC 6979); RSA-PSS de 3072 bits |
| Intercambio de claves | X25519 | ECDH P-256; híbrido X25519MLKEM768 |
| Cifrado de clave pública | HPKE (RFC 9180) | RSA-OAEP con SHA-256 (solo para claves pequeñas) |
| Hash general | SHA-256, SHA-512, SHA-3 | BLAKE2 o BLAKE3 |
| Números aleatorios | CSPRNG del sistema | — |

## 2. Cifrado simétrico

- **Usa siempre AEAD** (cifrado autenticado): GCM, ChaCha20-Poly1305 o, si se teme reutilizar nonces, AES-GCM-SIV (RFC 8452). Cifrar sin autenticar (CBC o CTR a secas) abre la puerta a *padding oracle* y a la manipulación del texto cifrado.
- **AES-GCM:** nonce de 96 bits **único por clave**. Reutilizarlo una sola vez revela el XOR de los textos en claro y permite falsificar etiquetas (recuperar la clave H de GHASH). Con nonces aleatorios, el límite es de unos 2³² mensajes por clave (NIST SP 800-38D). Usa la etiqueta completa de 128 bits.
- **ECB nunca:** bloques iguales dan cifrados iguales (el famoso pingüino).
- **CBC** solo con IV aleatorio e impredecible, y con *encrypt-then-MAC*. Para código nuevo, mejor no usarlo.
- **AES-128 frente a AES-256:** las dos son seguras hoy. AES-256 da margen frente a Grover (cuántico) y por eso es lo habitual en código nuevo.
- Los datos asociados (AAD) sirven para ligar el cifrado a su contexto (ID de usuario, versión), de modo que no se pueda mover un cifrado de una fila a otra.

## 3. Contraseñas y derivación de claves

Parámetros mínimos de OWASP (Password Storage Cheat Sheet). Súbelos mientras el tiempo de verificación sea aceptable (unos cientos de ms):
- **Argon2id:** m=19 MiB, t=2, p=1 (o m=46 MiB, t=1, p=1). RFC 9106.
- **scrypt:** N=2¹⁷, r=8, p=1.
- **bcrypt:** coste de 10 o más. Trunca a 72 bytes: o se limita la longitud o se hace un *pre-hash* con cuidado (HMAC y Base64, nunca bytes en bruto con NUL).
- **PBKDF2-HMAC-SHA256:** 600 000 iteraciones o más.
- Sal aleatoria de al menos 16 bytes por usuario (las bibliotecas la incluyen). Una *pepper* opcional, guardada fuera de la base de datos, como HMAC.
- **Nunca** MD5, SHA-1 o SHA-256 a secas, ni con sal, para contraseñas: son rápidos y las GPU prueban miles de millones por segundo.
- **HKDF** sirve para material que ya tiene alta entropía (un secreto ECDH, una clave maestra). No sirve para contraseñas.
- Para comparar etiquetas, hashes o tokens, usa una comparación en tiempo constante: `crypto.timingSafeEqual`, `hmac.compare_digest`.

## 4. Asimétrica

- **Tamaños:** RSA de 2048 bits es el mínimo aceptable; 3072 bits o más si los datos deben protegerse más allá de 2030 (NIST SP 800-57). En curvas elípticas, 256 bits (P-256, Curve25519) equivalen a unos 128 bits de seguridad.
- **RSA:** relleno OAEP para cifrar y PSS para firmar. PKCS#1 v1.5 en cifrado es vulnerable a Bleichenbacher. Exponente e=65537.
- **ECDSA:** un nonce repetido o sesgado filtra la clave privada (PS3, carteras de Bitcoin). Usa RFC 6979 o Ed25519, que es determinista.
- Comprueba que el punto recibido está en la curva (*invalid curve attacks*); X25519 es robusto frente a esto por diseño.
- Una firma no aporta confidencialidad, y el cifrado no aporta autenticidad del emisor.

## 5. Hashes y MAC

- MD5 y SHA-1 tienen colisiones prácticas (SHAttered, 2017): no valen para firmas ni para integridad frente a un adversario. Para sumas de comprobación sin adversario, se toleran.
- **Extensión de longitud:** `SHA256(clave || mensaje)` no es un MAC; un atacante puede añadir datos. Usa HMAC (SHA-3 y BLAKE2 no tienen este problema, pero HMAC es lo estándar).
- Truncar un HMAC a 128 bits es aceptable; por debajo de 64 bits, solo con limitación de intentos.

## 6. Aleatoriedad

- Node: `crypto.randomBytes`, `crypto.randomUUID`, `crypto.getRandomValues`. Navegador: `crypto.getRandomValues`. Python: `secrets`. Go: `crypto/rand`. Linux: `getrandom()` o `/dev/urandom`.
- **Nunca** `Math.random()`, `random` de Python, `rand()` de C ni generadores congruenciales lineales (LCG) para tokens, claves o nonces: son predecibles a partir de unas pocas salidas.
- Los UUID v4 de un CSPRNG valen como identificadores difíciles de adivinar (122 bits). Los UUID v1 y v7 no son secretos.

## 7. Tokens, JWT y sesiones

- **JWT:** fija el algoritmo esperado en el servidor y nunca aceptes `alg: none` ni la confusión RS256/HS256. Con HS256, usa una clave aleatoria de al menos 256 bits. Que caduquen pronto (`exp`); comprueba `aud` e `iss`. Un JWT no se puede revocar sin una lista: para sesiones web suele ser mejor una cookie de sesión opaca.
- Tokens de restablecimiento de contraseña: aleatorios de al menos 128 bits, de un solo uso, que caduquen en poco tiempo y guardados como hash.
- Las API keys se guardan como hash (SHA-256 basta porque tienen alta entropía), con un prefijo identificable para detectarlas si se filtran.

## 8. TLS

- TLS 1.2 como mínimo, mejor 1.3. Desactiva SSLv3, TLS 1.0 y 1.1, RC4, 3DES, los suites EXPORT y el intercambio de claves RSA estático.
- Secreto hacia adelante (ECDHE). Certificados con renovación automática (ACME). HSTS.
- No desactives nunca la verificación de certificados (`rejectUnauthorized: false`, `verify=False`) fuera de pruebas locales.

## 9. Poscuántica

- NIST publicó en agosto de 2024 FIPS 203 (ML-KEM, antes Kyber), FIPS 204 (ML-DSA, antes Dilithium) y FIPS 205 (SLH-DSA, antes SPHINCS+). FN-DSA (Falcon) y HQC están en camino; verifica su estado actual.
- La amenaza inmediata es "recolectar ahora, descifrar después" para datos con vida larga. Lo recomendado es el intercambio híbrido (X25519MLKEM768), que ya despliegan los navegadores y las CDN principales.
- La simétrica y los hashes con claves de 256 bits se consideran adecuados frente a un ordenador cuántico.

## 10. Antipatrones frecuentes (detéctalos)

| Patrón | Problema | Corrección |
|---|---|---|
| `clave = sha256(contraseña)` | Sin sal ni coste: fuerza bruta muy rápida | Argon2id o scrypt para derivar la clave |
| IV o nonce fijo o derivado del contador sin control | Con GCM es catastrófico; con CBC filtra igualdades | Nonce aleatorio de 96 bits o contador garantizado único |
| `createCipher` (sin `iv`) en Node | Obsoleto, deriva clave e IV con MD5 | `createCipheriv` con AEAD |
| AES-CBC sin MAC | *Padding oracle*, maleabilidad | AES-GCM |
| Comparar con `==` o `===` | Ataque de tiempo | `timingSafeEqual` |
| Clave escrita en el código | Quien vea el repositorio, la tiene | Gestor de secretos o variables de entorno; rotación |
| "Cifrado" XOR con clave repetida o Base64 | No es cifrado | AEAD |
| Cifrar contraseñas (reversible) | Si se filtra la clave, se filtran todas | Hash con KDF de contraseñas |
| `Math.random()` para tokens | Predecible | CSPRNG |
| RSA sin relleno o con PKCS#1 v1.5 para cifrar | Ataques algebraicos y Bleichenbacher | OAEP o HPKE |

## 11. Recetas de código

### Node.js: AES-256-GCM con clave derivada de contraseña (scrypt)

```js
import { randomBytes, scryptSync, createCipheriv, createDecipheriv } from 'node:crypto';

const SCRYPT = { N: 2 ** 17, r: 8, p: 1, maxmem: 256 * 1024 * 1024 };

export function cifrar(textoClaro, contraseña) {
  const sal = randomBytes(16);
  const clave = scryptSync(contraseña, sal, 32, SCRYPT);
  const iv = randomBytes(12); // 96 bits, único por mensaje
  const c = createCipheriv('aes-256-gcm', clave, iv);
  const cifrado = Buffer.concat([c.update(textoClaro, 'utf8'), c.final()]);
  // Formato: versión | sal | iv | etiqueta | cifrado
  return Buffer.concat([Buffer.from([1]), sal, iv, c.getAuthTag(), cifrado]).toString('base64');
}

export function descifrar(b64, contraseña) {
  const buf = Buffer.from(b64, 'base64');
  if (buf[0] !== 1) throw new Error('Versión desconocida');
  const sal = buf.subarray(1, 17), iv = buf.subarray(17, 29);
  const etiqueta = buf.subarray(29, 45), cifrado = buf.subarray(45);
  const clave = scryptSync(contraseña, sal, 32, SCRYPT);
  const d = createDecipheriv('aes-256-gcm', clave, iv);
  d.setAuthTag(etiqueta); // si no coincide, final() lanza: nunca devuelvas datos sin verificar
  return Buffer.concat([d.update(cifrado), d.final()]).toString('utf8');
}
```

### Python: contraseñas con Argon2id (`argon2-cffi`)

```python
from argon2 import PasswordHasher
ph = PasswordHasher(time_cost=2, memory_cost=19456, parallelism=1)  # 19 MiB
h = ph.hash(contraseña)
ph.verify(h, intento)            # lanza VerifyMismatchError si no coincide
if ph.check_needs_rehash(h): ...  # actualiza los parámetros al iniciar sesión
```

### Navegador (Web Crypto): AES-GCM

```js
const clave = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
const iv = crypto.getRandomValues(new Uint8Array(12));
const cifrado = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, clave, new TextEncoder().encode(texto));
```

## 12. Referencias

NIST SP 800-38D (GCM), SP 800-57 (gestión de claves), SP 800-63B (autenticación), SP 800-132, FIPS 203/204/205; RFC 5869 (HKDF), RFC 8439 (ChaCha20-Poly1305), RFC 9106 (Argon2), RFC 7748 (X25519), RFC 8032 (EdDSA), RFC 9180 (HPKE), RFC 8446 (TLS 1.3), RFC 8725 (buenas prácticas de JWT); OWASP Cryptographic Storage y Password Storage Cheat Sheets; libros *Serious Cryptography* (Aumasson) y *A Graduate Course in Applied Cryptography* (Boneh y Shoup).
