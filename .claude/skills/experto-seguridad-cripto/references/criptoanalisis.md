# Criptoanálisis clásico, Enigma y CTF

## Índice
1. Triaje de un texto cifrado desconocido
2. Estadística del idioma
3. Cifrados clásicos y cómo romperlos
4. Enigma
5. CTF de criptografía moderna
6. Herramientas
7. Referencias

## 1. Triaje de un texto cifrado desconocido

Antes de atacar, caracteriza el texto (`scripts/analisis_clasico.py analizar`):

1. **¿Es una codificación y no un cifrado?** Base64 (`A-Za-z0-9+/`, relleno `=`, longitud múltiplo de 4), hex (`0-9a-f`, longitud par), Base32 (`A-Z2-7`, `=`), binario, Morse, URL-encoding, ROT13/ROT47. Descodifica y vuelve a empezar: los CTF suelen encadenar capas.
2. **Alfabeto y longitud.** Solo letras → clásico. Bytes arbitrarios → XOR o cifrado moderno. Solo los dígitos 1-5 por pares → Polibio. Cinco letras ADFGVX → ADFGVX.
3. **Índice de coincidencia (IC).** Probabilidad de que dos letras al azar del texto coincidan.
   - Español ≈ 0,072-0,078; inglés ≈ 0,066; texto aleatorio con 26 letras ≈ 0,038.
   - IC parecido al del idioma y **frecuencias iguales que las del idioma** → transposición (las letras son las de siempre, en otro orden).
   - IC del idioma con frecuencias permutadas → sustitución monoalfabética (César, afín, sustitución general).
   - IC bajo (0,040-0,055) → polialfabético (Vigenère, Beaufort, autoclave) o Enigma.
4. **Repeticiones** (Kasiski): secuencias repetidas cuya distancia comparte divisores, que apuntan a la longitud de la clave.
5. **Contexto:** título del reto, pistas, formato de la bandera (`flag{...}`), fechas o nombres. Un *crib* (texto en claro probable) vale oro.

## 2. Estadística del idioma

Español (aproximado, %): E 13,7 · A 12,5 · O 8,7 · S 8,0 · R 6,9 · N 6,7 · I 6,3 · D 5,9 · L 5,0 · C 4,7 · T 4,6 · U 3,9 · M 3,2 · P 2,5 · B 1,4 · G 1,0 · V 0,9 · Y 0,9 · Q 0,9 · H 0,7 · F 0,7 · Z 0,5 · J 0,4 · Ñ 0,3 · X 0,2 · W/K < 0,1.

Pistas del español: `Q` casi siempre seguida de `U`; bigramas frecuentes DE, EN, ES, EL, LA, OS, AR, ER, RA, UE; palabras cortas DE, LA, QUE, EL, EN, Y, LOS, SE, DEL, LAS, UN, POR. Muchos cifrados históricos quitan tildes y espacios, y cambian la Ñ por N o NN.

Inglés: E T A O I N S H R D L U; bigramas TH HE IN ER AN; palabras THE OF AND TO.

Para puntuar candidatos, χ² contra las frecuencias o, mejor, *log-likelihood* de cuadrigramas.

## 3. Cifrados clásicos y cómo romperlos

- **César / ROT-n:** 26 claves; prueba todas y ordena por χ² (`cesar`).
- **Afín** `E(x) = a·x + b mod 26` con mcd(a, 26) = 1: 312 claves, fuerza bruta. Con dos correspondencias conocidas se resuelve un sistema lineal.
- **Sustitución monoalfabética:** análisis de frecuencias más patrones de palabras (si hay espacios). Para automatizar: *hill climbing* o recocido simulado con puntuación de cuadrigramas, partiendo de la clave por frecuencias.
- **Vigenère:** (1) longitud de clave con Kasiski y con el IC medio por columnas (el pico más pequeño con IC cercano al del idioma; los múltiplos también dan picos); (2) cada columna es un César: χ² por columna; (3) revisa la clave (suele ser una palabra) y corrige las letras dudosas a ojo. Con textos cortos (menos de unas 15 letras por columna), la estadística falla: usa *cribs*.
- **Beaufort:** `C = K − P`. Mismo ataque, cambiando la resta. **Autoclave:** la clave se extiende con el propio texto en claro; se ataca con cribs o *hill climbing*.
- **Transposición columnar:** las frecuencias son normales. Prueba anchos de columna; el anagramado de columnas se puntúa con bigramas. **Rail fence:** pocas claves, fuerza bruta.
- **Playfair:** sin letras dobles en un mismo bigrama, longitud par, sin J (o sin otra letra); se ataca con recocido simulado.
- **Hill:** álgebra lineal mod 26; con n² pares de texto en claro conocido se recupera la matriz.
- **XOR con clave repetida (bytes):** la longitud de clave sale de la distancia de Hamming normalizada; cada columna es un XOR de un byte que se puntúa por texto imprimible o por frecuencias. Con clave de un solo uso reutilizada (*two-time pad*), C1⊕C2 = P1⊕P2: arrastra un crib.

## 4. Enigma

