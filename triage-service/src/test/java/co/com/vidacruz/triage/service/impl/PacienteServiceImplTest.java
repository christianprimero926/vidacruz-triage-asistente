package co.com.vidacruz.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.exception.PacienteNoEncontradoException;
import co.com.vidacruz.triage.exception.ValidationException;
import co.com.vidacruz.triage.mapper.PacienteMapperImpl;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.TriageClassifier;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.validators.PacienteValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteServiceImpl")
class PacienteServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private R2dbcEntityTemplate template;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private PacienteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PacienteServiceImpl(new PacienteValidator(), new TriageClassifier(), new TriageProperties(),
                new PacienteMapperImpl(), template, pacienteRepository, auditoriaService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("clasifica, calcula el SLA y guarda paciente y auditoría")
        void shouldPersistPacienteAndAuditoria_WhenRequestIsValid() {
            when(template.insert(any(Paciente.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.empty());
            var request = PacienteFixtures.base().nombreCompleto("  Ana Ruiz  ").quejaPrincipal(" Mareo ")
                    .frecuenciaCardiaca(130).build();

            StepVerifier.create(service.registrar(request))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getId()).isNotNull();
                        assertThat(respuesta.getNivel()).isEqualTo(NivelTriage.CRITICO);
                        assertThat(respuesta.getSlaMinutos()).isEqualTo(5);
                        assertThat(respuesta.getEstado()).isEqualTo(EstadoPaciente.REGISTRADO);
                        assertThat(respuesta.getFechaRegistro()).isEqualTo(AHORA);
                    })
                    .verifyComplete();

            ArgumentCaptor<Paciente> paciente = ArgumentCaptor.forClass(Paciente.class);
            verify(template).insert(paciente.capture());
            assertThat(paciente.getValue().getNombreCompleto()).isEqualTo("Ana Ruiz");
            assertThat(paciente.getValue().getQuejaPrincipal()).isEqualTo("Mareo");
            assertThat(paciente.getValue().isEscalado()).isFalse();
            assertThat(paciente.getValue().getFechaEstado()).isEqualTo(AHORA);
            assertThat(paciente.getValue().getSlaVenceEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));

            ArgumentCaptor<AuditoriaClinica> auditoria = ArgumentCaptor.forClass(AuditoriaClinica.class);
            verify(auditoriaService).registrar(auditoria.capture());
            assertThat(auditoria.getValue().getPacienteId()).isEqualTo(paciente.getValue().getId());
            assertThat(auditoria.getValue().getTipoEvento()).isEqualTo("paciente_registrado");
            assertThat(auditoria.getValue().getEstadoAnterior()).isNull();
            assertThat(auditoria.getValue().getEstadoNuevo()).isEqualTo("REGISTRADO");
            assertThat(auditoria.getValue().getFechaEvento()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("usa el SLA configurado para el nivel")
        void shouldUseConfiguredSla_WhenPropertiesChange() {
            var properties = new TriageProperties();
            properties.getSla().setNoUrgente2Minutos(3);
            service = new PacienteServiceImpl(new PacienteValidator(), new TriageClassifier(), properties,
                    new PacienteMapperImpl(), template, pacienteRepository, auditoriaService,
                    Clock.fixed(AHORA, ZoneOffset.UTC));
            when(template.insert(any(Paciente.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.empty());

            StepVerifier.create(service.registrar(PacienteFixtures.e5()))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getNivel()).isEqualTo(NivelTriage.NO_URGENTE_2);
                        assertThat(respuesta.getSlaMinutos()).isEqualTo(3);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("no toca la base cuando la solicitud es inválida")
        void shouldFailWithoutPersisting_WhenRequestIsInvalid() {
            var request = PacienteFixtures.base().spo2(50).build();

            StepVerifier.create(service.registrar(request))
                    .expectError(ValidationException.class)
                    .verify();

            verifyNoInteractions(template, auditoriaService);
        }

        @Test
        @DisplayName("propaga el error si la auditoría falla, para que se revierta la transacción")
        void shouldPropagateError_WhenAuditoriaFails() {
            when(template.insert(any(Paciente.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(auditoriaService.registrar(any(AuditoriaClinica.class))).thenReturn(Mono.error(new IllegalStateException("fallo")));

            StepVerifier.create(service.registrar(PacienteFixtures.e4()))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        @DisplayName("devuelve el paciente con el tiempo en el estado actual")
        void shouldReturnTimeInCurrentState_WhenPacienteExists() {
            UUID id = UUID.randomUUID();
            Paciente paciente = pacienteDesde(id, AHORA.minusSeconds(450));
            when(pacienteRepository.findById(id)).thenReturn(Mono.just(paciente));

            StepVerifier.create(service.obtener(id))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getId()).isEqualTo(id);
                        assertThat(respuesta.getFechaEstadoActual()).isEqualTo(AHORA.minusSeconds(450));
                        assertThat(respuesta.getTiempoEnEstadoActualMinutos()).isEqualTo(7);
                        assertThat(respuesta.getTiempoEnEstadoActualSegundos()).isEqualTo(450);
                        assertThat(respuesta.isEscalado()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("nunca informa un tiempo negativo")
        void shouldReturnZero_WhenStateDateIsInTheFuture() {
            UUID id = UUID.randomUUID();
            when(pacienteRepository.findById(id)).thenReturn(Mono.just(pacienteDesde(id, AHORA.plusSeconds(30))));

            StepVerifier.create(service.obtener(id))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getTiempoEnEstadoActualMinutos()).isZero();
                        assertThat(respuesta.getTiempoEnEstadoActualSegundos()).isZero();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("falla con PacienteNoEncontradoException cuando no existe")
        void shouldFailWithNotFound_WhenPacienteDoesNotExist() {
            UUID id = UUID.randomUUID();
            when(pacienteRepository.findById(id)).thenReturn(Mono.empty());

            StepVerifier.create(service.obtener(id))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(PacienteNoEncontradoException.class);
                        assertThat(error.getMessage()).contains(id.toString());
                    })
                    .verify();
        }

        private Paciente pacienteDesde(UUID id, Instant fechaEstado) {
            return Paciente.builder()
                    .id(id)
                    .nombreCompleto("Ana Ruiz")
                    .temperatura(new BigDecimal("36.5"))
                    .nivel(NivelTriage.URGENTE)
                    .slaMinutos(10)
                    .estado(EstadoPaciente.REGISTRADO)
                    .fechaRegistro(fechaEstado)
                    .fechaEstado(fechaEstado)
                    .slaVenceEn(fechaEstado.plusSeconds(600))
                    .build();
        }
    }
}
