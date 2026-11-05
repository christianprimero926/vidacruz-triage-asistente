package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.domain.NivelTriage;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface EscalamientoService {

    /** Escala al paciente si sigue REGISTRADO y sin escalar (RN-03); emite el nivel del paciente solo si esta llamada lo escaló; vacío si no había nada que hacer. */
    Mono<NivelTriage> escalar(UUID pacienteId);
}
