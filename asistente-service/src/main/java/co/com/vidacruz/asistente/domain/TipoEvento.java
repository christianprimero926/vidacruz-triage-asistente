package co.com.vidacruz.asistente.domain;

import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Tipos del contrato de triaje.eventos; {@code activa} indica si el paciente queda en urgencias tras el evento. */
@Getter
@RequiredArgsConstructor
public enum TipoEvento {
    PACIENTE_REGISTRADO("paciente_registrado", true),
    PACIENTE_EN_CONSULTA("paciente_en_consulta", true),
    PACIENTE_ESCALADO("paciente_escalado", true),
    PACIENTE_ALTA("paciente_alta", false);

    private final String nombre;
    private final boolean activa;

    public static Optional<TipoEvento> deNombre(String nombre) {
        return Stream.of(values()).filter(tipo -> tipo.nombre.equals(nombre)).findFirst();
    }
}
