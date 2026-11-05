package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.dto.response.ConsultaResponseDTO;
import reactor.core.publisher.Mono;

public interface ConsultaService {

    Mono<ConsultaResponseDTO> consultar(ConsultaRequestDTO request);
}
