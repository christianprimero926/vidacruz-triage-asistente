package co.com.vidacruz.triage.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("API key obligatoria (fail-closed)")
class ApiKeyFailClosedTest {

    @Configuration
    @EnableConfigurationProperties(ApiKeyProperties.class)
    static class Configuracion {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Configuracion.class);

    @Test
    @DisplayName("sin la propiedad el contexto no arranca")
    void shouldFailToStart_WhenApiKeyIsMissing() {
        runner.run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    @DisplayName("con la propiedad vacía tampoco arranca, que es lo que pasa si falta TRIAGE_API_KEY")
    void shouldFailToStart_WhenApiKeyIsBlank() {
        runner.withPropertyValues("triage.security.api-key=").run(contexto -> assertThat(contexto).hasFailed());
        runner.withPropertyValues("triage.security.api-key=   ").run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    @DisplayName("con una clave arranca")
    void shouldStart_WhenApiKeyIsProvided() {
        runner.withPropertyValues("triage.security.api-key=algo").run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(ApiKeyProperties.class).getApiKey()).isEqualTo("algo");
        });
    }
}
