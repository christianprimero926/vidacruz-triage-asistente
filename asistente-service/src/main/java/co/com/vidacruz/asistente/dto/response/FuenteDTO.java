package co.com.vidacruz.asistente.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FuenteDTO {

    @Schema(description = "Identificador del protocolo citado", example = "PROTO-001")
    private String protocolo;

    @Schema(description = "Sección del protocolo citada", example = "Medidas iniciales")
    private String seccion;
}
