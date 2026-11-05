package co.com.vidacruz.triage.dto.response;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos completos del paciente con su estado y el tiempo en él")
public class PacienteResponseDTO {

    @Schema(description = "Identificador del paciente", example = "3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b")
    private UUID id;
    @Schema(description = "Nombre completo", example = "María Fernanda Gómez")
    private String nombreCompleto;
    @Schema(description = "Edad en años", example = "45")
    private Integer edad;
    @Schema(description = "Frecuencia cardíaca en bpm", example = "130")
    private Integer frecuenciaCardiaca;
    @Schema(description = "Presión sistólica en mmHg", example = "180")
    private Integer presionSistolica;
    @Schema(description = "Presión diastólica en mmHg", example = "110")
    private Integer presionDiastolica;
    @Schema(description = "Saturación de oxígeno en %", example = "97")
    private Integer spo2;
    @Schema(description = "Temperatura en grados Celsius", example = "36.8")
    private BigDecimal temperatura;
    @Schema(description = "Escala de dolor", example = "8")
    private Integer dolor;
    @Schema(description = "Escala de Glasgow", example = "15")
    private Integer gcs;
    @Schema(description = "Queja principal", example = "Dolor torácico")
    private String quejaPrincipal;
    @Schema(description = "Nivel de triaje", example = "CRITICO")
    private NivelTriage nivel;
    @Schema(description = "Minutos máximos hasta entrar a consulta", example = "5")
    private Integer slaMinutos;
    @Schema(description = "Estado clínico actual", example = "REGISTRADO")
    private EstadoPaciente estado;
    @Schema(description = "Indica si el paciente fue escalado por superar su SLA", example = "false")
    private boolean escalado;
    @Schema(description = "Fecha y hora del registro", example = "2026-11-05T15:00:00Z")
    private Instant fechaRegistro;
    @Schema(description = "Momento en que el paciente entró al estado actual", example = "2026-11-05T15:00:00Z")
    private Instant fechaEstadoActual;
    @Schema(description = "Momento del escalamiento, nulo si no fue escalado", example = "2026-11-05T15:05:00Z")
    private Instant fechaEscalamiento;
    @Schema(description = "Momento en que vence el SLA", example = "2026-11-05T15:05:00Z")
    private Instant slaVenceEn;
    @Schema(description = "Minutos completos en el estado actual", example = "7")
    private long tiempoEnEstadoActualMinutos;
    @Schema(description = "Segundos totales en el estado actual", example = "450")
    private long tiempoEnEstadoActualSegundos;
}
