package co.com.vidacruz.triage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Nuevo estado clínico del paciente")
public class EstadoRequestDTO {

    @Schema(description = "Estado destino: REGISTRADO, EN_CONSULTA o ALTA", example = "EN_CONSULTA")
    private String estado;
}
