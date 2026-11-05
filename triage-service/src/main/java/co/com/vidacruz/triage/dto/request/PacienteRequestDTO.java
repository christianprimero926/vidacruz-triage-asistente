package co.com.vidacruz.triage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos del paciente y sus signos vitales al llegar a recepción")
public class PacienteRequestDTO {

    @Schema(description = "Nombre completo (3 a 100 caracteres)", example = "María Fernanda Gómez")
    private String nombreCompleto;
    @Schema(description = "Edad en años (0 a 120)", example = "45")
    private Integer edad;
    @Schema(description = "Frecuencia cardíaca en bpm (20 a 250)", example = "130")
    private Integer frecuenciaCardiaca;
    @Schema(description = "Presión sistólica en mmHg (60 a 260)", example = "180")
    private Integer presionSistolica;
    @Schema(description = "Presión diastólica en mmHg (30 a 160, menor que la sistólica)", example = "110")
    private Integer presionDiastolica;
    @Schema(description = "Saturación de oxígeno en % (70 a 100)", example = "97")
    private Integer spo2;
    @Schema(description = "Temperatura en grados Celsius (34.0 a 42.0, máximo 2 decimales)", example = "36.8")
    private BigDecimal temperatura;
    @Schema(description = "Escala de dolor (0 a 10)", example = "8")
    private Integer dolor;
    @Schema(description = "Escala de Glasgow (3 a 15)", example = "15")
    private Integer gcs;
    @Schema(description = "Queja principal (obligatoria, máximo 500 caracteres)", example = "Dolor torácico")
    private String quejaPrincipal;
}
