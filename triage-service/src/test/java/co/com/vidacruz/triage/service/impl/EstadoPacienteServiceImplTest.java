package co.com.vidacruz.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import co.com.vidacruz.triage.exception.BusinessException;
import co.com.vidacruz.triage.exception.ErrorType;
import co.com.vidacruz.triage.exception.PacienteNoEncontradoException;
import co.com.vidacruz.triage.exception.TransicionInvalidaException;
import co.com.vidacruz.triage.exception.ValidationException;
import co.com.vidacruz.triage.mapper.PacienteMapperImpl;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.service.OutboxService;
import co.com.vidacruz.triage.validators.EstadoValidator;
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
@DisplayName("EstadoPacienteServiceImpl (RF-02)")
class EstadoPacienteServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:10:00Z");
    private static final Instant REGISTRO = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private OutboxService outboxService;

    private R2dbcEntityTemplate template;
    private EstadoPacienteServiceImpl service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        template = mock(R2dbcEntityTemplate.class, RETURNS_DEEP_STUBS);
        service = new EstadoPacienteServiceImpl(new EstadoValidator(), pacienteRepository, auditoriaService, outboxService,
                new PacienteMapperImpl(), template, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private Paciente paciente(EstadoPaciente estado) {
        return Paciente.builder().id(id).temperatura(new BigDecimal("36.5")).nivel(NivelTriage.URGENTE)
                .slaMinutos(10).estado(estado).fechaRegistro(REGISTRO).fechaEstado(REGISTRO).build();
    }

    private void filasActualizadas(long filas) {
        when(template.update(Paciente.class).matching(any(Query.class)).apply(any(Update.class)))
                .thenReturn(Mono.just(filas));
    }

    @Test
    @DisplayName("una transición válida actualiza, audita y devuelve el nuevo estado con tiempo en cero")
    void shouldUpdateAndAudit_WhenTransitionIsValid() {
        when(pacienteRepository.findById(id)).thenReturn(Mono.just(paciente(EstadoPaciente.REGISTRADO)),
                Mono.just(paciente(EstadoPaciente.EN_CONSULTA).toBuilder().fechaEstado(AHORA).build()));
        when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.empty());
        when(outboxService.registrar(any(Paciente.class), any(TipoEvento.class), any(Instant.class))).thenReturn(Mono.empty());
        filasActualizadas(1);

        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("EN_CONSULTA")))
                .assertNext(respuesta -> {
                    assertThat(respuesta.getEstado()).isEqualTo(EstadoPaciente.EN_CONSULTA);
                    assertThat(respuesta.getFechaEstadoActual()).isEqualTo(AHORA);
                    assertThat(respuesta.getTiempoEnEstadoActualSegundos()).isZero();
                })
                .verifyComplete();

        ArgumentCaptor<AuditoriaClinica> auditoria = ArgumentCaptor.forClass(AuditoriaClinica.class);
        ArgumentCaptor<Paciente> actualizado = ArgumentCaptor.forClass(Paciente.class);
        verify(auditoriaService).registrar(auditoria.capture());
        assertThat(auditoria.getValue().getTipoEvento()).isEqualTo("paciente_en_consulta");
        assertThat(auditoria.getValue().getEstadoAnterior()).isEqualTo("REGISTRADO");
        assertThat(auditoria.getValue().getEstadoNuevo()).isEqualTo("EN_CONSULTA");
        assertThat(auditoria.getValue().getFechaEvento()).isEqualTo(AHORA);
        verify(outboxService).registrar(actualizado.capture(), eq(TipoEvento.PACIENTE_EN_CONSULTA), eq(AHORA));
        assertThat(actualizado.getValue().getEstado()).isEqualTo(EstadoPaciente.EN_CONSULTA);
    }

    @Test
    @DisplayName("si escalar confirmó entre la lectura y el UPDATE, el evento y la respuesta salen con escalado=true")
    void shouldUseFreshPaciente_WhenEscalationCommittedBeforeUpdate() {
        Paciente leidoAntes = paciente(EstadoPaciente.REGISTRADO);
        Paciente releido = paciente(EstadoPaciente.EN_CONSULTA).toBuilder().escalado(true).fechaEstado(AHORA).build();
        when(pacienteRepository.findById(id)).thenReturn(Mono.just(leidoAntes), Mono.just(releido));
        when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.empty());
        when(outboxService.registrar(any(Paciente.class), any(TipoEvento.class), any(Instant.class))).thenReturn(Mono.empty());
        filasActualizadas(1);

        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("EN_CONSULTA")))
                .assertNext(respuesta -> assertThat(respuesta.isEscalado()).isTrue())
                .verifyComplete();

        ArgumentCaptor<Paciente> enEvento = ArgumentCaptor.forClass(Paciente.class);
        verify(outboxService).registrar(enEvento.capture(), eq(TipoEvento.PACIENTE_EN_CONSULTA), eq(AHORA));
        assertThat(enEvento.getValue().isEscalado()).isTrue();
    }

    @Test
    @DisplayName("una transición inválida falla con 409 y un mensaje descriptivo, sin auditar")
    void shouldFailWithConflict_WhenTransitionIsInvalid() {
        when(pacienteRepository.findById(id)).thenReturn(Mono.just(paciente(EstadoPaciente.ALTA)));

        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("EN_CONSULTA")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TransicionInvalidaException.class);
                    assertThat(error.getMessage()).startsWith("No se puede pasar de ALTA a EN_CONSULTA");
                    assertThat(((BusinessException) error).getType()).isEqualTo(ErrorType.CONFLICTO);
                })
                .verify(Duration.ofSeconds(2));

        verifyNoInteractions(auditoriaService, outboxService);
    }

    @Test
    @DisplayName("si otro proceso cambió el estado antes (0 filas) responde conflicto y no audita")
    void shouldFailWithConflict_WhenUpdateAffectsNoRows() {
        when(pacienteRepository.findById(id)).thenReturn(Mono.just(paciente(EstadoPaciente.REGISTRADO)));
        filasActualizadas(0);

        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("ALTA")))
                .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCodigo())
                        .isEqualTo("CONFLICTO_CONCURRENTE"))
                .verify();

        verifyNoInteractions(auditoriaService, outboxService);
    }

    @Test
    @DisplayName("un paciente inexistente falla con no encontrado")
    void shouldFailWithNotFound_WhenPacienteDoesNotExist() {
        when(pacienteRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("ALTA")))
                .expectError(PacienteNoEncontradoException.class)
                .verify();
    }

    @Test
    @DisplayName("un estado inexistente falla por validación sin consultar la base")
    void shouldFailValidation_WhenStateDoesNotExist() {
        StepVerifier.create(service.cambiarEstado(id, new EstadoRequestDTO("DESCONOCIDO")))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(pacienteRepository, auditoriaService);
    }
}
