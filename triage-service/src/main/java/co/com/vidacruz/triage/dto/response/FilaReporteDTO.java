package co.com.vidacruz.triage.dto.response;

import co.com.vidacruz.triage.domain.NivelTriage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Resumen de una hora de operación")
public class FilaReporteDTO {

    @Schema(description = "Inicio de la hora, en UTC", example = "2026-11-05T10:00:00Z")
    private Instant hora;
    @Schema(description = "Pacientes registrados en la hora", example = "5")
    private long totalRegistrados;
    @Schema(description = "Registrados por nivel de triaje; siempre trae los cinco niveles")
    private Map<NivelTriage, Long> conteoPorNivel;
    @Schema(description = "Pacientes de la hora que ya entraron a consulta", example = "3")
    private long pacientesAtendidos;
    @Schema(description = "Espera promedio en minutos hasta la primera consulta; nulo si nadie fue atendido", example = "14.00")
    private BigDecimal esperaPromedioMinutos;
    @Schema(description = "Percentil 95 de la espera en minutos (interpolado); nulo si nadie fue atendido", example = "28.00")
    private BigDecimal esperaP95Minutos;
}
