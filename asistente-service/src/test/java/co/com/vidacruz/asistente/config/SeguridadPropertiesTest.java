package co.com.vidacruz.asistente.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("SeguridadProperties (fail-closed)")
class SeguridadPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SeguridadProperties.class)
    static class Habilitar {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Habilitar.class);

    @Test
    void shouldFailToStart_WhenApiKeyIsEmpty() {
        runner.withPropertyValues("asistente.seguridad.api-key=").run(contexto ->
                assertThat(contexto).hasFailed());
    }

    @Test
    void shouldFailToStart_WhenApiKeyIsMissing() {
        runner.run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    void shouldFailToStart_WhenApiKeyIsBlank() {
        runner.withPropertyValues("asistente.seguridad.api-key=   ").run(contexto ->
                assertThat(contexto).hasFailed());
    }

    @Test
    void shouldStart_WhenApiKeyIsProvided() {
        runner.withPropertyValues("asistente.seguridad.api-key=abc").run(contexto ->
                assertThat(contexto.getBean(SeguridadProperties.class).apiKey()).isEqualTo("abc"));
    }
}
