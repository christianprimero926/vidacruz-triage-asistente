package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.dto.response.ReporteOperacionResponseDTO;
import reactor.core.publisher.Mono;

public interface ReporteService {

    /** Los parámetros llegan como texto para poder informar cuál campo es inválido. */
    Mono<ReporteOperacionResponseDTO> operacion(String desde, String hasta);
}
