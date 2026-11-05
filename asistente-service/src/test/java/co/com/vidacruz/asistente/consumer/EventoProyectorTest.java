package co.com.vidacruz.asistente.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.exception.EventoInvalidoException;
import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import co.com.vidacruz.asistente.service.EventoParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandExecutionException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.RedisConnectionFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventoProyector")
class EventoProyectorTest {

    @Mock
    private UrgenciasRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private EventoProyector proyector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        proyector = new EventoProyector(new EventoParser(new ObjectMapper()), repository, meterRegistry);
    }

    private static String evento(String tipo, String nivel) {
        String campoNivel = nivel == null ? "" : ",\"nivel\":\"" + nivel + "\"";
        return "{\"tipo\":\"" + tipo + "\",\"pacienteId\":\"p1\"" + campoNivel + "}";
    }

    @Nested
    @DisplayName("Proyección")
    class Proyeccion {

        @Test
        void shouldAddToLevelSet_WhenPatientIsRegistered() {
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(proyector.proyectar(evento("paciente_registrado", "CRITICO"))).expectNextCount(1).verifyComplete();

            verify(repository).agregar(NivelTriage.CRITICO, "p1");
            assertThat(meterRegistry.counter("asistente.eventos", "tipo", "paciente_registrado").count())
                    .isEqualTo(1.0);
        }

        @Test
        void shouldKeepPatientActive_WhenPatientEntersConsultationOrIsEscalated() {
            when(repository.agregar(NivelTriage.URGENTE, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(proyector.proyectar(evento("paciente_en_consulta", "URGENTE"))).expectNextCount(1).verifyComplete();
            StepVerifier.create(proyector.proyectar(evento("paciente_escalado", "URGENTE"))).expectNextCount(1).verifyComplete();

            verify(repository, times(2)).agregar(NivelTriage.URGENTE, "p1");
            verify(repository, never()).quitar(NivelTriage.URGENTE, "p1");
        }

        @Test
        void shouldRemoveFromLevelSet_WhenPatientIsDischarged() {
            when(repository.quitar(NivelTriage.NO_URGENTE, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(proyector.proyectar(evento("paciente_alta", "NO_URGENTE"))).expectNextCount(1).verifyComplete();

            verify(repository).quitar(NivelTriage.NO_URGENTE, "p1");
            verify(repository, never()).agregar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void shouldFailAsInvalidEvent_WhenAltaHasNoLevel() {
            StepVerifier.create(proyector.proyectar(evento("paciente_alta", null)))
                    .expectError(EventoInvalidoException.class).verify();
        }
    }

    @Nested
    @DisplayName("Errores de Redis")
    class ErroresRedis {

        @Test
        void shouldRetryAndSucceed_WhenConnectionFailsTransiently() {
            AtomicInteger intentos = new AtomicInteger();
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.defer(() ->
                    intentos.incrementAndGet() < 3
                            ? Mono.error(new RedisConnectionFailureException("sin conexión"))
                            : Mono.empty()));

            StepVerifier.create(proyector.proyectar(evento("paciente_registrado", "CRITICO"))).expectNextCount(1).verifyComplete();

            assertThat(intentos).hasValue(3);
        }

        @Test
        void shouldPropagateWithoutDlt_WhenConnectionKeepsFailing() {
            when(repository.agregar(NivelTriage.CRITICO, "p1"))
                    .thenReturn(Mono.error(new RedisConnectionFailureException("sin conexión")));

            StepVerifier.create(proyector.proyectar(evento("paciente_registrado", "CRITICO")))
                    .expectErrorSatisfies(error -> assertThat(error).isNotInstanceOf(EventoInvalidoException.class))
                    .verify();
        }

        @Test
        void shouldTurnPermanentCommandErrorIntoInvalidEventWithoutRetrying_WhenRedisRejectsCommand() {
            AtomicInteger intentos = new AtomicInteger();
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.defer(() -> {
                intentos.incrementAndGet();
                return Mono.error(new InvalidDataAccessApiUsageException("WRONGTYPE",
                        new RedisCommandExecutionException("WRONGTYPE Operation against a key holding the wrong kind")));
            }));

            StepVerifier.create(proyector.proyectar(evento("paciente_registrado", "CRITICO")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(EventoInvalidoException.class);
                        assertThat(error.getMessage()).contains("Redis rechazó el comando").contains("WRONGTYPE");
                    })
                    .verify();

            assertThat(intentos).hasValue(1);
        }

        @Test
        void shouldRetryOnTransientServerErrors_WhenRedisIsLoading() {
            AtomicInteger intentos = new AtomicInteger();
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.defer(() ->
                    intentos.incrementAndGet() < 2
                            ? Mono.error(new RedisCommandExecutionException("LOADING Redis is loading the dataset"))
                            : Mono.empty()));

            StepVerifier.create(proyector.proyectar(evento("paciente_registrado", "CRITICO"))).expectNextCount(1).verifyComplete();

            assertThat(intentos).hasValue(2);
        }
    }
}
