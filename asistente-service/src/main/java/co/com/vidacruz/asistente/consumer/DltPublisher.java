package co.com.vidacruz.asistente.consumer;

import static co.com.vidacruz.asistente.utilities.Constants.HEADER_MOTIVO_DLT;
import static co.com.vidacruz.asistente.utilities.Constants.METRICA_EVENTOS;
import static co.com.vidacruz.asistente.utilities.Constants.TAG_TIPO;
import static co.com.vidacruz.asistente.utilities.Constants.TIPO_EVENTO_INVALIDO;

import co.com.vidacruz.asistente.config.EventosKafkaProperties;
import co.com.vidacruz.asistente.utilities.Saneador;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/** Publica en el DLT el mensaje original con el motivo; si la publicación falla, el error se propaga y no se confirma. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DltPublisher {

    private final KafkaSender<String, String> sender;
    private final EventosKafkaProperties properties;
    private final MeterRegistry meterRegistry;

    public Mono<Void> publicar(ReceiverRecord<String, String> registro, String motivo) {
        String motivoLimpio = Saneador.limpiar(motivo);
        log.warn("Evento descartado hacia {} (partición {}, offset {}): {}", properties.topicDlt(),
                registro.partition(), registro.offset(), motivoLimpio);
        ProducerRecord<String, String> envio =
                new ProducerRecord<>(properties.topicDlt(), registro.key(), registro.value());
        envio.headers().add(HEADER_MOTIVO_DLT, motivoLimpio.getBytes(StandardCharsets.UTF_8));
        return sender.send(Mono.just(SenderRecord.create(envio, registro.offset())))
                .doOnNext(resultado -> meterRegistry.counter(METRICA_EVENTOS, TAG_TIPO, TIPO_EVENTO_INVALIDO)
                        .increment())
                .then();
    }
}
