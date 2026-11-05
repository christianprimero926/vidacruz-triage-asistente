package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RagRetriever {

    /** Segmentos más relevantes para la pregunta, ordenados por score y por encima del umbral. */
    Mono<List<SegmentoRecuperado>> recuperar(String pregunta);
}
