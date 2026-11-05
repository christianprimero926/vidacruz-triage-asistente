package co.com.vidacruz.triage.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.context.ContextRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@DisplayName("Propagación de la correlación al MDC")
class ContextPropagationConfigTest {

    @Test
    @DisplayName("la correlación del contexto de Reactor llega al MDC en otro hilo y no queda pegada después")
    void shouldExposeCorrelationInMdc_WhenStepRunsOnAnotherThread() {
        Hooks.enableAutomaticContextPropagation();
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ContextPropagationConfig.CorrelacionMdcAccessor());
        AtomicReference<String> visto = new AtomicReference<>();

        Mono.just(1)
                .publishOn(Schedulers.parallel())
                .doOnNext(valor -> visto.set(MDC.get("correlationId")))
                .contextWrite(contexto -> contexto.put("correlationId", "abc12345"))
                .block();

        assertThat(visto.get()).isEqualTo("abc12345");
        assertThat(MDC.get("correlationId")).isNull();
    }
}
