package co.com.vidacruz.triage.dto.response;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
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
@Schema(description = "Resultado del registro y la clasificación de un paciente")
public class PacienteRegistradoResponseDTO {

    @Schema(description = "Identificador del paciente", example = "3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b")
    private UUID id;
    @Schema(description = "Nivel de triaje asignado según RN-01", example = "CRITICO")
    private NivelTriage nivel;
    @Schema(description = "Minutos máximos hasta entrar a consulta (RN-02)", example = "5")
    private Integer slaMinutos;
    @Schema(description = "Estado clínico actual", example = "REGISTRADO")
    private EstadoPaciente estado;
    @Schema(description = "Fecha y hora del registro en ISO 8601", example = "2026-11-05T15:00:00Z")
    private Instant fechaRegistro;
}
