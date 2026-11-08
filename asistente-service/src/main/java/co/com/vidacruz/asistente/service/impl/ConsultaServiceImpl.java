package co.com.vidacruz.asistente.service.impl;

import static co.com.vidacruz.asistente.utilities.Constants.AVISO_SIN_PROTOCOLO;
import static co.com.vidacruz.asistente.utilities.Constants.METRICA_RESPUESTAS;
import static co.com.vidacruz.asistente.utilities.Constants.TAG_TIPO;
import static co.com.vidacruz.asistente.utilities.Constants.TIPO_CON_FUENTES;
import static co.com.vidacruz.asistente.utilities.Constants.TIPO_INVALIDA;
import static co.com.vidacruz.asistente.utilities.Constants.TIPO_SIN_PROTOCOLO;

import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.dto.response.ConsultaResponseDTO;
import co.com.vidacruz.asistente.dto.response.FuenteDTO;
import co.com.vidacruz.asistente.mapper.FuenteMapper;
import co.com.vidacruz.asistente.service.CompuertaRelevancia;
import co.com.vidacruz.asistente.service.ConsultaService;
import co.com.vidacruz.asistente.service.LlmGateway;
import co.com.vidacruz.asistente.service.RagRetriever;
import co.com.vidacruz.asistente.utilities.CitaCanonica;
import co.com.vidacruz.asistente.validators.ConsultaValidator;
import co.com.vidacruz.asistente.validators.RespuestaValidator;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultaServiceImpl implements ConsultaService {

    private final ConsultaValidator consultaValidator;
    private final RagRetriever ragRetriever;
    private final CompuertaRelevancia compuerta;
    private final LlmGateway llmGateway;
    private final RespuestaValidator respuestaValidator;
    private final FuenteMapper fuenteMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<ConsultaResponseDTO> consultar(ConsultaRequestDTO request) {
        return Mono.defer(() -> {
            consultaValidator.validar(request);
            String pregunta = request.getPregunta().strip();
            return ragRetriever.recuperar(pregunta)
                    .flatMap(segmentos -> compuerta.pasa(pregunta, segmentos)
                            ? consultarLlm(pregunta, segmentos)
                            : Mono.just(sinProtocolo(TIPO_SIN_PROTOCOLO)));
        });
    }

    private Mono<ConsultaResponseDTO> consultarLlm(String pregunta, List<SegmentoRecuperado> segmentos) {
        return llmGateway.consultar(pregunta, segmentos)
                .map(respuesta -> aRespuesta(pregunta, respuesta, segmentos));
    }

    private ConsultaResponseDTO aRespuesta(String pregunta, RespuestaLlm respuestaLlm,
                                         List<SegmentoRecuperado> segmentos) {
        RespuestaLlm respuesta = CitaCanonica.aplicar(respuestaLlm, segmentos);
        if (Boolean.TRUE.equals(respuesta.sinProtocolo())) {
            return sinProtocolo(TIPO_SIN_PROTOCOLO);
        }
        return respuestaValidator.esValida(pregunta, respuesta, segmentos)
                ? conFuentes(respuesta)
                : sinProtocolo(TIPO_INVALIDA);
    }

    private ConsultaResponseDTO conFuentes(RespuestaLlm respuesta) {
        List<FuenteDTO> fuentes = respuesta.fuentes().stream().distinct().map(fuenteMapper::aDto).toList();
        registrar(TIPO_CON_FUENTES);
        return ConsultaResponseDTO.builder().respuesta(respuesta.respuesta().strip()).fuentes(fuentes).build();
    }

    /** Respuesta exclusiva de aviso: sin contenido clínico y sin fuentes (RN-05). */
    private ConsultaResponseDTO sinProtocolo(String tipo) {
        registrar(tipo);
        log.info("Consulta respondida sin protocolo, tipo={}", tipo);
        return ConsultaResponseDTO.builder().respuesta(AVISO_SIN_PROTOCOLO).fuentes(List.of()).build();
    }

    private void registrar(String tipo) {
        meterRegistry.counter(METRICA_RESPUESTAS, TAG_TIPO, tipo).increment();
    }
}
