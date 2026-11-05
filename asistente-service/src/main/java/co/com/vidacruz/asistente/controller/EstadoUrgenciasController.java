package co.com.vidacruz.asistente.controller;

import static co.com.vidacruz.asistente.utilities.Constants.RUTA_BASE;
import static co.com.vidacruz.asistente.utilities.Constants.RUTA_ESTADO_URGENCIAS;

import co.com.vidacruz.asistente.dto.response.EstadoUrgenciasResponseDTO;
import co.com.vidacruz.asistente.service.EstadoUrgenciasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(RUTA_BASE)
@RequiredArgsConstructor
@Tag(name = "Asistente clínico")
public class EstadoUrgenciasController {

    private final EstadoUrgenciasService estadoUrgenciasService;

    @GetMapping(RUTA_ESTADO_URGENCIAS)
    @Operation(summary = "Pacientes activos por nivel de triaje, reconstruidos desde triaje.eventos")
    public Mono<EstadoUrgenciasResponseDTO> estadoUrgencias() {
        return estadoUrgenciasService.consultar();
    }
}