**Mecanismo (Wehrmacht, I):** teclado → tablero de conexiones (*Steckerbrett*, normalmente 10 pares) → 3 rotores elegidos entre 5 (I-V; la Kriegsmarine usaba 8 y la M4 tenía un cuarto rotor fino) → reflector (UKW-B habitual) → de vuelta por los rotores y el tablero → lámpara. El rotor derecho avanza en cada pulsación, antes de cifrar; las muescas hacen avanzar a los siguientes, con la **anomalía del doble paso** del rotor central.

**Clave del día:** orden de rotores (60 opciones con 3 de 5), ajuste de anillos (*Ringstellung*), conexiones del tablero (150 738 274 937 250 opciones con 10 cables) y posición inicial (17 576). El espacio total citado habitualmente, sin contar los anillos, es de unos 1,59×10²⁰ (≈ 2⁶⁷).

**Propiedades criptográficas:**
- Es una **involución**: con la misma posición, cifrar y descifrar son la misma operación (gracias al reflector).
- **Ninguna letra se cifra en sí misma.** Es el defecto clave: permite colocar un *crib* descartando las posiciones en las que alguna letra coincide con el cifrado.
- Es una sustitución polialfabética con un periodo enorme (16 900 = 26·25·26 con 3 rotores, por el doble paso), así que su IC está cerca del aleatorio.

**Historia del ataque:**
- **Marian Rejewski** (Biuro Szyfrów, 1932) reconstruyó el cableado de los rotores con teoría de permutaciones, aprovechando la clave de mensaje repetida dos veces (6 letras) y documentos obtenidos por la inteligencia francesa (Hans-Thilo Schmidt). Le siguieron el ciclómetro, las hojas de Zygalski y la *bomba kryptologiczna* (1938), junto con Różycki y Zygalski.
- En julio de 1939, en Pyry, los polacos entregaron su trabajo a británicos y franceses.
- **Bletchley Park:** la Bombe de **Alan Turing**, mejorada con el tablero diagonal de **Gordon Welchman**, buscaba ajustes coherentes con un *crib* (partes meteorológicas, "KEINE BESONDEREN EREIGNISSE", "WETTERVORHERSAGE") mediante bucles en el "menú". El *Banburismus* (estadística secuencial de Turing) atacó la Enigma naval.
- La Enigma no se rompió por un fallo matemático aislado, sino por la combinación de defectos de diseño (ninguna letra en sí misma), de procedimiento (claves repetidas, mensajes estereotipados, perezosos "cillies") y de capturas (U-110, U-559).

**Romper Enigma hoy:** con un crib se hace un ataque tipo Bombe. Sin crib, el método de Gillogly (1995) y sus mejoras (Weierud y Sullivan): búsqueda de rotores y posición por IC, y luego *hill climbing* del tablero con trigramas o cuadrigramas. Para simular, usa una implementación de referencia y compruébala con un vector de prueba conocido antes de confiar en ella.

## 5. CTF de criptografía moderna

**RSA** (`n, e, c`):
- Factoriza primero: factordb.com; `gcd` entre varios `n` (primo compartido); Fermat si p y q están cerca; Pollard p−1 si p−1 es liso.
- Exponente pequeño sin relleno: si m^e < n, entonces m = raíz e-ésima entera de c. Håstad: el mismo m enviado a e destinatarios, con el teorema chino del resto.
- Módulo común: mismo n, dos e coprimos → Bézout recupera m.
- Wiener (d < n^0,25 / 3) con fracciones continuas; Boneh-Durfee hasta d < n^0,292.
- Coppersmith o Franklin-Reiter para mensajes con relaciones conocidas o bits de clave filtrados.
- Oráculos: LSB/paridad, Bleichenbacher (PKCS#1 v1.5).

**Cifrado de bloque:**
- ECB: bloques repetidos; descifrado byte a byte con texto en claro elegido, y cortar y pegar bloques.
- CBC: *bit flipping* (cambiar un byte del bloque anterior modifica el claro siguiente); *padding oracle* (descifrado completo); IV = clave.
- CTR o GCM con nonce reutilizado: XOR de flujos de clave; *forbidden attack* en GCM.

**Hashes:** extensión de longitud (`hash_extender`, `hashpumpy`); colisiones MD5 con prefijo elegido; contraseñas con `hashcat` o `john` y diccionarios (rockyou).

**PRNG:** LCG (recuperar a, c y m a partir de salidas); Mersenne Twister (624 salidas de 32 bits reconstruyen el estado, `randcrack`); semillas por marca de tiempo.

**Curvas elípticas:** nonce de ECDSA repetido (dos firmas con el mismo r → k → d); curvas anómalas (Smart), orden liso (Pohlig-Hellman), puntos inválidos, MOV.

**Otros:** XOR, OTP reutilizado, Diffie-Hellman con grupo pequeño o primo liso, ataques de tiempo.

## 6. Herramientas

CyberChef (capas de codificación), dCode.fr (clásicos), quipqiup (sustitución), SageMath, PyCryptodome, gmpy2, RsaCtfTool, factordb, hashcat, John the Ripper, Cryptool 2 (incluye Enigma y ataques), y el script `analisis_clasico.py` de esta skill.

## 7. Referencias

Singh, *Los códigos secretos*; Kahn, *The Codebreakers*; Rejewski, "How Polish Mathematicians Deciphered the Enigma" (1980); Welchman, *The Hut Six Story*; Gillogly, "Ciphertext-only cryptanalysis of Enigma" (*Cryptologia*, 1995); cryptopals.com (retos prácticos); CryptoHack.
