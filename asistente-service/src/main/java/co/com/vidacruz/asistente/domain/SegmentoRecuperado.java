package co.com.vidacruz.asistente.domain;

/** Fragmento del corpus devuelto por la búsqueda semántica, con su puntaje de relevancia. */
public record SegmentoRecuperado(String protocolo, String seccion, String texto, double score) {
}
