package co.com.vidacruz.triage.domain;

import java.math.BigDecimal;
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
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("paciente")
public class Paciente {

    @Id
    private UUID id;
    @Column("nombre_completo")
    private String nombreCompleto;
    @Column("edad")
    private Integer edad;
    @Column("frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;
    @Column("presion_sistolica")
    private Integer presionSistolica;
    @Column("presion_diastolica")
    private Integer presionDiastolica;
    @Column("spo2")
    private Integer spo2;
    @Column("temperatura")
    private BigDecimal temperatura;
    @Column("dolor")
    private Integer dolor;
    @Column("gcs")
    private Integer gcs;
    @Column("queja_principal")
    private String quejaPrincipal;
    @Column("nivel")
    private NivelTriage nivel;
    @Column("sla_minutos")
    private Integer slaMinutos;
    @Column("estado")
    private EstadoPaciente estado;
    @Column("escalado")
    private boolean escalado;
    @Column("fecha_registro")
    private Instant fechaRegistro;
    @Column("fecha_estado")
    private Instant fechaEstado;
    @Column("fecha_escalamiento")
    private Instant fechaEscalamiento;
    @Column("sla_vence_en")
    private Instant slaVenceEn;
}
