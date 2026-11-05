package co.com.vidacruz.triage.controller;

import static co.com.vidacruz.triage.utilities.Constants.RUTA_BASE_TRIAGE;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_REPORTE_OPERACION;

import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.dto.response.ReporteOperacionResponseDTO;
import co.com.vidacruz.triage.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(RUTA_BASE_TRIAGE)
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Indicadores de operación de la sala de urgencias")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping(RUTA_REPORTE_OPERACION)
    @Operation(summary = "Pacientes por hora, conteo por nivel y espera promedio y p95 hasta la primera consulta")
    @ApiResponse(responseCode = "400", description = "Fechas ausentes, mal formadas, desordenadas o rango demasiado grande",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Mono<ReporteOperacionResponseDTO> operacion(
            @Parameter(description = "Inicio inclusive, ISO 8601", example = "2026-11-05T10:00:00Z")
            @RequestParam(required = false) String desde,
            @Parameter(description = "Fin exclusivo, ISO 8601", example = "2026-11-05T12:00:00Z")
            @RequestParam(required = false) String hasta) {
        return reporteService.operacion(desde, hasta);
    }
}
