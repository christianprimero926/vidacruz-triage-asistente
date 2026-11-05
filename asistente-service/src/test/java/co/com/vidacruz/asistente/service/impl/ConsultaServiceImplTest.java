package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.dto.response.ConsultaResponseDTO;
import co.com.vidacruz.asistente.dto.response.FuenteDTO;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import co.com.vidacruz.asistente.exception.ValidationException;
import co.com.vidacruz.asistente.mapper.FuenteMapperImpl;
import co.com.vidacruz.asistente.service.CompuertaRelevancia;
import co.com.vidacruz.asistente.service.LlmGateway;
import co.com.vidacruz.asistente.service.RagRetriever;
import co.com.vidacruz.asistente.validators.ConsultaValidator;
import co.com.vidacruz.asistente.validators.RespuestaValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultaServiceImpl")
class ConsultaServiceImplTest {

    private static final String AVISO = "No existe un protocolo disponible para esta pregunta.";
    private static final String PREGUNTA = "¿medidas iniciales en crisis hipertensiva?";
    private static final List<SegmentoRecuperado> SEGMENTOS =
            List.of(new SegmentoRecuperado("PROTO-001", "Medidas iniciales", "texto", 0.84));
    private static final FuenteCitada FUENTE = new FuenteCitada("PROTO-001", "Medidas iniciales");

    @Mock
    private RagRetriever ragRetriever;
    @Mock
    private CompuertaRelevancia compuerta;
    @Mock
    private LlmGateway llmGateway;
    @Mock
    private RespuestaValidator respuestaValidator;

    private SimpleMeterRegistry meterRegistry;
    private ConsultaServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ConsultaServiceImpl(new ConsultaValidator(), ragRetriever, compuerta, llmGateway,
                respuestaValidator, new FuenteMapperImpl(), meterRegistry);
    }

    private double contador(String tipo) {
        return meterRegistry.counter("asistente.respuestas", "tipo", tipo).count();
    }

    private static ConsultaRequestDTO pregunta() {
        return ConsultaRequestDTO.builder().pregunta(PREGUNTA).build();
    }

    @Nested
    @DisplayName("Sin protocolo relevante")
    class SinProtocolo {

        @Test
        void shouldReturnNoticeWithoutCallingLlm_WhenGateRejectsQuestion() {
            when(ragRetriever.recuperar(PREGUNTA)).thenReturn(Mono.just(SEGMENTOS));
            when(compuerta.pasa(PREGUNTA, SEGMENTOS)).thenReturn(false);

            StepVerifier.create(service.consultar(pregunta()))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getRespuesta()).isEqualTo(AVISO);
                        assertThat(respuesta.getFuentes()).isEmpty();
                    })
                    .verifyComplete();

            verifyNoInteractions(llmGateway);
            assertThat(contador("sin_protocolo")).isEqualTo(1.0);
        }

        @Test
        void shouldReturnNoticeWithoutCallingLlm_WhenNothingWasRetrieved() {
            when(ragRetriever.recuperar(PREGUNTA)).thenReturn(Mono.just(List.of()));
            when(compuerta.pasa(PREGUNTA, List.of())).thenReturn(false);

            StepVerifier.create(service.consultar(pregunta()))
                    .assertNext(respuesta -> assertThat(respuesta.getRespuesta()).isEqualTo(AVISO))
                    .verifyComplete();

            verifyNoInteractions(llmGateway);
        }
    }

    @Nested
    @DisplayName("Con protocolo relevante")
    class ConProtocolo {

        @BeforeEach
        void relevante() {
            when(ragRetriever.recuperar(PREGUNTA)).thenReturn(Mono.just(SEGMENTOS));
            when(compuerta.pasa(PREGUNTA, SEGMENTOS)).thenReturn(true);
        }

        @Test
        void shouldReturnAnswerWithDeduplicatedSources_WhenAnswerIsValid() {
            RespuestaLlm llm = new RespuestaLlm("Reconfirmar la presión tras 5 minutos.",
                    List.of(FUENTE, FUENTE), false);
            when(llmGateway.consultar(PREGUNTA, SEGMENTOS)).thenReturn(Mono.just(llm));
            when(respuestaValidator.esValida(llm, SEGMENTOS)).thenReturn(true);

            StepVerifier.create(service.consultar(pregunta()))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getRespuesta()).isEqualTo("Reconfirmar la presión tras 5 minutos.");
                        assertThat(respuesta.getFuentes())
                                .containsExactly(new FuenteDTO("PROTO-001", "Medidas iniciales"));
                    })
                    .verifyComplete();
            assertThat(contador("con_fuentes")).isEqualTo(1.0);
        }

        @Test
        void shouldReturnOnlyNoticeAndNoSources_WhenAnswerIsInvalid() {
            RespuestaLlm llm = new RespuestaLlm("Dar 10 mg de algo.", List.of(FUENTE), false);
            when(llmGateway.consultar(PREGUNTA, SEGMENTOS)).thenReturn(Mono.just(llm));
            when(respuestaValidator.esValida(any(), any())).thenReturn(false);

            StepVerifier.create(service.consultar(pregunta()))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.getRespuesta()).isEqualTo(AVISO);
                        assertThat(respuesta.getFuentes()).isEmpty();
                    })
                    .verifyComplete();
            assertThat(contador("invalida")).isEqualTo(1.0);
            assertThat(contador("con_fuentes")).isZero();
        }

        @Test
        void shouldPropagateLlmFailure_WhenProviderIsUnavailable() {
            when(llmGateway.consultar(PREGUNTA, SEGMENTOS))
                    .thenReturn(Mono.error(LlmNoDisponibleException.noConfigurado()));

            StepVerifier.create(service.consultar(pregunta()))
                    .expectError(LlmNoDisponibleException.class)
                    .verify();
        }
    }

    @Test
    void shouldCountAsSinProtocoloNotInvalida_WhenLlmDeclaresSinProtocolo() {
        RespuestaLlm llm = new RespuestaLlm("", List.of(), true);
        when(ragRetriever.recuperar(PREGUNTA)).thenReturn(Mono.just(SEGMENTOS));
        when(compuerta.pasa(PREGUNTA, SEGMENTOS)).thenReturn(true);
        when(llmGateway.consultar(PREGUNTA, SEGMENTOS)).thenReturn(Mono.just(llm));

        StepVerifier.create(service.consultar(pregunta()))
                .assertNext(respuesta -> assertThat(respuesta.getRespuesta()).isEqualTo(AVISO))
                .verifyComplete();

        assertThat(contador("sin_protocolo")).isEqualTo(1.0);
        assertThat(contador("invalida")).isZero();
    }

    @Nested
    @DisplayName("Validación de entrada")
    class Validacion {

        @Test
        void shouldFailBeforeSearching_WhenQuestionIsBlank() {
            StepVerifier.create(service.consultar(ConsultaRequestDTO.builder().pregunta(" ").build()))
                    .expectError(ValidationException.class)
                    .verify();

            verifyNoInteractions(ragRetriever, llmGateway);
        }

        @Test
        void shouldSearchWithTrimmedQuestion_WhenQuestionHasSurroundingSpaces() {
            when(ragRetriever.recuperar(PREGUNTA)).thenReturn(Mono.just(List.of()));
            when(compuerta.pasa(PREGUNTA, List.of())).thenReturn(false);

            StepVerifier.create(service.consultar(ConsultaRequestDTO.builder().pregunta("  " + PREGUNTA + " ").build()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(ragRetriever).recuperar(PREGUNTA);
        }
    }
}
