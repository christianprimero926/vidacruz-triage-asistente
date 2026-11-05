package co.com.vidacruz.triage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Error asociado a un campo de la solicitud")
public class ErrorDetalleDTO {

    @Schema(description = "Nombre del campo inválido", example = "frecuenciaCardiaca")
    private String campo;
    @Schema(description = "Motivo por el que el campo es inválido", example = "El campo 'frecuenciaCardiaca' debe estar entre 20 y 250")
    private String mensaje;
}
