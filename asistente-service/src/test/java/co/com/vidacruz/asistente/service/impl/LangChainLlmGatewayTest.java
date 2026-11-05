package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.config.LlmProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import co.com.vidacruz.asistente.service.PromptBuilder;
import co.com.vidacruz.asistente.service.RespuestaLlmParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
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

    @BeforeEach
    void setUp() {
        scheduler = Schedulers.newBoundedElastic(2, 10, "llm-test");
    }

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    private LangChainLlmGateway gateway(String apiKey) {
        LlmProperties properties = new LlmProperties(apiKey, "http://localhost", "modelo", 2, 2, 10);
        return new LangChainLlmGateway(chatModel, new PromptBuilder(),
                new RespuestaLlmParser(new ObjectMapper()), properties, scheduler);
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
}
