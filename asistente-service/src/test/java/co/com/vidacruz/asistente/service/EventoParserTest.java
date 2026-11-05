package co.com.vidacruz.asistente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.vidacruz.asistente.domain.EventoUrgencia;
import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.domain.TipoEvento;
import co.com.vidacruz.asistente.exception.EventoInvalidoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EventoParser")
class EventoParserTest {

    private final EventoParser parser = new EventoParser(new ObjectMapper());

    private static String evento(String tipo, String nivel) {
        String campoNivel = nivel == null ? "" : ",\"nivel\":\"" + nivel + "\"";
        return "{\"eventoId\":\"e1\",\"tipo\":\"" + tipo + "\",\"version\":1,\"pacienteId\":\"p1\""
                + campoNivel + ",\"estado\":\"REGISTRADO\",\"escalado\":false,\"slaMinutos\":5,"
                + "\"ocurridoEn\":\"2026-11-05T15:00:00Z\",\"correlationId\":\"c1\",\"nuevoCampo\":true}";
    }

    @Test
    void shouldParseAllFourEventTypes_WhenContractIsRespected() {
        assertThat(parser.parsear(evento("paciente_registrado", "CRITICO")))
                .isEqualTo(new EventoUrgencia(TipoEvento.PACIENTE_REGISTRADO, "p1", NivelTriage.CRITICO));
        assertThat(parser.parsear(evento("paciente_en_consulta", "URGENTE_2")).tipo())
                .isEqualTo(TipoEvento.PACIENTE_EN_CONSULTA);
        assertThat(parser.parsear(evento("paciente_escalado", "NO_URGENTE")).nivel())
                .isEqualTo(NivelTriage.NO_URGENTE);
        assertThat(parser.parsear(evento("paciente_alta", "NO_URGENTE_2")).tipo().isActiva()).isFalse();
    }

    @Test
    void shouldIgnoreUnknownFields_WhenProducerAddsProperties() {
        assertThat(parser.parsear(evento("paciente_registrado", "URGENTE")).pacienteId()).isEqualTo("p1");
    }

    @Test
    void shouldReject_WhenLevelIsMissingEvenForAlta() {
        assertThatThrownBy(() -> parser.parsear(evento("paciente_alta", null)))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("Nivel");
    }

    @Test
    void shouldReject_WhenLevelIsUnknown() {
        assertThatThrownBy(() -> parser.parsear(evento("paciente_registrado", "GRAVISIMO")))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("GRAVISIMO");
    }

    @Test
    void shouldReject_WhenTypeIsUnknown() {
        assertThatThrownBy(() -> parser.parsear(evento("paciente_trasladado", "CRITICO")))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("paciente_trasladado");
    }

    @Test
    void shouldReject_WhenPatientIdIsMissing() {
        assertThatThrownBy(() -> parser.parsear("{\"tipo\":\"paciente_alta\",\"nivel\":\"CRITICO\"}"))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("pacienteId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no es json", "{\"tipo\":", "[]", "null", ""})
    void shouldReject_WhenPayloadIsUnreadable(String payload) {
        assertThatThrownBy(() -> parser.parsear(payload)).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void shouldReject_WhenPayloadIsNull() {
        assertThatThrownBy(() -> parser.parsear(null)).isInstanceOf(EventoInvalidoException.class);
    }
}
