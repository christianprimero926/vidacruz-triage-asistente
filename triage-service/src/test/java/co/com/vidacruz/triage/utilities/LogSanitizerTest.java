package co.com.vidacruz.triage.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LogSanitizer")
class LogSanitizerTest {

    @Test
    @DisplayName("reemplaza saltos de línea y controles para que no se pueda falsificar una línea de log")
    void shouldReplaceControlCharacters_WhenValueHasLineBreaks() {
        assertThat(LogSanitizer.sanear("/ruta\r\nERROR falso\t\u0000fin")).isEqualTo("/ruta  ERROR falso  fin");
    }

    @Test
    @DisplayName("recorta a 100 caracteres")
    void shouldTruncateToOneHundred_WhenValueIsLong() {
        assertThat(LogSanitizer.sanear("a".repeat(500))).hasSize(100);
        assertThat(LogSanitizer.sanear("b".repeat(100))).hasSize(100);
    }

    @Test
    @DisplayName("conserva el texto normal y trata nulo como vacío")
    void shouldKeepPlainText_WhenThereIsNothingToClean() {
        assertThat(LogSanitizer.sanear("/triage/pacientes/ñandú")).isEqualTo("/triage/pacientes/ñandú");
        assertThat(LogSanitizer.sanear(null)).isEmpty();
    }
}
