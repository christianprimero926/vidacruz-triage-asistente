package co.com.vidacruz.triage.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** La columna secuencia la asigna la base y solo se usa para ordenar la publicación. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("outbox_evento")
public class OutboxEvento {

    @Id
    private UUID id;
    @Column("paciente_id")
    private UUID pacienteId;
    @Column("tipo_evento")
    private String tipoEvento;
    @Column("payload")
    private String payload;
    @Column("correlation_id")
    private String correlationId;
    @Column("traceparent")
    private String traceparent;
    @Column("fecha_creacion")
    private Instant fechaCreacion;
    @Column("fecha_publicacion")
    private Instant fechaPublicacion;
}
