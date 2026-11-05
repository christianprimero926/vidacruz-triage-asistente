-- Un registro que Kafka rechaza de forma permanente no puede bloquear al resto: se cuentan los rechazos y, al agotarse, se descarta.
ALTER TABLE outbox_evento
    ADD COLUMN intentos       INT          NOT NULL DEFAULT 0,
    ADD COLUMN ultimo_error   VARCHAR(500),
    ADD COLUMN fecha_descarte TIMESTAMPTZ;

DROP INDEX ix_outbox_pendientes_secuencia;
CREATE INDEX ix_outbox_pendientes_secuencia ON outbox_evento (secuencia)
    WHERE fecha_publicacion IS NULL AND fecha_descarte IS NULL;

-- Lo usa la CTE primera_consulta del reporte: solo interesan las entradas a consulta.
CREATE INDEX ix_auditoria_primera_consulta ON auditoria_clinica (paciente_id, fecha_evento)
    WHERE tipo_evento = 'paciente_en_consulta';
