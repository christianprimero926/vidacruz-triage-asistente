package co.com.vidacruz.triage.job;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.EscalamientoService;
import co.com.vidacruz.triage.service.MetricasTriage;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** RF-03. Es seguro con varias réplicas: el UPDATE condicional de escalar() decide quién gana. */
@Slf4j
@Component
public class SlaMonitorJob extends PeriodicJob {

    private static final int CONCURRENCIA = 8;

    private final PacienteRepository pacienteRepository;
    private final EscalamientoService escalamientoService;
    private final Clock clock;
    private final MetricasTriage metricas;
    private final int lote;

    public SlaMonitorJob(PacienteRepository pacienteRepository, EscalamientoService escalamientoService,
                         TriageProperties properties, Clock clock, MetricasTriage metricas) {
        super("sla-monitor", Duration.ofMillis(properties.getJobs().getSlaIntervaloMs()),
                properties.getJobs().isEnabled());
        this.pacienteRepository = pacienteRepository;
        this.escalamientoService = escalamientoService;
        this.clock = clock;
        this.metricas = metricas;
        this.lote = properties.getJobs().getSlaLote();
    }

    @Override
    public Mono<Void> ejecutar() {
        return Flux.defer(() -> pacienteRepository.findIdsConSlaVencido(clock.instant(), lote))
                .flatMap(this::escalarSinDetenerElResto, CONCURRENCIA)
                .then();
    }

    private Mono<NivelTriage> escalarSinDetenerElResto(UUID pacienteId) {
        return escalamientoService.escalar(pacienteId)
                .doOnNext(metricas::escalamiento)
                .onErrorResume(error -> {
                    log.error("No se pudo escalar al paciente id={}", pacienteId, error);
                    return Mono.empty();
                });
    }
}
