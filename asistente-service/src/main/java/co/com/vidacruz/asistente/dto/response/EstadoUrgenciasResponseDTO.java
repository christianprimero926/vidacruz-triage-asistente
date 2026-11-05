package co.com.vidacruz.asistente.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstadoUrgenciasResponseDTO {

    @Schema(description = "Pacientes activos (no dados de alta) por nivel de triaje",
            example = "{\"CRITICO\":1,\"URGENTE\":0,\"URGENTE_2\":2,\"NO_URGENTE\":0,\"NO_URGENTE_2\":0}")
    private Map<String, Long> niveles;

    @Schema(description = "Total de pacientes activos", example = "3")
    private long totalActivos;
}
