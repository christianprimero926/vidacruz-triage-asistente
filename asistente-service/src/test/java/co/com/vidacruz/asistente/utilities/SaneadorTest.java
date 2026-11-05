package co.com.vidacruz.asistente.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Saneador")
class SaneadorTest {

    @Test
    void shouldReplaceLineBreaksAndControlCharacters_WhenValueComesFromOutside() {
        assertThat(Saneador.limpiar("a\r\nb\tc\u0000d")).isEqualTo("a b c d");
    }

    @Test
    void shouldTruncateToOneHundredCharacters_WhenValueIsLong() {
        assertThat(Saneador.limpiar("x".repeat(250))).hasSize(100);
    }

    @Test
    void shouldReturnEmpty_WhenValueIsNull() {
        assertThat(Saneador.limpiar(null)).isEmpty();
    }

    @Test
    void shouldKeepAccentedText_WhenThereAreNoControlCharacters() {
        assertThat(Saneador.limpiar("Presión crítica")).isEqualTo("Presión crítica");
    }
}
