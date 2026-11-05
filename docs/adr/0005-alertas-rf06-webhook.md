# ADR 0005: Alertas de escalamiento (RF-06) por webhook

Estado: aceptado

## Contexto

Cuando un paciente supera el SLA de su nivel sin pasar a consulta, se escala y debe avisarse al equipo médico. La alerta no puede perderse si el canal falla, no debe duplicarse con varias réplicas y no debe filtrar datos personales en un canal de chat.

## Decisión

**Canal: webhook HTTP.** El cuerpo trae `text` ("[TRIAGE] Paciente <id> nivel CRITICO escalado: superó SLA de 5 min (lleva X min)") además de `alertaId`, `pacienteId`, `nivel`, `slaMinutos`, `minutosEspera` y `fechaEscalamiento`. El campo `text` hace que el mismo cuerpo sirva en Slack (incoming webhook) y en Teams (flujo de Workflows que recibe una solicitud web). La URL es `WEBHOOK_ALERTAS_URL`; en local apunta a un receptor de demo.

**Persistencia en la misma transacción del escalamiento.** El UPDATE condicional del paciente, la auditoría, el evento del outbox y la fila de `alerta_medica` (estado `PENDIENTE`) se confirman juntos. `alerta_medica.paciente_id` es único: un paciente genera a lo sumo una alerta.

**Despacho con reintentos.** `AlertaDispatcherJob` reclama lotes con `UPDATE ... FOR UPDATE SKIP LOCKED RETURNING`, sin tener una transacción abierta durante el HTTP, y hace el POST con timeout de 3 s. Si sale bien, marca `ENVIADA`. Si falla, reintenta con espera exponencial (base de 30 s) hasta 5 intentos y deja `FALLIDA` con `ultimo_error`. Sin URL configurada queda `FALLIDA` con "webhook no configurado". Métrica `triage.alertas{resultado}`.

**Estado consultable:** `GET /triage/alertas?estado=`, más recientes primero, máximo 200.

**Sin datos personales:** ni nombre ni queja; solo id del paciente, nivel y tiempos.

**Extensión:** la interfaz `CanalAlerta` es el punto para agregar otro canal; hoy existe `WebhookCanalAlerta`.

## Supuestos

- Un solo canal de guardia: el modelo no tiene sede ni sala.
- Quién reconoce o atiende la alerta queda fuera de alcance; no hay estado "reconocida".

## Alternativas consideradas

- SSE o WebSocket: exige un cliente conectado en ese momento; si nadie escucha, la alerta se pierde.
- Correo: lento y poco visible para algo medido en minutos; necesita SMTP.
- SMS o push: costo, proveedor adicional y credenciales que no se pueden dejar en el repositorio.
- Tercer servicio de alertas: ver [ADR 0001](0001-descomposicion-en-dos-servicios.md).

## Consecuencias

- Si el webhook falla los 5 intentos, la alerta queda `FALLIDA` y hay que revisarla en `GET /triage/alertas`; no hay reenvío automático posterior.
- Entrega al menos una vez: un fallo después de recibido el POST pero antes de marcar `ENVIADA` puede duplicar el aviso.
- Con solo el id del paciente en el canal, para ver el detalle hay que consultar el sistema. Es a propósito.
