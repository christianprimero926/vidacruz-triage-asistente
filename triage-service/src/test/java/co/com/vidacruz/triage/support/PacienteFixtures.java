package co.com.vidacruz.triage.support;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

/** Pacientes de ejemplo del enunciado (E1 a E6) y una base sana que los tests modifican campo a campo. */
@UtilityClass
public class PacienteFixtures {

    /** Signos normales: se clasifica NO_URGENTE con una queja no administrativa. */
    public static PacienteRequestDTO.PacienteRequestDTOBuilder base() {
        return PacienteRequestDTO.builder()
                .nombreCompleto("Paciente de Prueba")
                .edad(40)
                .frecuenciaCardiaca(72)
                .presionSistolica(120)
                .presionDiastolica(80)
                .spo2(99)
                .temperatura(new BigDecimal("36.5"))
                .dolor(2)
                .gcs(15)
                .quejaPrincipal("Herida por cortar");
    }

    public static PacienteRequestDTO e1() {
        return paciente(130, 180, 110, 97, "36.8", 8, 15, "Dolor torácico");
    }

    public static PacienteRequestDTO e2() {
        return paciente(95, 150, 95, 91, "37.0", 4, 15, "Dificultad para respirar");
    }

    public static PacienteRequestDTO e3() {
        return paciente(88, 130, 85, 98, "38.2", 6, 15, "Fiebre");
    }

    public static PacienteRequestDTO e4() {
        return paciente(72, 120, 80, 99, "36.5", 2, 15, "Herida por cortar");
    }

    public static PacienteRequestDTO e5() {
        return paciente(70, 115, 75, 99, "36.4", 0, 15, "Renovación de recetario");
    }

    public static PacienteRequestDTO e6() {
        return paciente(35, 80, 50, 85, "35.9", 0, 6, "Inconsciente");
    }

    private static PacienteRequestDTO paciente(int frecuencia, int sistolica, int diastolica, int spo2,
                                               String temperatura, int dolor, int gcs, String queja) {
        return base()
                .frecuenciaCardiaca(frecuencia)
                .presionSistolica(sistolica)
                .presionDiastolica(diastolica)
                .spo2(spo2)
                .temperatura(new BigDecimal(temperatura))
                .dolor(dolor)
                .gcs(gcs)
                .quejaPrincipal(queja)
                .build();
    }
}
