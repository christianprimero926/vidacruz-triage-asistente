package co.com.vidacruz.triage.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TextNormalizer")
class TextNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "Renovación de recetario|renovacion de recetario",
            "'  VACUNACIÓN  '|vacunacion",
            "recolección   de    resultados|recoleccion de resultados",
            "Ñandú|nandu"}, delimiter = '|')
    void shouldNormalize_WhenTextHasAccentsCaseOrExtraSpaces(String entrada, String esperado) {
        assertThat(TextNormalizer.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("un texto nulo se normaliza como vacío")
    void shouldReturnEmpty_WhenTextIsNull() {
        assertThat(TextNormalizer.normalizar(null)).isEmpty();
    }
}
