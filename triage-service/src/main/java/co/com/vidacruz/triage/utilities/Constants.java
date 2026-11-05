package co.com.vidacruz.triage.utilities;

import java.util.Set;
import lombok.experimental.UtilityClass;

/** Los miembros se declaran static de forma explícita para poder importarlos de uno en uno. */
@UtilityClass
public class Constants {

    // Rutas
    public static final String RUTA_BASE_TRIAGE = "/triage";
    public static final String RUTA_PACIENTES = "/pacientes";
    public static final String RUTA_PACIENTE_POR_ID = "/pacientes/{id}";

    // Cabeceras
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    // Códigos de error
    public static final String CODIGO_VALIDACION = "VALIDACION_FALLIDA";
    public static final String CODIGO_SOLICITUD_INVALIDA = "SOLICITUD_INVALIDA";
    public static final String CODIGO_PACIENTE_NO_ENCONTRADO = "PACIENTE_NO_ENCONTRADO";
    public static final String CODIGO_RECURSO_NO_ENCONTRADO = "RECURSO_NO_ENCONTRADO";
    public static final String CODIGO_METODO_NO_PERMITIDO = "METODO_NO_PERMITIDO";
    public static final String CODIGO_MEDIA_TYPE_NO_SOPORTADO = "MEDIA_TYPE_NO_SOPORTADO";
    public static final String CODIGO_ERROR_INTERNO = "ERROR_INTERNO";

    // Mensajes
    public static final String MSG_VALIDACION = "Hay campos inválidos: %s";
    public static final String MSG_CAMPO_OBLIGATORIO = "El campo '%s' es obligatorio";
    public static final String MSG_CAMPO_RANGO = "El campo '%s' debe estar entre %s y %s";
    public static final String MSG_CAMPO_LONGITUD = "El campo '%s' debe tener entre %d y %d caracteres";
    public static final String MSG_CAMPO_LONGITUD_MAXIMA = "El campo '%s' no puede superar %d caracteres";
    public static final String MSG_CAMPO_VACIO = "El campo '%s' no puede estar vacío";
    public static final String MSG_CAMPO_DECIMALES = "El campo '%s' admite como máximo %d decimales";
    public static final String MSG_PRESION_DIASTOLICA = "El campo '%s' debe ser menor que la presión sistólica";
    public static final String MSG_CAMPO_TIPO_INVALIDO = "El campo '%s' tiene un tipo o formato inválido";
    public static final String MSG_SOLICITUD_INVALIDA = "El cuerpo de la solicitud es obligatorio y debe ser un JSON válido";
    public static final String MSG_PARAMETRO_INVALIDO = "El parámetro '%s' tiene un valor inválido";
    public static final String MSG_PACIENTE_NO_ENCONTRADO = "No existe un paciente con id %s";
    public static final String MSG_RECURSO_NO_ENCONTRADO = "El recurso solicitado no existe";
    public static final String MSG_METODO_NO_PERMITIDO = "El método HTTP no está permitido para este recurso";
    public static final String MSG_MEDIA_TYPE_NO_SOPORTADO = "El tipo de contenido no está soportado, use application/json";
    public static final String MSG_ERROR_INTERNO = "Ocurrió un error inesperado. Intente de nuevo más tarde";

    // Quejas administrativas normalizadas (ver TextNormalizer): coincidencia exacta, no "contiene".
    public static final Set<String> QUEJAS_ADMINISTRATIVAS = Set.of(
            "documentos",
            "recoleccion de resultados",
            "renovacion de recetario",
            "vacunacion",
            "seguimiento");
}
