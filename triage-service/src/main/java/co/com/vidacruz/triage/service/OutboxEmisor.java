package co.com.vidacruz.triage.service;

import static co.com.vidacruz.triage.utilities.Constants.HEADER_CORRELATION_KAFKA;
import static co.com.vidacruz.triage.utilities.Constants.HEADER_TIPO_EVENTO;
import static co.com.vidacruz.triage.utilities.Constants.HEADER_TRACEPARENT;

import co.com.vidacruz.triage.config.TriageKafkaProperties;
import co.com.vidacruz.triage.domain.OutboxEvento;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/** Envía un evento del outbox a Kafka de a uno, para saber exactamente cuál rechazó el broker. */
@Component
@RequiredArgsConstructor
public class OutboxEmisor {

    private final KafkaSender<String, String> sender;
    private final TriageKafkaProperties kafkaProperties;

    /** Completa al confirmar el broker; si no, falla con la excepción original de Kafka. */
    public Mono<Void> enviar(OutboxEvento evento) {
        return sender.send(Mono.just(aRegistro(evento)))
                .doOnNext(resultado -> {
                    if (resultado.exception() != null) {
                        throw Exceptions.propagate(resultado.exception());
                    }
                })
                .then();
    }

    private SenderRecord<String, String, UUID> aRegistro(OutboxEvento evento) {
        ProducerRecord<String, String> registro = new ProducerRecord<>(
                kafkaProperties.getTopic(), evento.getPacienteId().toString(), evento.getPayload());
        registro.headers().add(new RecordHeader(HEADER_TIPO_EVENTO, bytes(evento.getTipoEvento())));
        agregarSiExiste(registro, HEADER_CORRELATION_KAFKA, evento.getCorrelationId());
        agregarSiExiste(registro, HEADER_TRACEPARENT, evento.getTraceparent());
        return SenderRecord.create(registro, evento.getId());
    }

    private void agregarSiExiste(ProducerRecord<String, String> registro, String nombre, String valor) {
        if (valor != null) {
            registro.headers().add(new RecordHeader(nombre, bytes(valor)));
        }
    }

    private byte[] bytes(String valor) {
        return valor.getBytes(StandardCharsets.UTF_8);
    }
}
