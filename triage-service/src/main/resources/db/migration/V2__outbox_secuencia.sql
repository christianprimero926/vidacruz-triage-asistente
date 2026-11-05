-- El orden de publicación no puede depender del reloj: dos eventos del mismo instante deben salir en el orden en que se guardaron.
ALTER TABLE outbox_evento ADD COLUMN secuencia BIGINT GENERATED ALWAYS AS IDENTITY;

CREATE INDEX ix_outbox_pendientes_secuencia ON outbox_evento (secuencia)
    WHERE fecha_publicacion IS NULL;
