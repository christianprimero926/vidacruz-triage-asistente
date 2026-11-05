package co.com.vidacruz.triage.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.config.TriageProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PoliticaReintento")
class PoliticaReintentoTest {

    private PoliticaReintento politica(long baseMs, int maximo) {
        var properties = new TriageProperties();
        properties.getAlertas().setEsperaBaseMs(baseMs);
        properties.getAlertas().setMaxIntentos(maximo);
        return new PoliticaReintento(properties);
    }

    @ParameterizedTest(name = "tras el intento {0} espera {1} s")
    @CsvSource({"1,30", "2,60", "3,120", "4,240", "5,480"})
    void shouldDoubleTheWaitEachAttempt_WhenFailuresAccumulate(int intento, long segundos) {
        assertThat(politica(30_000, 5).espera(intento)).isEqualTo(Duration.ofSeconds(segundos));
    }

    @Test
    @DisplayName("la espera nunca supera una hora aunque los intentos crezcan")
    void shouldCapTheWait_WhenAttemptsAreHigh() {
        assertThat(politica(30_000, 100).espera(40)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("se agota al llegar al máximo de intentos")
    void shouldBeExhausted_WhenAttemptsReachTheMaximum() {
        var politica = politica(1000, 3);

        assertThat(politica.agotada(2)).isFalse();
        assertThat(politica.agotada(3)).isTrue();
    }
}
