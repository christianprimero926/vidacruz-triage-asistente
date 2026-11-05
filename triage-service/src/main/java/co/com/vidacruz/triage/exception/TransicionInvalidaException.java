package co.com.vidacruz.triage.exception;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_TRANSICION_INVALIDA;
import static co.com.vidacruz.triage.utilities.Constants.MSG_SIN_TRANSICIONES;
import static co.com.vidacruz.triage.utilities.Constants.MSG_TRANSICION_INVALIDA;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import java.util.Comparator;
import java.util.stream.Collectors;

public class TransicionInvalidaException extends BusinessException {

    public TransicionInvalidaException(EstadoPaciente origen, EstadoPaciente destino) {
        super(ErrorType.CONFLICTO, CODIGO_TRANSICION_INVALIDA,
                String.format(MSG_TRANSICION_INVALIDA, origen, destino, origen, validas(origen)));
    }

    private static String validas(EstadoPaciente origen) {
        String lista = origen.transicionesValidas().stream()
                .sorted(Comparator.naturalOrder())
                .map(EstadoPaciente::name)
                .collect(Collectors.joining(", "));
        return lista.isEmpty() ? MSG_SIN_TRANSICIONES : lista;
    }
}
