package co.com.vidacruz.asistente.service.impl;

import static co.com.vidacruz.asistente.utilities.Constants.COD_ESTADO_NO_DISPONIBLE;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_ESTADO_NO_DISPONIBLE;

import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.dto.response.EstadoUrgenciasResponseDTO;
import co.com.vidacruz.asistente.exception.BusinessException;
import co.com.vidacruz.asistente.exception.ErrorType;
import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import co.com.vidacruz.asistente.service.EstadoUrgenciasService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstadoUrgenciasServiceImpl implements EstadoUrgenciasService {

    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(3);

    private final UrgenciasRepository repository;

    @Override
    public Mono<EstadoUrgenciasResponseDTO> consultar() {
        return repository.contarPorNivel()
                .timeout(TIEMPO_MAXIMO)
                .map(EstadoUrgenciasServiceImpl::aRespuesta)
                .onErrorMap(error -> {
                    log.warn("No se pudo leer el estado de urgencias: {}", error.getClass().getSimpleName());
                    return new BusinessException(ErrorType.SERVICIO_NO_DISPONIBLE, COD_ESTADO_NO_DISPONIBLE,
                            MSG_ESTADO_NO_DISPONIBLE, error);
                });
    }

    private static EstadoUrgenciasResponseDTO aRespuesta(Map<NivelTriage, Long> porNivel) {
        Map<String, Long> niveles = new LinkedHashMap<>();
        porNivel.forEach((nivel, total) -> niveles.put(nivel.name(), total));
        long total = porNivel.values().stream().mapToLong(Long::longValue).sum();
        return EstadoUrgenciasResponseDTO.builder().niveles(niveles).totalActivos(total).build();
    }
}
