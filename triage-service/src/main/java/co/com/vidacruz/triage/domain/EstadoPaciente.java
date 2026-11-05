package co.com.vidacruz.triage.domain;

import java.util.Set;

public enum EstadoPaciente {
    REGISTRADO,
    EN_CONSULTA,
    ALTA;

    public Set<EstadoPaciente> transicionesValidas() {
        return switch (this) {
            case REGISTRADO -> Set.of(EN_CONSULTA, ALTA);
            case EN_CONSULTA -> Set.of(ALTA);
            case ALTA -> Set.of();
        };
    }

    public boolean puedePasarA(EstadoPaciente destino) {
        return transicionesValidas().contains(destino);
    }
}
