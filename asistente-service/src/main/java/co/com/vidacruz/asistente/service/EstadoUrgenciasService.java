package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.dto.response.EstadoUrgenciasResponseDTO;
import reactor.core.publisher.Mono;

public interface EstadoUrgenciasService {

    Mono<EstadoUrgenciasResponseDTO> consultar();
}
