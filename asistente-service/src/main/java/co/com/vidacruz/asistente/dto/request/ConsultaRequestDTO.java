package co.com.vidacruz.asistente.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultaRequestDTO {

    @Schema(description = "Pregunta del médico en lenguaje natural (máximo 500 caracteres)",
            example = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
    private String pregunta;
}
