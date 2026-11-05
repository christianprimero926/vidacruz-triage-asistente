CREATE TABLE paciente (
    id                  UUID PRIMARY KEY,
    nombre_completo     VARCHAR(100)  NOT NULL,
    edad                INT           NOT NULL,
    frecuencia_cardiaca INT           NOT NULL,
    presion_sistolica   INT           NOT NULL,
    presion_diastolica  INT           NOT NULL,
    spo2                INT           NOT NULL,
    temperatura         NUMERIC(5, 2) NOT NULL,
    dolor               INT           NOT NULL,
    gcs                 INT           NOT NULL,
    queja_principal     VARCHAR(500)  NOT NULL,
    nivel               VARCHAR(20)   NOT NULL
        CONSTRAINT ck_paciente_nivel CHECK (nivel IN ('CRITICO', 'URGENTE', 'URGENTE_2', 'NO_URGENTE', 'NO_URGENTE_2')),
    sla_minutos         INT           NOT NULL,
    estado              VARCHAR(20)   NOT NULL
        CONSTRAINT ck_paciente_estado CHECK (estado IN ('REGISTRADO', 'EN_CONSULTA', 'ALTA')),
    escalado            BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_registro      TIMESTAMPTZ   NOT NULL,
    fecha_estado        TIMESTAMPTZ   NOT NULL,
    fecha_escalamiento  TIMESTAMPTZ,
    sla_vence_en        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_paciente_fecha_registro ON paciente (fecha_registro);

-- Lo consulta el monitor de SLA: solo los pacientes que aún pueden escalarse.
CREATE INDEX ix_paciente_sla_pendiente ON paciente (sla_vence_en)
    WHERE estado = 'REGISTRADO' AND escalado = FALSE;

CREATE TABLE auditoria_clinica (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id     UUID         NOT NULL REFERENCES paciente (id),
    tipo_evento     VARCHAR(30)  NOT NULL
        CONSTRAINT ck_auditoria_tipo_evento CHECK (tipo_evento IN
            ('paciente_registrado', 'paciente_en_consulta', 'paciente_alta', 'paciente_escalado')),
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20)  NOT NULL,
    fecha_evento    TIMESTAMPTZ  NOT NULL,
    correlation_id  VARCHAR(64)
);

CREATE INDEX ix_auditoria_paciente_fecha ON auditoria_clinica (paciente_id, fecha_evento);

-- Última barrera de RN-03: un paciente solo puede tener un escalamiento auditado.
CREATE UNIQUE INDEX uq_auditoria_escalamiento ON auditoria_clinica (paciente_id)
    WHERE tipo_evento = 'paciente_escalado';

-- RN-04: la auditoría es inmutable también para quien entre directo a la base.
CREATE FUNCTION auditoria_clinica_inmutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La auditoría clínica es inmutable: no se permite % sobre auditoria_clinica', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_auditoria_clinica_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_clinica
    FOR EACH ROW EXECUTE FUNCTION auditoria_clinica_inmutable();

CREATE TABLE outbox_evento (
    id                UUID PRIMARY KEY,
    paciente_id       UUID         NOT NULL,
    tipo_evento       VARCHAR(30)  NOT NULL,
    payload           TEXT         NOT NULL,
    correlation_id    VARCHAR(64),
    traceparent       VARCHAR(64),
    fecha_creacion    TIMESTAMPTZ  NOT NULL,
    fecha_publicacion TIMESTAMPTZ
);

CREATE INDEX ix_outbox_pendientes ON outbox_evento (fecha_creacion)
    WHERE fecha_publicacion IS NULL;

CREATE TABLE alerta_medica (
    id               UUID PRIMARY KEY,
    paciente_id      UUID         NOT NULL UNIQUE REFERENCES paciente (id),
    nivel            VARCHAR(20)  NOT NULL
        CONSTRAINT ck_alerta_nivel CHECK (nivel IN ('CRITICO', 'URGENTE', 'URGENTE_2', 'NO_URGENTE', 'NO_URGENTE_2')),
    minutos_espera   INT          NOT NULL,
    estado           VARCHAR(20)  NOT NULL
        CONSTRAINT ck_alerta_estado CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    intentos         INT          NOT NULL DEFAULT 0,
    proximo_intento  TIMESTAMPTZ  NOT NULL,
    ultimo_error     VARCHAR(500),
    fecha_creacion   TIMESTAMPTZ  NOT NULL,
    fecha_envio      TIMESTAMPTZ
);

CREATE INDEX ix_alerta_pendientes ON alerta_medica (proximo_intento)
    WHERE estado = 'PENDIENTE';
