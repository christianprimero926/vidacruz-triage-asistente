# ADR 0001: Descomposición en dos servicios sin librería compartida

Estado: aceptado

## Contexto

El enunciado pide dos capacidades con ritmos y dependencias distintas: el triaje de pacientes (datos transaccionales, SLA, escalamiento, auditoría, reportes) y un asistente de consulta de protocolos con estado de urgencias en tiempo casi real. El asistente depende de un proveedor LLM externo y de Redis; el triaje, de PostgreSQL. Además hay que avisar al equipo médico cuando un paciente supera su SLA (RF-06).

## Decisión

Dos servicios independientes:

- `triage-service`: pacientes, clasificación, estados, auditoría, reporte, monitor de SLA, escalamiento y alertas.
- `asistente-service`: RAG sobre el corpus de protocolos y proyección de los eventos a Redis. No tiene base de datos relacional.

No hay librería compartida. El contrato entre ambos es el JSON del evento de `triaje.eventos`, y cada servicio tiene su propia clase para leerlo, con `@JsonIgnoreProperties(ignoreUnknown = true)`. Duplicar un DTO de diez campos cuesta menos que acoplar los ciclos de release de los dos servicios.

Las alertas viven dentro de `triage-service`, no en un tercer servicio. Nacen de un escalamiento, y este ya ocurre en una transacción con el paciente, la auditoría y el outbox. Meter la alerta en esa misma transacción garantiza que no hay escalamiento sin alerta ni alerta sin escalamiento. Un servicio aparte tendría que suscribirse a `paciente_escalado`, y habría otro despliegue, otro estado de reintentos y otro punto de falla para una lógica de unas pocas clases.

## Alternativas consideradas

- Un solo servicio: más simple, pero mezcla la caída del LLM y la carga de ONNX con el flujo clínico transaccional, y no muestra la integración por eventos que pide el enunciado.
- Librería compartida de DTO y utilidades: acopla versiones y obliga a publicar un artefacto. Se descartó.
- Tercer servicio de alertas: ver arriba. Tendría sentido con varios productores de alertas o canales complejos (sedes, turnos, escalas de guardia).

## Consecuencias

- Los servicios se despliegan y escalan por separado. Si triage cae, el asistente sigue respondiendo avisos sin protocolo y el estado de urgencias con lo ya proyectado.
- Hay código duplicado a propósito (filtros de API key y correlación, formato de error, enums de nivel). El evento lleva `version` para poder evolucionar el contrato.
- Si las alertas crecen (varios canales, turnos, reconocimiento), se pueden extraer detrás de la interfaz `CanalAlerta` o de un consumidor de `paciente_escalado` sin cambiar el flujo de escalamiento.
