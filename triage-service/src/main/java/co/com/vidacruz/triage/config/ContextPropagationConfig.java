package co.com.vidacruz.triage.config;

import static co.com.vidacruz.triage.utilities.Constants.CORRELATION_ID_KEY;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Con spring.reactor.context-propagation=auto, la correlación que viaja en el contexto de Reactor se copia al
 * MDC del hilo que ejecuta cada paso, y así aparece en los logs sin pasarla a mano.
 */
@Configuration
public class ContextPropagationConfig {

    @PostConstruct
    void registrarCorrelacionEnMdc() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new CorrelacionMdcAccessor());
    }

    static class CorrelacionMdcAccessor implements ThreadLocalAccessor<String> {

        @Override
        public Object key() {
            return CORRELATION_ID_KEY;
        }

        @Override
        public String getValue() {
            return MDC.get(CORRELATION_ID_KEY);
        }

        @Override
        public void setValue(String valor) {
            MDC.put(CORRELATION_ID_KEY, valor);
        }

        @Override
        public void setValue() {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
