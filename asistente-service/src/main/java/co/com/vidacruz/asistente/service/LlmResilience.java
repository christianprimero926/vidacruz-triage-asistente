package co.com.vidacruz.asistente.service;

import static co.com.vidacruz.asistente.utilities.Constants.CIRCUIT_BREAKER_LLM;
import static co.com.vidacruz.asistente.utilities.Constants.METRICA_LATENCIA_LLM;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_CIRCUITO_ABIERTO;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_ERROR;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_OK;
import static co.com.vidacruz.asistente.utilities.Constants.TAG_RESULTADO;

import co.com.vidacruz.asistente.config.LlmProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Todo lo que protege la llamada al proveedor: scheduler acotado (bulkhead), timeout, circuit breaker `llm`
 * (configurado por propiedades) y medición de latencia.
 */
@Component
public class LlmResilience {

    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final Scheduler llmScheduler;
    private final Duration timeout;

    public LlmResilience(CircuitBreakerRegistry registry, MeterRegistry meterRegistry,
                         @Qualifier("llmScheduler") Scheduler llmScheduler, LlmProperties properties) {
        this.circuitBreaker = registry.circuitBreaker(CIRCUIT_BREAKER_LLM);
        this.meterRegistry = meterRegistry;
        this.llmScheduler = llmScheduler;
        this.timeout = Duration.ofSeconds(properties.timeoutSegundos());
    }

    /** La llamada del cliente es bloqueante, por eso corre en el scheduler del LLM y no en el event loop. */
    public <T> Mono<T> ejecutar(Callable<T> llamada) {
        return proteger(Mono.fromCallable(llamada).subscribeOn(llmScheduler).timeout(timeout));
    }

    <T> Mono<T> proteger(Mono<T> llamada) {
        return Mono.defer(() -> {
            Timer.Sample muestra = Timer.start(meterRegistry);
            return llamada
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .doOnSuccess(valor -> detener(muestra, RESULTADO_OK))
                    .doOnError(error -> detener(muestra, resultadoDe(error)));
        });
    }

    private void detener(Timer.Sample muestra, String resultado) {
        muestra.stop(Timer.builder(METRICA_LATENCIA_LLM).tag(TAG_RESULTADO, resultado).register(meterRegistry));
    }

    private static String resultadoDe(Throwable error) {
        return error instanceof CallNotPermittedException ? RESULTADO_CIRCUITO_ABIERTO : RESULTADO_ERROR;
    }
}
