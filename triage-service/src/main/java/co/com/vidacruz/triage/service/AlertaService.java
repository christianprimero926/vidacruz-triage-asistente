package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.dto.response.AlertaResponseDTO;
import reactor.core.publisher.Flux;

public interface AlertaService {

    /** Las más recientes primero, hasta 200; el estado es un filtro opcional. */
    Flux<AlertaResponseDTO> listar(String estado);
}
