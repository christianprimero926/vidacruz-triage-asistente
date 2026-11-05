package co.com.vidacruz.triage.validators;

import static co.com.vidacruz.triage.utilities.Constants.MSG_ESTADO_INVALIDO;
import static co.com.vidacruz.triage.utilities.ConstantsFields.ESTADO;

import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EstadoValidator {

    /** Devuelve el estado pedido o lanza ValidationException si falta o no existe. */
    public EstadoPaciente validar(EstadoRequestDTO request) {
        ErrorCollector errores = new ErrorCollector();
        EstadoPaciente estado = null;
        errores.textoObligatorio(ESTADO, request.getEstado(), Integer.MAX_VALUE);
        if (!errores.tieneError(ESTADO)) {
            estado = Arrays.stream(EstadoPaciente.values())
                    .filter(candidato -> candidato.name().equals(request.getEstado().trim()))
                    .findFirst()
                    .orElse(null);
            if (estado == null) {
                errores.agregar(ESTADO, String.format(MSG_ESTADO_INVALIDO, ESTADO, opciones()));
            }
        }
        errores.lanzarSiHayErrores();
        return estado;
    }

    private String opciones() {
        return Arrays.stream(EstadoPaciente.values()).map(EstadoPaciente::name).collect(Collectors.joining(", "));
    }
}
