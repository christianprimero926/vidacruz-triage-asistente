package co.com.vidacruz.triage.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contrato del topic triaje.eventos. No lleva datos personales: ni nombre ni queja. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Evento de ciclo de vida de un paciente publicado en triaje.eventos")
public class EventoPacienteDTO {

    public static final int VERSION_ACTUAL = 1;

    @Schema(description = "Identificador único del evento, sirve para deduplicar", example = "0b7c1f6e-2f1d-4c55-8d0a-6f3f2b9d7a10")
    private UUID eventoId;
    @Schema(description = "paciente_registrado, paciente_en_consulta, paciente_alta o paciente_escalado", example = "paciente_registrado")
    private String tipo;
    @Schema(description = "Versión del contrato", example = "1")
    private int version;
    @Schema(description = "Paciente del evento; también es la clave del mensaje", example = "3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b")
    private UUID pacienteId;
    @Schema(description = "Nivel de triaje del paciente", example = "CRITICO")
    private String nivel;
    @Schema(description = "Estado clínico tras el evento", example = "REGISTRADO")
    private String estado;
    @Schema(description = "Si el paciente ya fue escalado", example = "false")
    private boolean escalado;
    @Schema(description = "SLA del nivel en minutos", example = "5")
    private int slaMinutos;
    @Schema(description = "Momento en que ocurrió el evento", example = "2026-11-05T15:00:00Z")
    private Instant ocurridoEn;
    @Schema(description = "Correlación de la solicitud que lo originó", example = "9c1f4a52-7e0d-4b8a-a3c1-5d2e6f708192")
    private String correlationId;
}
