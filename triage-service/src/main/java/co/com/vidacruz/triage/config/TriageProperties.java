package co.com.vidacruz.triage.config;

import co.com.vidacruz.triage.domain.NivelTriage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "triage")
public class TriageProperties {

    @Valid
    private Sla sla = new Sla();

    @Data
    public static class Sla {

        @Min(1)
        private int criticoMinutos = 5;
        @Min(1)
        private int urgenteMinutos = 10;
        @Min(1)
        private int urgente2Minutos = 30;
        @Min(1)
        private int noUrgenteMinutos = 60;
        @Min(1)
        private int noUrgente2Minutos = 120;

        public int minutosPara(NivelTriage nivel) {
            return switch (nivel) {
                case CRITICO -> criticoMinutos;
                case URGENTE -> urgenteMinutos;
                case URGENTE_2 -> urgente2Minutos;
                case NO_URGENTE -> noUrgenteMinutos;
                case NO_URGENTE_2 -> noUrgente2Minutos;
            };
        }
    }
}
