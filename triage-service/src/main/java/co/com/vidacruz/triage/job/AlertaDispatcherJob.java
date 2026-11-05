package co.com.vidacruz.triage.job;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.AlertaReclamada;
import co.com.vidacruz.triage.exception.CanalAlertaException;
import co.com.vidacruz.triage.repository.AlertaDespachoRepository;
import co.com.vidacruz.triage.service.CanalAlerta;
import co.com.vidacruz.triage.service.MetricasTriage;
import co.com.vidacruz.triage.service.PoliticaReintento;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RF-06. Reclama alertas pendientes y las entrega por el canal configurado. Varios despachadores pueden
 * correr a la vez: el reclamo con SKIP LOCKED garantiza que cada alerta la tome uno solo.
 */
@Slf4j
@Component
public class AlertaDispatcherJob extends PeriodicJob {

    private static final int CONCURRENCIA = 4;
    private static final String RESULTADO_ENVIADA = "enviada";
    private static final String RESULTADO_REINTENTO = "reintento";
    private static final String RESULTADO_FALLIDA = "fallida";
    /** Margen sobre el timeout del canal antes de que otro despachador pueda retomar una alerta reclamada. */
    private static final Duration MARGEN_RECLAMO = Duration.ofSeconds(5);

    private final AlertaDespachoRepository repository;
    private final CanalAlerta canal;
    private final PoliticaReintento politica;
    private final Clock clock;
    private final MetricasTriage metricas;
    private final int lote;
    private final Duration reclamo;

    public AlertaDispatcherJob(AlertaDespachoRepository repository, CanalAlerta canal, PoliticaReintento politica,
                               TriageProperties properties, Clock clock, MetricasTriage metricas) {
        super("alertas-dispatcher", Duration.ofMillis(properties.getJobs().getAlertasIntervaloMs()),
                properties.getJobs().isEnabled());
        this.repository = repository;
        this.canal = canal;
        this.politica = politica;
        this.clock = clock;
        this.metricas = metricas;
        this.lote = properties.getJobs().getAlertasLote();
        this.reclamo = Duration.ofMillis(properties.getAlertas().getTimeoutMs()).plus(MARGEN_RECLAMO);
    }

    @Override
    public Mono<Void> ejecutar() {
        return Flux.defer(() -> repository.reclamar(clock.instant(), clock.instant().plus(reclamo), lote))
                .flatMap(this::despachar, CONCURRENCIA)
                .then();
    }

    private Mono<Void> despachar(AlertaReclamada alerta) {
        return canal.enviar(alerta.aNotificacion())
                .then(Mono.defer(() -> repository.marcarEnviada(alerta.id(), clock.instant())))
                .doOnSuccess(filas -> {
                    metricas.alerta(RESULTADO_ENVIADA);
                    log.info("Alerta id={} enviada en el intento {}", alerta.id(), alerta.intentos());
                })
                .onErrorResume(error -> registrarFallo(alerta, error))
                .then();
    }

    private Mono<Long> registrarFallo(AlertaReclamada alerta, Throwable error) {
        String motivo = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        boolean definitivo = politica.agotada(alerta.intentos())
                || (error instanceof CanalAlertaException canalError && !canalError.isReintentable());
        log.warn("Alerta id={} no se pudo enviar (intento {}): {}", alerta.id(), alerta.intentos(), motivo);
        if (definitivo) {
            return repository.marcarFallida(alerta.id(), motivo)
                    .doOnSuccess(filas -> metricas.alerta(RESULTADO_FALLIDA));
        }
        return repository.reprogramar(alerta.id(), clock.instant().plus(politica.espera(alerta.intentos())), motivo)
                .doOnSuccess(filas -> metricas.alerta(RESULTADO_REINTENTO));
    }
}
