package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.domain.AlertaMedica;
import co.com.vidacruz.triage.domain.EstadoAlerta;
import co.com.vidacruz.triage.dto.response.AlertaResponseDTO;
import co.com.vidacruz.triage.mapper.AlertaMapper;
import co.com.vidacruz.triage.repository.AlertaMedicaRepository;
import co.com.vidacruz.triage.service.AlertaService;
import co.com.vidacruz.triage.validators.AlertaValidator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

    private final AlertaValidator validator;
    private final AlertaMedicaRepository repository;
    private final AlertaMapper mapper;

    @Override
    public Flux<AlertaResponseDTO> listar(String estado) {
        return Mono.fromSupplier(() -> validator.validarFiltro(estado))
                .flatMapMany(this::consultar)
                .map(mapper::toResponse);
    }

    private Flux<AlertaMedica> consultar(Optional<EstadoAlerta> filtro) {
        return filtro.map(repository::findTop200ByEstadoOrderByFechaCreacionDescIdAsc)
                .orElseGet(repository::findTop200ByOrderByFechaCreacionDescIdAsc);
    }
}
