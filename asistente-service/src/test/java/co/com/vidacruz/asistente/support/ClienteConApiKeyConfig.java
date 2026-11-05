package co.com.vidacruz.asistente.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Los IT llaman con la API key de prueba; los que prueban el rechazo construyen su propio cliente sin ella. */
@Configuration
public class ClienteConApiKeyConfig {

    @Bean
    WebTestClientBuilderCustomizer apiKeyPorDefecto(@Value("${asistente.seguridad.api-key}") String apiKey) {
        return builder -> builder.defaultHeader("X-API-Key", apiKey);
    }
}
