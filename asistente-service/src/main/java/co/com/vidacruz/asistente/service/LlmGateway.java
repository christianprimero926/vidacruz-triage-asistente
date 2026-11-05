package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import java.util.List;
import reactor.core.publisher.Mono;

/** Único punto de contacto con el proveedor de IA; cualquier fallo sale como LlmNoDisponibleException. */
public interface LlmGateway {

    Mono<RespuestaLlm> consultar(String pregunta, List<SegmentoRecuperado> contexto);
}
