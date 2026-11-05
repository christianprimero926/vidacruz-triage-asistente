package co.com.vidacruz.asistente.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.config.EventosKafkaProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("DltPublisher")
class DltPublisherTest {

    @Mock
    private KafkaSender<String, String> sender;
    @Mock
    private ReceiverOffset offset;

    private SimpleMeterRegistry meterRegistry;
    private DltPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new DltPublisher(sender,
                new EventosKafkaProperties("localhost:9092", "triaje.eventos", "triaje.eventos.dlt", "g", 100, true),
                meterRegistry);
    }

    @SuppressWarnings("unchecked")
    private SenderRecord<String, String, Long> enviado() {
        ArgumentCaptor<Publisher<SenderRecord<String, String, Long>>> captor = ArgumentCaptor.forClass(Publisher.class);
        verify(sender).send(captor.capture());
        return Flux.from(captor.getValue()).blockFirst();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void enviaOk() {
        when(sender.send(any(Publisher.class))).thenReturn(Flux.just(mock(SenderResult.class)));
    }

    private ReceiverRecord<String, String> registro(String valor) {
        return new ReceiverRecord<>(new ConsumerRecord<>("triaje.eventos", 0, 5L, "p1", valor), offset);
    }

    @Test
    void shouldKeepOriginalKeyAndPayloadAndAddReason_WhenPublishing() {
        enviaOk();

        StepVerifier.create(publisher.publicar(registro("{roto"), "Nivel ausente")).verifyComplete();

        SenderRecord<String, String, Long> envio = enviado();
        assertThat(envio.topic()).isEqualTo("triaje.eventos.dlt");
        assertThat(envio.key()).isEqualTo("p1");
        assertThat(envio.value()).isEqualTo("{roto");
        assertThat(new String(envio.headers().lastHeader("motivo-dlt").value(), StandardCharsets.UTF_8))
                .isEqualTo("Nivel ausente");
        assertThat(meterRegistry.counter("asistente.eventos", "tipo", "invalido").count()).isEqualTo(1.0);
    }

    @Test
    void shouldStripControlCharactersAndTruncateReason_WhenItCarriesRawTopicData() {
        enviaOk();
        String motivo = "Tipo desconocido: x\r\nFALSO log injection\t" + "a".repeat(300);

        StepVerifier.create(publisher.publicar(registro("x"), motivo)).verifyComplete();

        String cabecera = new String(enviado().headers().lastHeader("motivo-dlt").value(), StandardCharsets.UTF_8);
        assertThat(cabecera).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t");
        assertThat(cabecera).hasSize(100).startsWith("Tipo desconocido: x");
    }
}
