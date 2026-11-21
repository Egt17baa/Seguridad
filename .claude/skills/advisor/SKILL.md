---
name: advisor
description: Use when el usuario escribe «/advisor» o «/Advisor» con un modelo (fable, opus, sonnet, haiku) y, opcionalmente, una pregunta; o cuando pide «una segunda opinión», «que lo mire otro modelo» o «pregúntale a Fable» sobre un diseño, un plan, un diff, un fallo o una decisión.
---

# Advisor: segunda opinión de otro modelo

Lanza un subagente de solo lectura con el modelo pedido para que dé su opinión independiente. **No cambia el modelo de la sesión**; para eso está `/model`.

## Uso

`/advisor <modelo> [pregunta]`

| Argumento | Valores | Por defecto |
|---|---|---|
| `modelo` | `fable` (Fable 5.2), `opus`, `sonnet`, `haiku` | `fable` |
| `pregunta` | texto libre | lo que se está haciendo ahora en la conversación |

Un modelo que no está en la tabla: dilo y no lances nada.

## Pasos

1. **Arma el encargo.** Es lo único que ve el asesor, así que debe valer por sí solo, en este orden:
   - **Pregunta:** una frase con lo que hay que decidir o revisar.
   - **Contexto:** lo necesario de la conversación (decisiones tomadas, restricciones, lo que ya se descartó) y las rutas `archivo:línea` que debe leer. Rutas, no copias del código. Nunca datos personales del dueño ni secretos.
   - **Solo lectura:** no edita, no hace commits, no publica nada.
   - **En este repositorio (Clavero), si la pregunta toca el código o la seguridad de la app:** leer `CLAUDE.md` y `.claude/skills/experto-seguridad-cripto/SKILL.md` antes de opinar, y cerrar con «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con `archivo:línea`.
   - **Formato de respuesta:** el bloque de abajo.
2. **Lánzalo** con la herramienta `Agent`: `model` = el modelo pedido, `subagent_type` = `general-purpose`, `run_in_background: false`.
3. **Si falla el lanzamiento** o el asesor dice ser otro modelo: avísalo. Nunca lo cambies por otro modelo sin decirlo.
4. **Responde al usuario** con el veredicto del asesor y, debajo, en una o dos líneas, si estás de acuerdo o no y por qué. Si discrepáis, muestra las dos posturas; la decisión es del usuario.

## Formato que se pide al asesor

```
Veredicto: <una línea>
Motivos: <3 a 5 puntos, con archivo:línea cuando haya código>
Riesgos o dudas: <lo que no pudo comprobar>
[Las dos secciones de revisión, si el paso 1 las pide]
Modelo: <el modelo que dice ser>
```

## Errores comunes

- Mandar «revisa esto» sin contexto: el asesor empieza de cero y no ve la conversación.
- Pegar archivos enteros en el encargo en vez de dar las rutas.
- Presentar la opinión del asesor como un hecho comprobado: es una opinión; lo que afirme sobre el código se verifica antes de actuar.
- Decir que la sesión ya usa ese modelo: solo lo usa el subagente.
- Fiarse del «solo lectura»: `general-purpose` puede escribir; lo frena solo el encargo. Revisa `git status` después.
