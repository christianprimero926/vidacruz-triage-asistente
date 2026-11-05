package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import co.com.vidacruz.triage.service.MetricasTriage;
import co.com.vidacruz.triage.service.PacienteService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Decorador: PacienteServiceImpl confirma la transacción antes de emitir su resultado, así que contar aquí
 * garantiza que solo se cuenta lo que quedó guardado.
 */
@Primary
@Service
@RequiredArgsConstructor
public class PacienteServiceConMetricas implements PacienteService {

    private final PacienteServiceImpl delegado;
    private final MetricasTriage metricas;

    @Override
    public Mono<PacienteRegistradoResponseDTO> registrar(PacienteRequestDTO request) {
        return delegado.registrar(request).doOnSuccess(registrado -> {
            if (registrado != null) {
                metricas.clasificacion(registrado.getNivel());
            }
        });
    }

    @Override
    public Mono<PacienteResponseDTO> obtener(UUID id) {
        return delegado.obtener(id);
    }
}
