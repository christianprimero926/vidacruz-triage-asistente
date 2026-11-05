package co.com.vidacruz.asistente.domain;

import java.util.Optional;
import java.util.stream.Stream;

public enum NivelTriage {
    CRITICO, URGENTE, URGENTE_2, NO_URGENTE, NO_URGENTE_2;

    public static Optional<NivelTriage> deNombre(String nombre) {
        return Stream.of(values()).filter(nivel -> nivel.name().equals(nombre)).findFirst();
    }
}
