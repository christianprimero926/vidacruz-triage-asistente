package co.com.vidacruz.triage.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import co.com.vidacruz.triage.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EstadoValidator")
class EstadoValidatorTest {

    private final EstadoValidator validator = new EstadoValidator();

    @Test
    @DisplayName("convierte un estado conocido, ignorando espacios alrededor")
    void shouldReturnState_WhenValueIsKnown() {
        assertThat(validator.validar(new EstadoRequestDTO(" EN_CONSULTA "))).isEqualTo(EstadoPaciente.EN_CONSULTA);
    }

    @ParameterizedTest(name = "estado \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "DESCONOCIDO", "alta", "ESCALADO"})
    void shouldRejectEstado_WhenMissingOrUnknown(String valor) {
        ValidationException error = assertThrows(ValidationException.class,
                () -> validator.validar(new EstadoRequestDTO(valor)));

        assertThat(error.getErrores()).singleElement()
                .satisfies(detalle -> assertThat(detalle.getCampo()).isEqualTo("estado"));
    }

    @Test
    @DisplayName("el mensaje de un estado desconocido lista las opciones")
    void shouldListOptions_WhenStateIsUnknown() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> validator.validar(new EstadoRequestDTO("X")));

        assertThat(error.getErrores().get(0).getMensaje())
                .isEqualTo("El campo 'estado' debe ser uno de: REGISTRADO, EN_CONSULTA, ALTA");
    }
}
