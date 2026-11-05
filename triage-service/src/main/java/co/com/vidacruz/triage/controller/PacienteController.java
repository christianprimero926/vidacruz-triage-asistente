package co.com.vidacruz.triage.controller;

import static co.com.vidacruz.triage.utilities.Constants.RUTA_BASE_TRIAGE;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_ESTADO;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_PACIENTES;
import static co.com.vidacruz.triage.utilities.Constants.RUTA_PACIENTE_POR_ID;

import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import co.com.vidacruz.triage.service.EstadoPacienteService;
import co.com.vidacruz.triage.service.PacienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(RUTA_BASE_TRIAGE)
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Registro, clasificación y consulta de pacientes")
public class PacienteController {

    private final PacienteService pacienteService;
    private final EstadoPacienteService estadoPacienteService;

    @PostMapping(RUTA_PACIENTES)
    @Operation(summary = "Registra y clasifica un paciente según sus signos vitales")
    @ApiResponse(responseCode = "201", description = "Paciente registrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Mono<ResponseEntity<PacienteRegistradoResponseDTO>> registrar(@RequestBody PacienteRequestDTO request) {
        return pacienteService.registrar(request)
                .map(registrado -> ResponseEntity
                        .created(URI.create(RUTA_BASE_TRIAGE + RUTA_PACIENTES + "/" + registrado.getId()))
                        .body(registrado));
    }

    @GetMapping(RUTA_PACIENTE_POR_ID)
    @Operation(summary = "Obtiene los datos completos de un paciente y el tiempo en su estado actual")
    @ApiResponse(responseCode = "404", description = "El paciente no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Mono<PacienteResponseDTO> obtener(@PathVariable UUID id) {
        return pacienteService.obtener(id);
    }

    @PatchMapping(RUTA_ESTADO)
    @Operation(summary = "Cambia el estado clínico del paciente respetando las transiciones válidas")
    @ApiResponse(responseCode = "400", description = "Estado vacío, inexistente o id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "El paciente no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Transición inválida o cambio concurrente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    public Mono<PacienteResponseDTO> cambiarEstado(@PathVariable UUID id, @RequestBody EstadoRequestDTO request) {
        return estadoPacienteService.cambiarEstado(id, request);
    }
}
