package co.com.vidacruz.triage.service;

import static co.com.vidacruz.triage.utilities.Constants.QUEJAS_ADMINISTRATIVAS;

import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.utilities.TextNormalizer;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** RN-01. Puro y sin I/O: se evalúa de mayor a menor gravedad y rige el primer nivel que se cumpla. */
@Component
public class TriageClassifier {

    private static final BigDecimal TEMPERATURA_URGENTE = new BigDecimal("38.5");
    private static final BigDecimal TEMPERATURA_URGENTE_2 = new BigDecimal("38.0");

    public NivelTriage clasificar(PacienteRequestDTO paciente) {
        if (esCritico(paciente)) {
            return NivelTriage.CRITICO;
        }
        if (esUrgente(paciente)) {
            return NivelTriage.URGENTE;
        }
        if (esUrgente2(paciente)) {
            return NivelTriage.URGENTE_2;
        }
        return esQuejaAdministrativa(paciente.getQuejaPrincipal())
                ? NivelTriage.NO_URGENTE_2
                : NivelTriage.NO_URGENTE;
    }

    private boolean esCritico(PacienteRequestDTO paciente) {
        int frecuencia = paciente.getFrecuenciaCardiaca();
        return frecuencia < 40
                || frecuencia > 120
                || paciente.getSpo2() < 90
                || paciente.getPresionSistolica() < 90
                || paciente.getGcs() <= 8;
    }

    private boolean esUrgente(PacienteRequestDTO paciente) {
        return entre(paciente.getSpo2(), 90, 93)
                || entre(paciente.getPresionSistolica(), 90, 100)
                || paciente.getTemperatura().compareTo(TEMPERATURA_URGENTE) >= 0
                || entre(paciente.getGcs(), 9, 13);
    }

    private boolean esUrgente2(PacienteRequestDTO paciente) {
        BigDecimal temperatura = paciente.getTemperatura();
        return entre(paciente.getFrecuenciaCardiaca(), 90, 120)
                || (temperatura.compareTo(TEMPERATURA_URGENTE_2) >= 0 && temperatura.compareTo(TEMPERATURA_URGENTE) < 0)
                || paciente.getDolor() >= 7;
    }

    private boolean esQuejaAdministrativa(String queja) {
        return QUEJAS_ADMINISTRATIVAS.contains(TextNormalizer.normalizar(queja));
    }

    private boolean entre(int valor, int minimo, int maximo) {
        return valor >= minimo && valor <= maximo;
    }
}
