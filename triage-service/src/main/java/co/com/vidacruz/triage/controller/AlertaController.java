package co.com.vidacruz.triage.controller;

import static co.com.vidacruz.triage.utilities.Constants.RUTA_ALERTAS;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_BASE_TRIAGE;

import co.com.vidacruz.triage.dto.response.AlertaResponseDTO;
import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping(RUTA_BASE_TRIAGE)
@RequiredArgsConstructor
@Tag(name = "Alertas", description = "Alertas al equipo médico por pacientes escalados")
public class AlertaController {

    private final AlertaService alertaService;

    @GetMapping(RUTA_ALERTAS)
    @Operation(summary = "Lista las alertas más recientes (máximo 200), opcionalmente por estado")
    @ApiResponse(responseCode = "400", description = "Estado desconocido",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Flux<AlertaResponseDTO> listar(
            @Parameter(description = "PENDIENTE, ENVIADA o FALLIDA", example = "FALLIDA")
            @RequestParam(required = false) String estado) {
        return alertaService.listar(estado);
    }
}
