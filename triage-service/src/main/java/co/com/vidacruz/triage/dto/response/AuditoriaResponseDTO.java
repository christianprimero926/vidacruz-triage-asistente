package co.com.vidacruz.triage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Entrada de la auditoría clínica de un paciente")
public class AuditoriaResponseDTO {

    @Schema(description = "Identificador de la entrada", example = "1")
    private Long id;
    @Schema(description = "Paciente al que pertenece", example = "3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b")
    private UUID pacienteId;
    @Schema(description = "Tipo de evento", example = "paciente_en_consulta")
    private String tipoEvento;
    @Schema(description = "Estado anterior, nulo en el registro", example = "REGISTRADO")
    private String estadoAnterior;
    @Schema(description = "Estado nuevo", example = "EN_CONSULTA")
    private String estadoNuevo;
    @Schema(description = "Momento del evento", example = "2026-11-05T15:03:00Z")
    private Instant fechaEvento;
    @Schema(description = "Correlación de la solicitud que lo originó", example = "9c1f4a52-7e0d-4b8a-a3c1-5d2e6f708192")
    private String correlationId;
}
