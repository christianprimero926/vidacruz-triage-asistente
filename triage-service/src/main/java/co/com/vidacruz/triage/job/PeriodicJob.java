package co.com.vidacruz.triage.job;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Template method de los procesos de fondo: las corridas nunca se solapan y, si una tarda más que el
 * periodo, los ticks sobrantes se descartan en lugar de acumularse.
 */
@Slf4j
public abstract class PeriodicJob implements SmartLifecycle {

    private final String nombre;
    private final Duration periodo;
    private final boolean habilitado;
    private volatile Disposable suscripcion;

    protected PeriodicJob(String nombre, Duration periodo, boolean habilitado) {
        this.nombre = nombre;
        this.periodo = periodo;
        this.habilitado = habilitado;
    }

    /** Una corrida del proceso; los tests la invocan directamente. */
    public abstract Mono<Void> ejecutar();

    @Override
    public void start() {
        if (!habilitado || isRunning()) {
            return;
        }
        suscripcion = Flux.interval(periodo)
                .onBackpressureDrop()
                .concatMap(tick -> Mono.defer(this::ejecutar).onErrorResume(this::registrarFallo), 1)
                .subscribe();
        log.info("Job {} iniciado cada {} ms", nombre, periodo.toMillis());
    }

    @Override
    public void stop() {
        Disposable actual = suscripcion;
        if (actual != null) {
            actual.dispose();
            suscripcion = null;
            log.info("Job {} detenido", nombre);
        }
    }

    @Override
    public boolean isRunning() {
        return suscripcion != null && !suscripcion.isDisposed();
    }

    private Mono<Void> registrarFallo(Throwable error) {
        log.error("Falló una corrida del job {}", nombre, error);
        return Mono.empty();
    }
}
