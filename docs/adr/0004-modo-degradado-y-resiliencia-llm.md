# ADR 0004: Modo degradado y resiliencia del LLM

Estado: aceptado

## Contexto

El LLM es una dependencia externa lenta y con fallas posibles (timeouts, 429, 5xx, clave ausente). Una llamada colgada no debe consumir los hilos del servicio ni arrastrar a los demás endpoints, y el estado de urgencias (Redis) no tiene por qué depender de él.

## Decisión

`LlmGateway` envuelve la llamada bloqueante al `ChatModel` así:

- Se ejecuta en un scheduler propio y acotado (`boundedElastic` con 4 hilos y cola de 20, `asistente.llm.hilos` y `asistente.llm.cola`). Hace de bulkhead: si el LLM se lentifica, solo se agotan esos hilos y esa cola; lo demás sigue respondiendo.
- Timeout de 20 s (`asistente.llm.timeout-segundos`) y `maxRetries=0` en el cliente: los reintentos no los hace el SDK.
- Circuit breaker de Resilience4j `llm`: ventana de 10 llamadas, mínimo 5, abre con 50 % de fallos, 30 s abierto y 3 llamadas en medio abierto. Con el circuito abierto no se llama al modelo.
- Cualquier fallo (timeout, circuito abierto, 4xx, 5xx, cola llena, JSON ilegible) se traduce a `LlmNoDisponibleException`, que responde **503** con mensaje claro y cabecera `Retry-After`. Se mide en `asistente.llm.latencia{resultado=ok|error|circuito_abierto}`.
- Sin `LLM_API_KEY` el servicio arranca. Las consultas que pasan la compuerta responden 503 "no configurado"; las que no tienen protocolo se resuelven sin LLM.
- La readiness no depende del LLM, para que Kubernetes no saque el pod de servicio por una falla externa. El circuit breaker se ve en `/actuator/health` con `allow-health-indicator-to-fail=false`.
- El proveedor se cambia por variables (`LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`) y debe hablar el API compatible con OpenAI (OpenAI, Azure OpenAI, Groq, Gemini en modo compatible, entre otros).

## Alternativas consideradas

- Responder con texto del corpus cuando el LLM cae: se descartó porque sería devolver contenido clínico sin el filtrado del flujo normal. Un 503 es más honesto.
- Reintentos del SDK: duplican la espera y le esconden las fallas al circuit breaker.
- Readiness que incluya el LLM: reinicios y tráfico perdido por algo que el pod no puede arreglar.

## Consecuencias

- Durante una caída del proveedor las consultas con protocolo fallan rápido (circuito abierto) en lugar de acumular peticiones.
- Con la cola llena, las peticiones adicionales fallan de inmediato con 503.
- Los umbrales son valores razonables, no medidos con carga real; hay que ajustarlos con datos de operación.
