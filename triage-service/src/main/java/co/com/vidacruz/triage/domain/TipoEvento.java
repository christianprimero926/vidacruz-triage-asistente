package co.com.vidacruz.triage.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** El nombre en minúscula es el que se persiste y el que viaja en el evento de Kafka. */
@Getter
@RequiredArgsConstructor
public enum TipoEvento {
    PACIENTE_REGISTRADO("paciente_registrado"),
    PACIENTE_EN_CONSULTA("paciente_en_consulta"),
    PACIENTE_ALTA("paciente_alta"),
    PACIENTE_ESCALADO("paciente_escalado");

    private final String nombre;

    /** Evento que corresponde a entrar a un estado; REGISTRADO solo se alcanza al registrar. */
    public static TipoEvento paraEstado(EstadoPaciente estado) {
        return switch (estado) {
            case REGISTRADO -> PACIENTE_REGISTRADO;
            case EN_CONSULTA -> PACIENTE_EN_CONSULTA;
            case ALTA -> PACIENTE_ALTA;
        };
    }
}
