# Auditoría de código y webs

## Índice
1. Método
2. Webs: cabeceras HTTP
3. Webs estáticas y JAMstack
4. Código: qué buscar por categoría
5. Secretos y cadena de suministro
6. Herramientas
7. Referencias

## 1. Método

1. **Inventario.** Qué se despliega, dónde (Netlify, Cloudflare, servidor propio), qué entradas recibe (formularios, parámetros, ficheros, cabeceras), qué datos guarda y quién se autentica.
2. **Modelo de amenaza.** Atacante anónimo en Internet, usuario autenticado malicioso, dependencia comprometida, alguien con acceso al repositorio. Descarta los que no apliquen y dilo.
3. **Revisión dirigida.** Sigue los datos desde la entrada hasta el sumidero (HTML, SQL, shell, sistema de ficheros, `eval`, redirecciones, peticiones salientes). Lee el código, no solo los nombres de las funciones.
4. **Verificación.** Cuando puedas, confirma ejecutando: generar el sitio y leer las cabeceras reales (`curl -sI`), lanzar los tests, `npm audit`. Un hallazgo comprobado vale más que diez supuestos.
5. **Informe**, con el formato de `SKILL.md`.

## 2. Webs: cabeceras HTTP

Valores de referencia razonables (ajústalos al sitio):

| Cabecera | Valor recomendado | Por qué |
|---|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; upgrade-insecure-requests` | Limita el XSS y la inyección de recursos. Evita `'unsafe-inline'` y `'unsafe-eval'` en `script-src`; si hace falta código en línea, usa nonces o hashes. |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` | Obliga a usar HTTPS. Pon `preload` solo si todos los subdominios sirven HTTPS. |
| `X-Content-Type-Options` | `nosniff` | Evita que el navegador adivine el tipo MIME. |
| `Referrer-Policy` | `strict-origin-when-cross-origin` o `no-referrer` | No filtra rutas ni parámetros a terceros. |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), interest-cohort=()` | Desactiva las API que no se usan. |
| `Cross-Origin-Opener-Policy` | `same-origin` | Aísla la ventana frente a ataques entre orígenes. |
| `X-Frame-Options` | `DENY` | Protección contra clickjacking para navegadores antiguos; la principal es `frame-ancestors`. |

Notas:
- `X-XSS-Protection` está obsoleta; lo correcto es `0` o no enviarla.
- Una CSP solo en `<meta>` no admite `frame-ancestors` ni `report-uri`: es mejor enviarla como cabecera.
- Comprueba la CSP con lo que el sitio carga de verdad (fuentes, imágenes, analítica). Una CSP que rompe la web acaba desactivada.
- Cookies: `Secure; HttpOnly; SameSite=Lax` (o `Strict`), y el prefijo `__Host-` cuando sea posible.

## 3. Webs estáticas y JAMstack

Superficie típica, que suele ser pequeña:
- **Cabeceras** en `_headers` (Netlify y Cloudflare Pages), `netlify.toml` (`[[headers]]`) o en el Worker. Con Cloudflare Workers Static Assets, el fichero `_headers` dentro del directorio de recursos también se aplica.
- **XSS en el generador:** contenido Markdown o YAML renderizado a HTML sin sanear. `marked` no sanea el HTML incrustado por defecto. Si el contenido es del propio autor el riesgo es bajo, pero si entra contenido de terceros (PR, CMS) hace falta DOMPurify o `sanitize-html`.
- **Enlaces externos:** `target="_blank"` sin `rel="noopener"` (los navegadores modernos ya lo aplican por defecto, así que el riesgo es bajo). Comprueba que todos usan `https:` y nunca `javascript:`.
- **Recursos de terceros:** CDN sin Subresource Integrity (`integrity=` y `crossorigin`).
- **Servidor de previsualización local:** *path traversal* (`../`), que escuche en `0.0.0.0` en vez de `127.0.0.1`, o listado de directorios.
- **Ficheros filtrados en `dist/`:** `.env`, `.git`, mapas de código fuente o borradores.
- **Formularios** que envían a terceros y datos personales (RGPD).

## 4. Código: qué buscar por categoría

Basado en el OWASP Top 10. La edición de 2025 reordena las categorías y añade "Software Supply Chain Failures" y "Mishandling of Exceptional Conditions"; verifica la lista vigente si vas a citar números.

- **Control de acceso:** IDOR (`/api/pedidos/123` sin comprobar el propietario), comprobaciones solo en el cliente, CORS con `*` y credenciales, CSRF.
- **Configuración:** modo depuración en producción, errores con trazas, listado de directorios, cabeceras ausentes, permisos de buckets.
- **Cadena de suministro:** dependencias sin versión fija, `postinstall` sospechosos, acciones de GitHub sin fijar a un SHA, *typosquatting*, ausencia de lockfile.
- **Criptografía:** ver `cripto-moderna.md`.
- **Inyección:** SQL concatenado, `child_process.exec` con entrada del usuario, plantillas sin escapar, `innerHTML`, `eval`, `new Function`, deserialización insegura (`pickle`, `yaml.load` sin `SafeLoader`), XXE, *prototype pollution* (`Object.assign` o *merge* profundo con claves `__proto__`).
- **Diseño:** falta de límites de intentos, flujos de recuperación de cuenta débiles, lógica de negocio.
- **Autenticación:** contraseñas sin KDF, sesiones que no rotan tras el inicio de sesión, JWT con `alg: none` o claves débiles, MFA ausente donde importa.
- **Integridad:** actualizaciones sin firma, CI que ejecuta código de PR de forks con secretos.
- **Registro:** no se registran los fallos de autenticación, o se registran secretos y datos personales.
- **Excepciones:** *fail-open* (un error en la comprobación concede acceso), errores tragados.
- **SSRF:** peticiones salientes a URL controladas por el usuario (metadatos de la nube en `169.254.169.254`).
- **ReDoS:** expresiones regulares con cuantificadores anidados aplicadas a entrada externa.
- **Path traversal:** `path.join(base, entrada)` sin comprobar que el resultado sigue dentro de `base` (usa `path.resolve` y `startsWith(base + path.sep)`).

## 5. Secretos y cadena de suministro

- Busca claves en el código y en el historial: `git log -p | grep -iE "api[_-]?key|secret|token|password|BEGIN (RSA|EC|OPENSSH) PRIVATE"`, o mejor gitleaks o trufflehog.
- Si un secreto llegó a un commit público, **está comprometido**: hay que revocarlo y rotarlo. Reescribir el historial no basta.
- `npm audit --omit=dev`, `pip-audit`, `osv-scanner`. Distingue las vulnerabilidades alcanzables de las que no lo son.
- Fija versiones exactas y usa lockfile. En GitHub Actions, `permissions:` mínimos y acciones fijadas por SHA.

## 6. Herramientas

`curl -sI`, Mozilla HTTP Observatory, securityheaders.com, CSP Evaluator de Google, Semgrep, CodeQL, gitleaks, `npm audit`, OWASP ZAP (solo contra lo autorizado), testssl.sh y SSL Labs para TLS.

## 7. Referencias

OWASP ASVS 4.0.3 / 5.0, OWASP Cheat Sheet Series, OWASP Top 10, CWE Top 25, MDN Web Security, RFC 6797 (HSTS), W3C CSP Level 3.
