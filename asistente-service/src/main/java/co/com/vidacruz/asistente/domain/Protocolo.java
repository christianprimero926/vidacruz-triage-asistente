package co.com.vidacruz.asistente.domain;

import java.util.List;

public record Protocolo(String id, String titulo, List<SeccionProtocolo> secciones) {
}
