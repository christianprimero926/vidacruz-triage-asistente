package co.com.vidacruz.asistente.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultaResponseDTO {

    @Schema(description = "Respuesta sustentada en el corpus, o el aviso de que no existe protocolo",
            example = "Reconfirmar la presión tras 5 minutos de reposo.")
    private String respuesta;

    @Schema(description = "Protocolos y secciones que sustentan la respuesta; vacío si no hay protocolo")
    private List<FuenteDTO> fuentes;
}
