package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.config.LlmProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import co.com.vidacruz.asistente.service.LlmConversacion;
import co.com.vidacruz.asistente.service.LlmResilience;
import co.com.vidacruz.asistente.service.PromptBuilder;
import co.com.vidacruz.asistente.service.RespuestaLlmParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("LangChainLlmGateway")
class LangChainLlmGatewayTest {

    private static final String JSON = "{\"respuesta\":\"Reconfirmar la presión.\","
            + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}],\"sinProtocolo\":false}";
    private static final List<SegmentoRecuperado> CONTEXTO =
            List.of(new SegmentoRecuperado("PROTO-001", "Medidas iniciales", "texto", 0.84));

    @Mock
    private ChatModel chatModel;

    private Scheduler scheduler;
    private CircuitBreakerRegistry registry;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("llm", CircuitBreakerConfig.custom().slidingWindowSize(4)
                .minimumNumberOfCalls(2).failureRateThreshold(50).build());
        meterRegistry = new SimpleMeterRegistry();
        scheduler = Schedulers.newBoundedElastic(2, 10, "llm-test");
    }

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    private LangChainLlmGateway gateway(String apiKey) {
        LlmProperties properties = new LlmProperties(apiKey, "http://localhost", "modelo", 1, 2, 10);
        LlmConversacion conversacion = new LlmConversacion(new PromptBuilder(), new RespuestaLlmParser(new ObjectMapper()));
        return new LangChainLlmGateway(chatModel, conversacion, properties,
                new LlmResilience(registry, meterRegistry, scheduler, properties));
    }

    private static ChatResponse respondeCon(String texto) {
        return ChatResponse.builder().aiMessage(AiMessage.from(texto)).build();
    }

    @Test
    void shouldReturnParsedAnswerOffTheCallerThread_WhenModelResponds() {
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocacion -> {
            assertThat(Thread.currentThread().getName()).startsWith("llm-test");
            return respondeCon(JSON);
        });

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .assertNext(respuesta -> assertThat(respuesta.respuesta()).isEqualTo("Reconfirmar la presión."))
                .verifyComplete();
    }

    @Test
    void shouldFailWithoutCallingModel_WhenApiKeyIsBlank() {
        StepVerifier.create(gateway(" ").consultar("¿medidas?", CONTEXTO))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(LlmNoDisponibleException.class)
                        .hasMessageContaining("no está configurado"))
                .verify();

        verifyNoInteractions(chatModel);
    }

    @Test
    void shouldMapAnyModelFailure_WhenProviderErrors() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("HTTP 500"));

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .expectError(LlmNoDisponibleException.class)
                .verify();
    }

    @Test
    void shouldMapToLlmNoDisponible_WhenModelReturnsUnreadableJson() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respondeCon("no sé"));

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .expectError(LlmNoDisponibleException.class)
                .verify();
    }

    private void fallarVeces(LangChainLlmGateway gateway, int veces) {
        for (int i = 0; i < veces; i++) {
            StepVerifier.create(gateway.consultar("¿medidas?", CONTEXTO))
                    .expectError(LlmNoDisponibleException.class).verify();
        }
    }

    private long muestras(String resultado) {
        return meterRegistry.timer("asistente.llm.latencia", "resultado", resultado).count();
    }

    @Test
    void shouldFailAsLlmNoDisponible_WhenModelExceedsTimeout() {
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocacion -> {
            Thread.sleep(5000);
            return respondeCon(JSON);
        });

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .expectError(LlmNoDisponibleException.class)
                .verify();
        assertThat(muestras("error")).isEqualTo(1);
    }

    @Test
    void shouldOpenCircuitAndNotCallModel_WhenFailureRateExceedsThreshold() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("HTTP 500"));
        LangChainLlmGateway gateway = gateway("clave");

        fallarVeces(gateway, 2);
        assertThat(registry.circuitBreaker("llm").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        fallarVeces(gateway, 3);

        verify(chatModel, times(2)).chat(any(ChatRequest.class));
        assertThat(muestras("error")).isEqualTo(2);
        assertThat(muestras("circuito_abierto")).isEqualTo(3);
    }

    @Test
    void shouldRecordOkLatency_WhenModelResponds() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respondeCon(JSON));

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO)).expectNextCount(1).verifyComplete();

        assertThat(muestras("ok")).isEqualTo(1);
        assertThat(muestras("error")).isZero();
    }

    @Test
    void shouldCountUnreadableJsonAsFailureOfTheCircuit_WhenModelReturnsGarbage() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respondeCon("basura"));
        LangChainLlmGateway gateway = gateway("clave");

        fallarVeces(gateway, 2);

        assertThat(registry.circuitBreaker("llm").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldFailInsteadOfCompletingEmpty_WhenModelReturnsBlankText() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respondeCon("   "));

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .expectError(LlmNoDisponibleException.class)
                .verify();
        assertThat(muestras("error")).isEqualTo(1);
    }

    @Test
    void shouldFailInsteadOfCompletingEmpty_WhenModelReturnsNullText() {
        AiMessage sinTexto = org.mockito.Mockito.mock(AiMessage.class);
        ChatResponse respuesta = ChatResponse.builder().aiMessage(sinTexto).build();
        when(sinTexto.text()).thenReturn(null);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respuesta);

        StepVerifier.create(gateway("clave").consultar("¿medidas?", CONTEXTO))
                .expectError(LlmNoDisponibleException.class)
                .verify();
    }

    @Test
    void shouldCountEmptyAnswersAsCircuitFailures_WhenModelKeepsReturningNothing() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(respondeCon(" "));
        LangChainLlmGateway gateway = gateway("clave");

        fallarVeces(gateway, 2);

        assertThat(registry.circuitBreaker("llm").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
