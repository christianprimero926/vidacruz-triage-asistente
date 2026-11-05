package co.com.vidacruz.asistente.utilities;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final String RUTA_CORPUS = "corpus/protocolos.json";
    public static final String MSG_CORPUS_NO_CARGADO = "No se pudo cargar el corpus de protocolos desde ";
    public static final String MSG_CORPUS_VACIO = "El corpus de protocolos no contiene protocolos";
    public static final String MSG_BUSQUEDA_FALLIDA = "No se pudo calcular el embedding de la consulta";
    public static final String FORMATO_SEGMENTO = "%s — %s: %s";
}
