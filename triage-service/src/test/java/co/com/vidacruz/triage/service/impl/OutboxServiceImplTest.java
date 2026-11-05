package co.com.vidacruz.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.OutboxEvento;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxServiceImpl (contrato del evento)")
class OutboxServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    @DisplayName("guarda un payload con el contrato acordado y sin datos personales")
    void shouldStoreContractPayload_WhenRegisteringEvent() throws Exception {
        when(outboxRepository.guardar(any(OutboxEvento.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        var service = new OutboxServiceImpl(outboxRepository, objectMapper);
        UUID pacienteId = UUID.randomUUID();
        Paciente paciente = Paciente.builder().id(pacienteId).nombreCompleto("Ana Ruiz").quejaPrincipal("Mareo")
                .temperatura(new BigDecimal("36.5")).nivel(NivelTriage.CRITICO).slaMinutos(5)
                .estado(EstadoPaciente.EN_CONSULTA).escalado(true).build();

        StepVerifier.create(service.registrar(paciente, TipoEvento.PACIENTE_EN_CONSULTA, AHORA))
                .verifyComplete();

        ArgumentCaptor<OutboxEvento> guardado = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(outboxRepository).guardar(guardado.capture());
        OutboxEvento evento = guardado.getValue();
        JsonNode json = objectMapper.readTree(evento.getPayload());

        assertThat(evento.getPacienteId()).isEqualTo(pacienteId);
        assertThat(evento.getTipoEvento()).isEqualTo("paciente_en_consulta");
        assertThat(evento.getFechaPublicacion()).isNull();
        assertThat(json.get("eventoId").asText()).isEqualTo(evento.getId().toString());
        assertThat(json.get("tipo").asText()).isEqualTo("paciente_en_consulta");
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("pacienteId").asText()).isEqualTo(pacienteId.toString());
        assertThat(json.get("nivel").asText()).isEqualTo("CRITICO");
        assertThat(json.get("estado").asText()).isEqualTo("EN_CONSULTA");
        assertThat(json.get("escalado").asBoolean()).isTrue();
        assertThat(json.get("slaMinutos").asInt()).isEqualTo(5);
        assertThat(json.get("ocurridoEn").asText()).isEqualTo("2026-11-05T15:00:00Z");
        assertThat(evento.getPayload()).doesNotContain("Ana Ruiz").doesNotContain("Mareo");
    }

    @Test
    @DisplayName("propaga el error si no puede guardar, para que se revierta la transacción")
    void shouldPropagateError_WhenRepositoryFails() {
        when(outboxRepository.guardar(any(OutboxEvento.class))).thenReturn(Mono.error(new IllegalStateException("db")));
        var service = new OutboxServiceImpl(outboxRepository, objectMapper);
        Paciente paciente = Paciente.builder().id(UUID.randomUUID()).nivel(NivelTriage.URGENTE).slaMinutos(10)
                .estado(EstadoPaciente.REGISTRADO).build();

        StepVerifier.create(service.registrar(paciente, TipoEvento.PACIENTE_REGISTRADO, AHORA))
                .expectError(IllegalStateException.class)
                .verify(Duration.ofSeconds(2));
    }
}
