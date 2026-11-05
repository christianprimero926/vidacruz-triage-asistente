package co.com.vidacruz.asistente.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NormalizadorTexto")
class NormalizadorTextoTest {

    @Test
    void shouldUnifyVariants_WhenWordsShareSixLetterRoot() {
        assertThat(NormalizadorTexto.raices("hipertensiva")).isEqualTo(NormalizadorTexto.raices("Hipertensión"));
    }

    @Test
    void shouldRemoveAccentsAndLowercase_WhenNormalizing() {
        assertThat(NormalizadorTexto.raices("Antibiótico NIÑO")).containsExactly("antibi", "nino");
    }

    @Test
    void shouldDropShortWordsNumbersAndStopwords_WhenNormalizing() {
        assertThat(NormalizadorTexto.raices("¿Cuál es el tratamiento 30 ml/kg para sepsis?"))
                .containsExactly("tratam", "sepsis");
    }

    @Test
    void shouldKeepFiveLetterWordsWhole_WhenShorterThanRoot() {
        assertThat(NormalizadorTexto.raices("qSOFA")).containsExactly("qsofa");
    }
}
