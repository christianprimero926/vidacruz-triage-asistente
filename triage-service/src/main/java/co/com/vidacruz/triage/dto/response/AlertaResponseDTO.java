package co.com.vidacruz.triage.dto.response;

import co.com.vidacruz.triage.domain.EstadoAlerta;
import co.com.vidacruz.triage.domain.NivelTriage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Alerta al equipo médico por un paciente escalado")
public class AlertaResponseDTO {

    @Schema(description = "Identificador de la alerta", example = "5a1d3c9e-0f2b-4a77-8c61-9d3e7f2b1a40")
    private UUID id;
    @Schema(description = "Paciente escalado", example = "3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b")
    private UUID pacienteId;
    @Schema(description = "Nivel del paciente", example = "CRITICO")
    private NivelTriage nivel;
    @Schema(description = "Minutos que llevaba esperando al escalarse", example = "5")
    private Integer minutosEspera;
    @Schema(description = "PENDIENTE, ENVIADA o FALLIDA", example = "PENDIENTE")
    private EstadoAlerta estado;
    @Schema(description = "Intentos de envío realizados", example = "1")
    private Integer intentos;
    @Schema(description = "Cuándo se intentará el próximo envío", example = "2026-11-05T15:05:30Z")
    private Instant proximoIntento;
    @Schema(description = "Motivo del último fallo, si lo hubo", example = "HTTP 500")
    private String ultimoError;
    @Schema(description = "Cuándo se creó la alerta", example = "2026-11-05T15:05:00Z")
    private Instant fechaCreacion;
    @Schema(description = "Cuándo se entregó, si ya se envió", example = "2026-11-05T15:05:02Z")
    private Instant fechaEnvio;
}
