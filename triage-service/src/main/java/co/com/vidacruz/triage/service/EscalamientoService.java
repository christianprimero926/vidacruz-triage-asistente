package co.com.vidacruz.triage.service;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface EscalamientoService {

    /** Escala al paciente si sigue REGISTRADO y sin escalar (RN-03); emite true solo si esta llamada lo escaló. */
    Mono<Boolean> escalar(UUID pacienteId);
}
