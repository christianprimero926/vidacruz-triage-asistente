package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.com.vidacruz.asistente.consumer.DltPublisher;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Trazas y correlación del consumidor de eventos")
class TrazasConsumidorIT extends KafkaRedisITBase {

    private static final String TOPIC = "eventos-trazas";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_PADRE = "00f067aa0ba902b7";
    private static final InMemorySpanExporter EXPORTADOR = InMemorySpanExporter.create();

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger loggerConsumidor = (Logger) LoggerFactory.getLogger(DltPublisher.class);

    @TestConfiguration
    static class ExportadorEnMemoria {
        @Bean
        InMemorySpanExporter exportadorEnMemoria() {
            return EXPORTADOR;
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registrarPropiedades(registry, "trazas");
        registry.add("management.tracing.enabled", () -> "true");
        registry.add("management.otlp.tracing.export.enabled", () -> "false");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "100ms");
    }

    @BeforeEach
    void capturarLogs() {
        logs.start();
        loggerConsumidor.addAppender(logs);
        EXPORTADOR.reset();
    }

    @AfterEach
    void liberarLogs() {
        loggerConsumidor.detachAppender(logs);
    }

    @Test
    void shouldContinueTraceFromKafkaHeaderAndPutCorrelationInMdc_WhenEventIsProcessed() {
        publicar(TOPIC, "paciente-1", "esto no es json", Map.of(
                "traceparent", "00-" + TRACE_ID + "-" + SPAN_PADRE + "-01",
                "correlation-id", "correlacion-kafka-01"));

        await().atMost(Duration.ofSeconds(30)).until(() -> logs.list.stream()
                .anyMatch(e -> e.getFormattedMessage().startsWith("Evento descartado")));

        ILoggingEvent registro = logs.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("Evento descartado")).findFirst().orElseThrow();
        assertThat(registro.getMDCPropertyMap())
                .containsEntry("correlationId", "correlacion-kafka-01")
                .containsEntry("traceId", TRACE_ID);

        await().atMost(Duration.ofSeconds(30)).until(() -> spanDelEvento().isPresent());
        SpanData span = spanDelEvento().orElseThrow();
        assertThat(span.getName()).isEqualTo("urgencias.evento");
        assertThat(span.getParentSpanId()).isEqualTo(SPAN_PADRE);
    }

    private Optional<SpanData> spanDelEvento() {
        return EXPORTADOR.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("urgencias.evento") && TRACE_ID.equals(s.getTraceId())).findFirst();
    }

    @Test
    void shouldIgnoreUnsafeCorrelationHeader_WhenItDoesNotMatchTheAllowedFormat() {
        publicar(TOPIC, "paciente-2", "tampoco es json", Map.of("correlation-id", "mal valor!"));

        await().atMost(Duration.ofSeconds(30)).until(() -> logs.list.stream()
                .anyMatch(e -> e.getFormattedMessage().startsWith("Evento descartado")));

        assertThat(logs.list.stream().filter(e -> e.getFormattedMessage().startsWith("Evento descartado"))
                .findFirst().orElseThrow().getMDCPropertyMap()).doesNotContainKey("correlationId");
    }
}
