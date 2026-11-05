package co.com.vidacruz.asistente.service;

import static co.com.vidacruz.asistente.utilities.Constants.CIRCUIT_BREAKER_LLM;
import static co.com.vidacruz.asistente.utilities.Constants.METRICA_LATENCIA_LLM;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_CIRCUITO_ABIERTO;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_ERROR;
import static co.com.vidacruz.asistente.utilities.Constants.RESULTADO_OK;
import static co.com.vidacruz.asistente.utilities.Constants.TAG_RESULTADO;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Circuit breaker `llm` (configurado por propiedades) y medición de latencia alrededor de cada llamada al proveedor. */
@Component
public class LlmResilience {

    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;

    public LlmResilience(CircuitBreakerRegistry registry, MeterRegistry meterRegistry) {
        this.circuitBreaker = registry.circuitBreaker(CIRCUIT_BREAKER_LLM);
        this.meterRegistry = meterRegistry;
    }

    public <T> Mono<T> proteger(Mono<T> llamada) {
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
