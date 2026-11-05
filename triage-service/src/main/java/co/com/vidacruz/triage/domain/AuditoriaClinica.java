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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("auditoria_clinica")
public class AuditoriaClinica {

    @Id
    private Long id;
    @Column("paciente_id")
    private UUID pacienteId;
    @Column("tipo_evento")
    private String tipoEvento;
    @Column("estado_anterior")
    private String estadoAnterior;
    @Column("estado_nuevo")
    private String estadoNuevo;
    @Column("fecha_evento")
    private Instant fechaEvento;
    @Column("correlation_id")
    private String correlationId;
}
