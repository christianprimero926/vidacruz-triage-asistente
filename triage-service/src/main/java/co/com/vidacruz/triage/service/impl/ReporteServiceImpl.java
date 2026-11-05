package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.dto.request.RangoReporteDTO;
import co.com.vidacruz.triage.dto.response.ReporteOperacionResponseDTO;
import co.com.vidacruz.triage.repository.ReporteRepository;
import co.com.vidacruz.triage.service.ReporteService;
import co.com.vidacruz.triage.validators.ReporteValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    private final ReporteValidator validator;
    private final ReporteRepository reporteRepository;

    @Override
    public Mono<ReporteOperacionResponseDTO> operacion(String desde, String hasta) {
        return Mono.fromSupplier(() -> validator.validar(desde, hasta))
                .flatMap(this::consultar);
    }

    private Mono<ReporteOperacionResponseDTO> consultar(RangoReporteDTO rango) {
        return reporteRepository.operacionPorHora(rango.desde(), rango.hasta())
                .collectList()
                .map(filas -> ReporteOperacionResponseDTO.builder()
                        .desde(rango.desde())
                        .hasta(rango.hasta())
                        .filas(filas)
                        .build());
    }
}
