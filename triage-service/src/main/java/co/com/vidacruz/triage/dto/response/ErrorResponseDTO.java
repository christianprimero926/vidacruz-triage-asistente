package co.com.vidacruz.triage.dto.response;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Formato común de error")
public class ErrorResponseDTO {

    @Schema(description = "Momento del error en ISO 8601", example = "2026-11-05T15:00:00Z")
    private Instant timestamp;
    @Schema(description = "Código HTTP", example = "400")
    private int status;
    @Schema(description = "Código estable del error", example = "VALIDACION_FALLIDA")
    private String codigo;
    @Schema(description = "Descripción legible del error", example = "Hay campos inválidos: spo2")
    private String mensaje;
    @Schema(description = "Ruta solicitada", example = "/triage/pacientes")
    private String path;
    @Schema(description = "Identificador de correlación de la solicitud", example = "9c1f4a52-7e0d-4b8a-a3c1-5d2e6f708192")
    private String correlationId;
    @Schema(description = "Errores por campo, solo en validaciones")
    private List<ErrorDetalleDTO> errores;
}
