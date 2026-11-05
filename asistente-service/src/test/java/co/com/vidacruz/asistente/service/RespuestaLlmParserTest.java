package co.com.vidacruz.asistente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RespuestaLlmParser")
class RespuestaLlmParserTest {

    private static final String JSON = "{\"respuesta\":\"Reconfirmar la presión.\","
            + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}],\"sinProtocolo\":false}";

    private final RespuestaLlmParser parser = new RespuestaLlmParser(new ObjectMapper());

    @Test
    void shouldParse_WhenResponseIsPlainJson() {
        RespuestaLlm resultado = parser.parsear(JSON);

        assertThat(resultado.respuesta()).isEqualTo("Reconfirmar la presión.");
        assertThat(resultado.fuentes()).containsExactly(new FuenteCitada("PROTO-001", "Medidas iniciales"));
        assertThat(resultado.sinProtocolo()).isFalse();
    }

    @Test
    void shouldParse_WhenJsonComesInsideCodeBlock() {
        assertThat(parser.parsear("```json\n" + JSON + "\n```").fuentes()).hasSize(1);
    }

    @Test
    void shouldParse_WhenJsonHasTextAroundIt() {
        assertThat(parser.parsear("Aquí está: " + JSON + " Espero que sirva.").respuesta())
                .isEqualTo("Reconfirmar la presión.");
    }

    @Test
    void shouldIgnoreUnknownFields_WhenModelAddsExtraProperties() {
        String conExtra = JSON.replace("\"sinProtocolo\":false", "\"sinProtocolo\":false,\"nota\":\"x\"");

        assertThat(parser.parsear(conExtra).sinProtocolo()).isFalse();
    }

    @Test
    void shouldFailAsLlmNoDisponible_WhenThereIsNoJson() {
        assertThatThrownBy(() -> parser.parsear("No puedo ayudar con eso"))
                .isInstanceOf(LlmNoDisponibleException.class);
        assertThatThrownBy(() -> parser.parsear(null)).isInstanceOf(LlmNoDisponibleException.class);
    }

    @Test
    void shouldFailAsLlmNoDisponible_WhenJsonIsMalformed() {
        assertThatThrownBy(() -> parser.parsear("{\"respuesta\": \"cortada\""))
                .isInstanceOf(LlmNoDisponibleException.class);
        assertThatThrownBy(() -> parser.parsear("{respuesta: sin comillas}"))
                .isInstanceOf(LlmNoDisponibleException.class);
    }
}
