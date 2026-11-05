package co.com.vidacruz.asistente.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.exception.EventoInvalidoException;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrgenciasEventConsumer.procesar (orquestación)")
class UrgenciasEventConsumerTest {

    @Mock
    private KafkaReceiver<String, String> receiver;
    @Mock
    private EventoProyector proyector;
    @Mock
    private DltPublisher dltPublisher;
    @Mock
    private ReconstruccionEstado reconstruccion;
    @Mock
    private ReceiverOffset offset;

    private UrgenciasEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UrgenciasEventConsumer(receiver, proyector, dltPublisher, reconstruccion,
                ObservationRegistry.NOOP);
    }

    private ReceiverRecord<String, String> registro(String valor) {
        return new ReceiverRecord<>(new ConsumerRecord<>("triaje.eventos", 3, 7L, "p1", valor), offset);
    }

    @Test
    void shouldProjectThenMarkProgressThenAcknowledge_WhenEventIsValid() {
        when(proyector.proyectar("evento")).thenReturn(Mono.empty());
        when(reconstruccion.alProcesar(3, 7L)).thenReturn(Mono.empty());

        StepVerifier.create(consumer.procesar(registro("evento"))).verifyComplete();

        InOrder orden = inOrder(proyector, reconstruccion, offset);
        orden.verify(proyector).proyectar("evento");
        orden.verify(reconstruccion).alProcesar(3, 7L);
        orden.verify(offset).acknowledge();
    }

    @Test
    void shouldPublishToDltWithReasonAndAcknowledge_WhenEventIsInvalid() {
        ReceiverRecord<String, String> registro = registro("basura");
        when(proyector.proyectar("basura")).thenReturn(Mono.error(new EventoInvalidoException("Nivel ausente")));
        when(dltPublisher.publicar(registro, "Nivel ausente")).thenReturn(Mono.empty());
        when(reconstruccion.alProcesar(anyInt(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(consumer.procesar(registro)).verifyComplete();

        verify(dltPublisher).publicar(registro, "Nivel ausente");
        verify(offset).acknowledge();
    }

    @Test
    void shouldNotAcknowledge_WhenDltPublicationFails() {
        when(proyector.proyectar(any())).thenReturn(Mono.error(new EventoInvalidoException("x")));
        when(dltPublisher.publicar(any(), eq("x"))).thenReturn(Mono.error(new IllegalStateException("kafka")));

        StepVerifier.create(consumer.procesar(registro("basura"))).expectError(IllegalStateException.class).verify();

        verify(offset, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeNorSendToDlt_WhenRedisIsUnavailable() {
        when(proyector.proyectar(any())).thenReturn(Mono.error(new IllegalStateException("redis caído")));

        StepVerifier.create(consumer.procesar(registro("evento"))).expectError(IllegalStateException.class).verify();

        verify(offset, never()).acknowledge();
        verify(dltPublisher, never()).publicar(any(), any());
    }

    @Test
    void shouldNotAcknowledge_WhenMarkingPartitionFails() {
        when(proyector.proyectar(any())).thenReturn(Mono.empty());
        when(reconstruccion.alProcesar(3, 7L)).thenReturn(Mono.error(new IllegalStateException("redis")));

        StepVerifier.create(consumer.procesar(registro("evento"))).expectError(IllegalStateException.class).verify();

        verify(offset, never()).acknowledge();
    }
}
