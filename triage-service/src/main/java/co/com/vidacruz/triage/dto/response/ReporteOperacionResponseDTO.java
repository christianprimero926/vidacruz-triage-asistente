package co.com.vidacruz.triage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Reporte de operación agrupado por hora")
public class ReporteOperacionResponseDTO {

    @Schema(description = "Inicio del rango consultado, inclusive", example = "2026-11-05T10:00:00Z")
    private Instant desde;
    @Schema(description = "Fin del rango consultado, exclusive", example = "2026-11-05T12:00:00Z")
    private Instant hasta;
    @Schema(description = "Una fila por cada hora con pacientes registrados")
    private List<FilaReporteDTO> filas;
}
