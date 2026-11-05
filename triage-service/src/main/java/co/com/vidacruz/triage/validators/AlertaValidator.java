package co.com.vidacruz.triage.validators;

import static co.com.vidacruz.triage.utilities.Constants.MSG_ESTADO_INVALIDO;
import static co.com.vidacruz.triage.utilities.ConstantsFields.ESTADO;

import co.com.vidacruz.triage.domain.EstadoAlerta;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AlertaValidator {

    /** El filtro es opcional: vacío devuelve todas; un valor desconocido lanza ValidationException. */
    public Optional<EstadoAlerta> validarFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return Optional.empty();
        }
        Optional<EstadoAlerta> encontrado = Arrays.stream(EstadoAlerta.values())
                .filter(candidato -> candidato.name().equals(estado.trim()))
                .findFirst();
        if (encontrado.isEmpty()) {
            ErrorCollector errores = new ErrorCollector();
            String opciones = Arrays.stream(EstadoAlerta.values()).map(EstadoAlerta::name)
                    .collect(Collectors.joining(", "));
            errores.agregar(ESTADO, String.format(MSG_ESTADO_INVALIDO, ESTADO, opciones));
            errores.lanzarSiHayErrores();
        }
        return encontrado;
    }
}
