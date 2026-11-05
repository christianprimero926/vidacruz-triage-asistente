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
public class ErrorCampoDTO {

    @Schema(description = "Campo inválido", example = "pregunta")
    private String campo;

    @Schema(description = "Motivo por el que el campo es inválido", example = "La pregunta es obligatoria")
    private String mensaje;
}
