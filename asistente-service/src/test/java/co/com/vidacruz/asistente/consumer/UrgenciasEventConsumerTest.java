package co.com.vidacruz.asistente.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.config.EventosKafkaProperties;
import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import co.com.vidacruz.asistente.service.EventoParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrgenciasEventConsumer.procesar")
class UrgenciasEventConsumerTest {

    @Mock
    private KafkaReceiver<String, String> receiver;
    @Mock
    private KafkaSender<String, String> sender;
    @Mock
    private UrgenciasRepository repository;
    @Mock
    private ReconstruccionEstado reconstruccion;
    @Mock
    private ReceiverOffset offset;

    private SimpleMeterRegistry meterRegistry;
    private UrgenciasEventConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        EventosKafkaProperties properties =
                new EventosKafkaProperties("localhost:9092", "triaje.eventos", "triaje.eventos.dlt", "g", 100, true);
        consumer = new UrgenciasEventConsumer(receiver, sender, new EventoParser(new ObjectMapper()), repository,
                reconstruccion, properties, meterRegistry);
    }

    private ReceiverRecord<String, String> registro(String valor) {
        return new ReceiverRecord<>(new ConsumerRecord<>("triaje.eventos", 0, 7L, "p1", valor), offset);
    }

    private static String evento(String tipo, String nivel) {
        String campoNivel = nivel == null ? "" : ",\"nivel\":\"" + nivel + "\"";
        return "{\"tipo\":\"" + tipo + "\",\"pacienteId\":\"p1\"" + campoNivel + "}";
    }

    @Nested
    @DisplayName("Eventos válidos")
    class Validos {

        @Test
        void shouldAddToLevelSetAndAcknowledge_WhenPatientIsRegistered() {
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(consumer.procesar(registro(evento("paciente_registrado", "CRITICO"))))
                    .verifyComplete();

            verify(repository).agregar(NivelTriage.CRITICO, "p1");
            verify(offset).acknowledge();
            assertThat(meterRegistry.counter("asistente.eventos", "tipo", "paciente_registrado").count())
                    .isEqualTo(1.0);
        }

        @Test
        void shouldKeepPatientActive_WhenPatientEntersConsultation() {
            when(repository.agregar(NivelTriage.URGENTE, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(consumer.procesar(registro(evento("paciente_en_consulta", "URGENTE"))))
                    .verifyComplete();

            verify(repository).agregar(NivelTriage.URGENTE, "p1");
            verify(offset).acknowledge();
        }

        @Test
        void shouldRemoveFromLevelSetAndAcknowledge_WhenPatientIsDischarged() {
            when(repository.quitar(NivelTriage.NO_URGENTE, "p1")).thenReturn(Mono.empty());

            StepVerifier.create(consumer.procesar(registro(evento("paciente_alta", "NO_URGENTE"))))
                    .verifyComplete();

            verify(repository).quitar(NivelTriage.NO_URGENTE, "p1");
            verify(repository, never()).agregar(any(), any());
            verify(offset).acknowledge();
        }

        @Test
        void shouldRetryRedisAndAcknowledgeOnce_WhenRedisFailsTransiently() {
            AtomicInteger intentos = new AtomicInteger();
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.defer(() ->
                    intentos.incrementAndGet() < 3 ? Mono.error(new IllegalStateException("redis")) : Mono.empty()));

            StepVerifier.create(consumer.procesar(registro(evento("paciente_registrado", "CRITICO"))))
                    .verifyComplete();

            assertThat(intentos).hasValue(3);
            verify(offset).acknowledge();
        }
    }

    @Nested
    @DisplayName("Redis caído")
    class RedisCaido {

        @Test
        void shouldPropagateErrorWithoutAcknowledging_WhenRedisKeepsFailing() {
            when(repository.agregar(NivelTriage.CRITICO, "p1")).thenReturn(Mono.error(new IllegalStateException("redis")));

            StepVerifier.create(consumer.procesar(registro(evento("paciente_registrado", "CRITICO"))))
                    .expectError()
                    .verify();

            verify(offset, never()).acknowledge();
            verifyNoInteractions(sender);
        }
    }

    @Nested
    @DisplayName("Mensajes inválidos hacia el DLT")
    class Dlt {

        @SuppressWarnings("unchecked")
        private ProducerRecord<String, String> enviadoADlt() {
            ArgumentCaptor<Publisher<SenderRecord<String, String, Long>>> captor = ArgumentCaptor.forClass(Publisher.class);
            verify(sender).send(captor.capture());
            return Flux.from(captor.getValue()).blockFirst();
        }

        private void sobreviveYSeConfirma(String valor, String motivoEsperado) {
            when(sender.send(any(Publisher.class))).thenReturn(Flux.just(org.mockito.Mockito.mock(SenderResult.class)));

            StepVerifier.create(consumer.procesar(registro(valor))).verifyComplete();

            ProducerRecord<String, String> enviado = enviadoADlt();
            assertThat(enviado.topic()).isEqualTo("triaje.eventos.dlt");
            assertThat(enviado.key()).isEqualTo("p1");
            assertThat(enviado.value()).isEqualTo(valor);
            assertThat(new String(enviado.headers().lastHeader("motivo-dlt").value(), StandardCharsets.UTF_8))
                    .contains(motivoEsperado);
            verify(offset).acknowledge();
            verifyNoInteractions(repository);
            assertThat(meterRegistry.counter("asistente.eventos", "tipo", "invalido").count()).isEqualTo(1.0);
        }

        @Test
        void shouldSendToDlt_WhenAltaHasNoLevel() {
            sobreviveYSeConfirma(evento("paciente_alta", null), "Nivel");
        }

        @Test
        void shouldSendToDlt_WhenLevelIsUnknown() {
            sobreviveYSeConfirma(evento("paciente_registrado", "GRAVISIMO"), "GRAVISIMO");
        }

        @Test
        void shouldSendToDlt_WhenTypeIsUnknown() {
            sobreviveYSeConfirma(evento("paciente_trasladado", "CRITICO"), "paciente_trasladado");
        }

        @Test
        void shouldSendToDlt_WhenPayloadIsNotJson() {
            sobreviveYSeConfirma("esto no es json", "JSON");
        }

        @Test
        void shouldNotAcknowledge_WhenDltPublicationFails() {
            when(sender.send(any(Publisher.class))).thenReturn(Flux.error(new IllegalStateException("kafka")));

            StepVerifier.create(consumer.procesar(registro("esto no es json"))).expectError().verify();

            verify(offset, never()).acknowledge();
        }
    }
}
