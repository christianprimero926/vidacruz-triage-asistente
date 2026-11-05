package co.com.vidacruz.asistente.consumer;

import static co.com.vidacruz.asistente.utilities.Constants.METRICA_EVENTOS;
import static co.com.vidacruz.asistente.utilities.Constants.TAG_TIPO;

import co.com.vidacruz.asistente.domain.EventoUrgencia;
import co.com.vidacruz.asistente.exception.EventoInvalidoException;
import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import co.com.vidacruz.asistente.service.EventoParser;
import co.com.vidacruz.asistente.utilities.Saneador;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Interpreta un mensaje de triaje.eventos y lo aplica en Redis. Los fallos de conexión se reintentan; el error de
 * comando que Redis rechaza una y otra vez se reporta como evento inválido para que vaya al DLT y no frene la partición.
 */
@Component
@RequiredArgsConstructor
public class EventoProyector {

    private static final Retry REINTENTO_REDIS = Retry.backoff(3, Duration.ofMillis(200))
            .maxBackoff(Duration.ofSeconds(2))
            .filter(error -> !RedisErrorPermanente.esPermanente(error))
            .transientErrors(true);

    private final EventoParser parser;
    private final UrgenciasRepository repository;
    private final MeterRegistry meterRegistry;

    public Mono<Void> proyectar(String mensaje) {
        return Mono.fromCallable(() -> parser.parsear(mensaje)).flatMap(this::aplicar);
    }

    private Mono<Void> aplicar(EventoUrgencia evento) {
        Mono<Void> operacion = evento.tipo().isActiva()
                ? repository.agregar(evento.nivel(), evento.pacienteId())
                : repository.quitar(evento.nivel(), evento.pacienteId());
        return operacion.retryWhen(REINTENTO_REDIS)
                .onErrorMap(RedisErrorPermanente::esPermanente, error -> new EventoInvalidoException(
                        "Redis rechazó el comando: " + Saneador.limpiar(error.getMessage()), error))
                .doOnSuccess(vacio -> meterRegistry.counter(METRICA_EVENTOS, TAG_TIPO, evento.tipo().getNombre())
                        .increment());
    }
}
