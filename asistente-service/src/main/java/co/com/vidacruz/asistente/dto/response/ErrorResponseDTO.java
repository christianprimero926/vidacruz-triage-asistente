package co.com.vidacruz.asistente.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ErrorResponseDTO {

    @Schema(description = "Momento del error (ISO 8601, UTC)", example = "2026-11-05T15:00:00Z")
    private Instant timestamp;

    @Schema(description = "Código HTTP", example = "400")
    private int status;

    @Schema(description = "Código funcional del error", example = "VALIDACION_ERROR")
    private String codigo;

    @Schema(description = "Mensaje para el cliente", example = "Hay campos inválidos: pregunta")
    private String mensaje;

    @Schema(description = "Ruta de la solicitud", example = "/asistente/consulta")
    private String path;

    @Schema(description = "Identificador de correlación de la solicitud")
    private String correlationId;

    @Schema(description = "Detalle por campo; solo en errores de validación")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ErrorCampoDTO> errores;
}
