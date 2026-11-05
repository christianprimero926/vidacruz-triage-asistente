# ADR 0002: Outbox transaccional y Kafka con entrega al menos una vez

Estado: aceptado

## Contexto

Cada cambio de un paciente (registro, consulta, alta, escalamiento) debe quedar en PostgreSQL y llegar a Kafka para que el asistente actualice el estado de urgencias. Escribir en la base y publicar en Kafka son dos sistemas: sin cuidado, un fallo entre ambos pierde el evento o publica uno de algo que no se confirmó. Además, el orden de los eventos de un mismo paciente importa: un alta no puede procesarse antes que el registro.

## Decisión

**Outbox transaccional.** El dato, la auditoría y la fila de `outbox_evento` se escriben en la misma transacción. Un job (`OutboxPublisherJob`, cada 500 ms) lee los pendientes por `fecha_creacion`, los envía con `KafkaSender` y marca `fecha_publicacion` solo de los confirmados. Si Kafka cae, el registro responde 201 igual y el evento sale cuando vuelva.

**Mensaje.** JSON, clave = `pacienteId`, de modo que todos los eventos de un paciente caen en la misma partición y conservan el orden. Sin datos personales. Cabeceras `tipo-evento`, `correlation-id` y `traceparent`. Productor con `acks=all`, idempotente y timeouts cortos (5 s de bloqueo y de request, 10 s de entrega).

**Al menos una vez, consumidor idempotente.** Si el job envía y se cae antes de marcar, el evento se reenvía. El consumidor lo absorbe: usa sets de Redis (`SADD` y `SREM` sobre `urgencias:activos:<NIVEL>`), así que reaplicar el mismo evento no cambia el resultado.

**Un solo publicador.** El job toma `pg_try_advisory_xact_lock` dentro de una transacción; si no lo obtiene, sale. Así solo una réplica publica y se conserva el orden por paciente. El lock se sostiene durante el envío a Kafka, a propósito: soltarlo antes permitiría que otra réplica lea los mismos pendientes y publique desordenado. Se acepta porque el lote es acotado (`triage.jobs.outbox-lote=100`) y los timeouts del productor son cortos, de modo que la transacción no puede quedar abierta más que unos segundos. Las demás réplicas esperan al siguiente tick.

**reactor-kafka.** Se usa por su integración con Reactor y su backpressure: el consumidor no pide más de lo que procesa y el productor devuelve un `Flux` de resultados. Spring anunció en 2025 la descontinuación del proyecto. Es una deuda reconocida. Rutas de salida: Spring Kafka con un listener que devuelve `Mono`, o el cliente Kafka nativo envuelto en `Mono`/`Flux`. La lógica de negocio no depende de reactor-kafka: queda aislada en el publicador del outbox y en `UrgenciasEventConsumer`.

## Alternativas consideradas

- Publicar a Kafka dentro del request: pierde eventos o crea fantasmas si algo falla entre la base y el broker.
- CDC con Debezium: más infraestructura de la que justifica este alcance.
- Avro o Protobuf con registro de esquemas: mejor contrato, pero agrega un componente. El JSON con campo `version` y lector tolerante alcanza aquí.
- Sin lock, varias réplicas publicando: duplicados y desorden por paciente.

## Consecuencias

- Latencia de publicación de unos cientos de milisegundos (el intervalo del job).
- Duplicados posibles: todo consumidor nuevo debe ser idempotente.
- El caudal de publicación no crece con más réplicas. Para un volumen mayor habría que particionar el outbox por clave, con un lock por partición.
- El topic tiene retención infinita para poder reconstruir el estado de Redis; se paga en disco.
