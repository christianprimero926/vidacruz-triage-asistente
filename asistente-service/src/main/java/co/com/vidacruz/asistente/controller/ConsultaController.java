package co.com.vidacruz.asistente.controller;

import static co.com.vidacruz.asistente.utilities.Constants.RUTA_BASE;
import static co.com.vidacruz.asistente.utilities.Constants.RUTA_CONSULTA;

import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.dto.response.ConsultaResponseDTO;
import co.com.vidacruz.asistente.service.ConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(RUTA_BASE)
@RequiredArgsConstructor
@Tag(name = "Asistente clínico")
public class ConsultaController {

    private final ConsultaService consultaService;

    @PostMapping(RUTA_CONSULTA)
    @Operation(summary = "Consulta los protocolos clínicos en lenguaje natural, con cita de fuentes")
    public Mono<ConsultaResponseDTO> consultar(@RequestBody ConsultaRequestDTO request) {
        return consultaService.consultar(request);
    }
}
