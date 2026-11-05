package co.com.vidacruz.triage.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/** Todas las pruebas de integración llaman con la API key; las de seguridad la quitan a propósito. */
@TestConfiguration(proxyBeanMethods = false)
public class TestWebClientConfig {

    public static final String API_KEY = "clave-de-pruebas";

    @Bean
    public WebTestClientBuilderCustomizer apiKeyPorDefecto() {
        return builder -> builder.defaultHeader("X-API-Key", API_KEY);
    }
}
