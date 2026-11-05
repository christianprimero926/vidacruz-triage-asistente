package co.com.vidacruz.triage.controller;

import static co.com.vidacruz.triage.utilities.Constants.RUTA_AUDITORIA;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_BASE_TRIAGE;

import co.com.vidacruz.triage.dto.response.AuditoriaResponseDTO;
import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Solo lectura: no existe ninguna operación de borrado o modificación de la auditoría (RN-04). */
@Slf4j
@RestController
@RequestMapping(RUTA_BASE_TRIAGE)
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Trazabilidad clínica de cada paciente")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping(RUTA_AUDITORIA)
    @Operation(summary = "Lista la auditoría del paciente en orden cronológico")
    @ApiResponse(responseCode = "404", description = "El paciente no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Flux<AuditoriaResponseDTO> listar(@PathVariable UUID id) {
        return auditoriaService.listarPorPaciente(id);
    }
}
