package co.com.vidacruz.asistente;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("POST /asistente/consulta con retriever real y ChatModel simulado")
class ConsultaControllerIT {

    private static final String AVISO = "No existe un protocolo disponible para esta pregunta.";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatModel chatModel;

    private void modeloResponde(String json) {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());
    }

    private WebTestClient.ResponseSpec consultar(String pregunta) {
        return webTestClient.post().uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", pregunta))
                .exchange();
    }

    @Nested
    @DisplayName("Consultas del enunciado")
    class Enunciado {

        @Test
        void shouldAnswerAndCiteProto001_WhenAskingAboutHypertensiveCrisis() {
            modeloResponde("{\"respuesta\":\"Reconfirmar la presión tras 5 minutos de reposo y no reducirla más de "
                    + "un 25% en la primera hora.\",\"fuentes\":[{\"protocolo\":\"PROTO-001\","
                    + "\"seccion\":\"Medidas iniciales\"},{\"protocolo\":\"PROTO-001\","
                    + "\"seccion\":\"Medidas iniciales\"}],\"sinProtocolo\":false}");

            consultar("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.respuesta").value(containsString("5 minutos"))
                    .jsonPath("$.fuentes.length()").isEqualTo(1)
                    .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001")
                    .jsonPath("$.fuentes[0].seccion").isEqualTo("Medidas iniciales");
        }

        @Test
        void shouldAnswerAndCiteProto002_WhenAskingAboutFirstAntibioticInSepsis() {
            modeloResponde("```json\n{\"respuesta\":\"Antibiótico de amplio espectro en la primera hora.\","
                    + "\"fuentes\":[{\"protocolo\":\"PROTO-002\",\"seccion\":\"Acciones iniciales\"}],"
                    + "\"sinProtocolo\":false}\n```");

            consultar("¿Cuándo se administra el primer antibiótico en sepsis?")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-002")
                    .jsonPath("$.fuentes[0].seccion").isEqualTo("Acciones iniciales");
        }

        @Test
        void shouldReturnNoticeWithoutCallingLlm_WhenAskingAboutDiabetes() {
            consultar("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.respuesta").isEqualTo(AVISO)
                    .jsonPath("$.fuentes.length()").isEqualTo(0);

            verifyNoInteractions(chatModel);
        }

        @Test
        void shouldReturnNoticeWithoutCallingLlm_WhenAskingAboutPneumonia() {
            consultar("¿cuál es el tratamiento de la neumonía?")
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.respuesta").isEqualTo(AVISO);

            verifyNoInteractions(chatModel);
        }
    }

    @Nested
    @DisplayName("Segunda barrera anti-alucinación")
    class SegundaBarrera {

        @Test
        void shouldReturnOnlyNotice_WhenModelInventsAFigure() {
            modeloResponde("{\"respuesta\":\"Reconfirmar la presión tras 10 minutos de reposo.\","
                    + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}],"
                    + "\"sinProtocolo\":false}");

            consultar("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.respuesta").isEqualTo(AVISO)
                    .jsonPath("$.fuentes.length()").isEqualTo(0);
        }

        @Test
        void shouldReturnOnlyNotice_WhenModelCitesASectionThatDoesNotExist() {
            modeloResponde("{\"respuesta\":\"Reconfirmar la presión.\","
                    + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Dosis\"}],\"sinProtocolo\":false}");

            consultar("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.respuesta").isEqualTo(AVISO);
        }

        @Test
        void shouldReturnNotice_WhenQuestionPassesGateButModelFlagsSinProtocolo() {
            modeloResponde("{\"respuesta\":\"\",\"fuentes\":[],\"sinProtocolo\":true}");

            consultar("¿cómo manejo la hipertensión en el embarazo?")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.respuesta").isEqualTo(AVISO)
                    .jsonPath("$.fuentes.length()").isEqualTo(0);

            verify(chatModel).chat(any(ChatRequest.class));
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errores {

        @Test
        void shouldRespond503WithRetryAfter_WhenProviderFails() {
            when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("HTTP 500"));

            consultar("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "30")
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("LLM_NO_DISPONIBLE")
                    .jsonPath("$.status").isEqualTo(503)
                    .jsonPath("$.path").isEqualTo("/asistente/consulta")
                    .jsonPath("$.errores").doesNotExist();
        }

        @Test
        void shouldRespond503_WhenModelReturnsUnreadableJson() {
            modeloResponde("lo siento, no puedo");

            consultar("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                    .expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.codigo").isEqualTo("LLM_NO_DISPONIBLE");
        }

        @Test
        void shouldRespond400WithField_WhenQuestionIsBlank() {
            consultar("  ")
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("VALIDACION_ERROR")
                    .jsonPath("$.errores[0].campo").isEqualTo("pregunta");
        }

        @Test
        void shouldRespond400_WhenQuestionExceedsFiveHundredCharacters() {
            consultar("a".repeat(501))
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errores[0].campo").isEqualTo("pregunta");
        }

        @Test
        void shouldRespond400NamingTheField_WhenQuestionIsNotAString() {
            webTestClient.post().uri("/asistente/consulta")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"pregunta\":123}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errores[0].campo").isEqualTo("pregunta");
        }

        @Test
        void shouldRespond400_WhenJsonIsMalformed() {
            webTestClient.post().uri("/asistente/consulta")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"pregunta\":")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.codigo").isEqualTo("CUERPO_INVALIDO");
        }

        @Test
        void shouldRespond404InCommonFormat_WhenRouteDoesNotExist() {
            webTestClient.get().uri("/asistente/no-existe")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("NOT_FOUND")
                    .jsonPath("$.path").isEqualTo("/asistente/no-existe");
        }

        @Test
        void shouldRespond405InCommonFormat_WhenMethodIsNotAllowed() {
            webTestClient.get().uri("/asistente/consulta")
                    .exchange()
                    .expectStatus().isEqualTo(405)
                    .expectBody().jsonPath("$.codigo").isEqualTo("METHOD_NOT_ALLOWED");
        }

        @Test
        void shouldRespond415InCommonFormat_WhenContentTypeIsNotJson() {
            webTestClient.post().uri("/asistente/consulta")
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("hola")
                    .exchange()
                    .expectStatus().isEqualTo(415)
                    .expectBody().jsonPath("$.codigo").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        }
    }
}
