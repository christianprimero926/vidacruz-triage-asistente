package co.com.vidacruz.asistente.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConsultaValidator")
class ConsultaValidatorTest {

    private final ConsultaValidator validator = new ConsultaValidator();

    private static ConsultaRequestDTO con(String pregunta) {
        return ConsultaRequestDTO.builder().pregunta(pregunta).build();
    }

    @Test
    void shouldAccept_WhenQuestionHasExactlyFiveHundredCharacters() {
        assertThatCode(() -> validator.validar(con("a".repeat(500)))).doesNotThrowAnyException();
    }

    @Test
    void shouldReject_WhenQuestionIsNullOrBlank() {
        assertThatThrownBy(() -> validator.validar(con(null))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validar(con("   "))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validar(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldReportQuestionField_WhenQuestionIsTooLong() {
        assertThatThrownBy(() -> validator.validar(con("a".repeat(501))))
                .isInstanceOfSatisfying(ValidationException.class, ex -> {
                    assertThat(ex.getErrores()).hasSize(1);
                    assertThat(ex.getErrores().get(0).getCampo()).isEqualTo("pregunta");
                    assertThat(ex.getMessage()).contains("pregunta");
                });
    }
}
