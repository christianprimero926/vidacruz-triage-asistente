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
@Table("alerta_medica")
public class AlertaMedica {

    @Id
    private UUID id;
    @Column("paciente_id")
    private UUID pacienteId;
    @Column("nivel")
    private NivelTriage nivel;
    @Column("minutos_espera")
    private Integer minutosEspera;
    @Column("estado")
    private EstadoAlerta estado;
    @Column("intentos")
    private Integer intentos;
    @Column("proximo_intento")
    private Instant proximoIntento;
    @Column("ultimo_error")
    private String ultimoError;
    @Column("fecha_creacion")
    private Instant fechaCreacion;
    @Column("fecha_envio")
    private Instant fechaEnvio;
}
