package co.com.vidacruz.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.domain.AlertaMedica;
import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoAlerta;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.service.OutboxService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscalamientoServiceImpl (RF-03, RN-03)")
class EscalamientoServiceImplTest {

    private static final Instant REGISTRO = Instant.parse("2026-11-05T15:00:00Z");
    private static final Instant AHORA = REGISTRO.plus(Duration.ofMinutes(7)).plusSeconds(30);
    private static final Duration LIMITE = Duration.ofSeconds(2);

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private OutboxService outboxService;

    private R2dbcEntityTemplate template;
    private EscalamientoServiceImpl service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        template = mock(R2dbcEntityTemplate.class, RETURNS_DEEP_STUBS);
        service = new EscalamientoServiceImpl(template, pacienteRepository, auditoriaService, outboxService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void filasActualizadas(long filas) {
        when(template.update(Paciente.class).matching(any(Query.class)).apply(any(Update.class)))
                .thenReturn(Mono.just(filas));
    }

    @Test
    @DisplayName("si el UPDATE condicional no afecta filas no audita, no publica y no crea alerta")
    void shouldDoNothing_WhenPacienteIsNoLongerEscalable() {
        filasActualizadas(0);

        StepVerifier.create(service.escalar(id)).expectNext(false).verifyComplete();

        verifyNoInteractions(pacienteRepository, auditoriaService, outboxService);
    }

    @Test
    @DisplayName("al escalar audita REGISTRADO a ESCALADO, publica el evento y crea la alerta pendiente")
    void shouldAuditPublishAndCreateAlert_WhenPacienteIsEscalated() {
        Paciente paciente = Paciente.builder().id(id).temperatura(new BigDecimal("36.5")).nivel(NivelTriage.URGENTE)
                .slaMinutos(10).estado(EstadoPaciente.REGISTRADO).escalado(true)
                .fechaRegistro(REGISTRO).fechaEstado(REGISTRO).fechaEscalamiento(AHORA).build();
        filasActualizadas(1);
        when(pacienteRepository.findById(id)).thenReturn(Mono.just(paciente));
        when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.empty());
        when(outboxService.registrar(any(Paciente.class), any(TipoEvento.class), any(Instant.class)))
                .thenReturn(Mono.empty());
        when(template.insert(any(AlertaMedica.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.escalar(id)).expectNext(true).expectComplete().verify(LIMITE);

        ArgumentCaptor<AuditoriaClinica> auditoria = ArgumentCaptor.forClass(AuditoriaClinica.class);
        verify(auditoriaService).registrar(auditoria.capture());
        assertThat(auditoria.getValue().getTipoEvento()).isEqualTo("paciente_escalado");
        assertThat(auditoria.getValue().getEstadoAnterior()).isEqualTo("REGISTRADO");
        assertThat(auditoria.getValue().getEstadoNuevo()).isEqualTo("ESCALADO");
        assertThat(auditoria.getValue().getFechaEvento()).isEqualTo(AHORA);
        verify(outboxService).registrar(paciente, TipoEvento.PACIENTE_ESCALADO, AHORA);

        ArgumentCaptor<AlertaMedica> alerta = ArgumentCaptor.forClass(AlertaMedica.class);
        verify(template).insert(alerta.capture());
        assertThat(alerta.getValue().getPacienteId()).isEqualTo(id);
        assertThat(alerta.getValue().getNivel()).isEqualTo(NivelTriage.URGENTE);
        assertThat(alerta.getValue().getMinutosEspera()).isEqualTo(7);
        assertThat(alerta.getValue().getEstado()).isEqualTo(EstadoAlerta.PENDIENTE);
        assertThat(alerta.getValue().getIntentos()).isZero();
        assertThat(alerta.getValue().getProximoIntento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("si falla un efecto el error se propaga para revertir la transacción")
    void shouldPropagateError_WhenAuditoriaFails() {
        Paciente paciente = Paciente.builder().id(id).nivel(NivelTriage.URGENTE).estado(EstadoPaciente.REGISTRADO)
                .fechaRegistro(REGISTRO).build();
        filasActualizadas(1);
        when(pacienteRepository.findById(eq(id))).thenReturn(Mono.just(paciente));
        when(auditoriaService.registrar(any(AuditoriaClinica.class)))
                .thenReturn(Mono.error(new IllegalStateException("falla")));

        StepVerifier.create(service.escalar(id)).expectError(IllegalStateException.class).verify(LIMITE);

        verifyNoInteractions(outboxService);
    }
}
