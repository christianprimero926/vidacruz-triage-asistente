package co.com.vidacruz.asistente.exception;

/** El mensaje no se puede proyectar (ilegible, tipo o nivel desconocido); va al DLT en lugar de reintentarse. */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String motivo) {
        super(motivo);
    }

    public EventoInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
