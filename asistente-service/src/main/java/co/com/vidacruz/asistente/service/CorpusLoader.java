package co.com.vidacruz.asistente.service;

import static co.com.vidacruz.asistente.utilities.Constants.MSG_CORPUS_NO_CARGADO;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_CORPUS_VACIO;
import static co.com.vidacruz.asistente.utilities.Constants.RUTA_CORPUS;

import co.com.vidacruz.asistente.domain.Protocolo;
import co.com.vidacruz.asistente.domain.SeccionProtocolo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Carga el corpus oficial al arrancar; si no se puede leer, la aplicación no arranca. */
@Slf4j
@Component
public class CorpusLoader {

    private final List<Protocolo> protocolos;

    public CorpusLoader(ObjectMapper objectMapper) {
        this.protocolos = cargar(objectMapper);
        log.info("Corpus cargado: {} protocolos", protocolos.size());
    }

    public List<Protocolo> protocolos() {
        return protocolos;
    }

    public boolean existe(String protocolo, String seccion) {
        return contenido(protocolo, seccion).isPresent();
    }

    public Optional<String> contenido(String protocolo, String seccion) {
        return protocolos.stream()
                .filter(p -> p.id().equals(protocolo))
                .flatMap(p -> p.secciones().stream())
                .filter(s -> s.nombre().equals(seccion))
                .map(SeccionProtocolo::contenido)
                .findFirst();
    }

    private static List<Protocolo> cargar(ObjectMapper objectMapper) {
        try (InputStream entrada = new ClassPathResource(RUTA_CORPUS).getInputStream()) {
            List<Protocolo> leidos = objectMapper.readValue(entrada, new TypeReference<>() { });
            if (leidos.isEmpty()) {
                throw new IllegalStateException(MSG_CORPUS_VACIO);
            }
            return List.copyOf(leidos);
        } catch (IOException e) {
            throw new IllegalStateException(MSG_CORPUS_NO_CARGADO + RUTA_CORPUS, e);
        }
    }
}
