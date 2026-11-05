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
    @Valid
    private Jobs jobs = new Jobs();
    @Valid
    private Reporte reporte = new Reporte();

    @Valid
    private Alertas alertas = new Alertas();

    @Valid
    private Outbox outbox = new Outbox();

    @Data
    public static class Alertas {

        /** Vacía deja las alertas en FALLIDA con "webhook no configurado". La URL es secreta: no se registra. */
        private String webhookUrl;
        @Min(1)
        private int timeoutMs = 3000;
        @Min(1)
        private int maxIntentos = 5;
        /** Espera tras el primer fallo; se duplica en cada reintento. */
        @Min(1)
        private long esperaBaseMs = 30000;
    }

    @Data
    public static class Outbox {

        /** Rechazos permanentes de Kafka tolerados antes de descartar el registro; los transitorios no cuentan. */
        @Min(1)
        private int maxIntentos = 5;
    }

    @Data
    public static class Reporte {

        @Min(1)
        private int rangoMaximoDias = 31;
    }

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

    @Data
    public static class Jobs {

        /** En los tests se apaga y se llama a ejecutar() a mano. */
        private boolean enabled = true;
        @Min(1)
        private long outboxIntervaloMs = 500;
        @Min(1)
        private int outboxLote = 100;
        @Min(1)
        private long slaIntervaloMs = 15000;
        @Min(1)
        private int slaLote = 100;
        @Min(1)
        private long alertasIntervaloMs = 2000;
        @Min(1)
        private int alertasLote = 20;
        /** Tope de una corrida: si se cuelga, se cancela y el siguiente tick sigue. */
        @Min(1)
        private long timeoutCorridaMs = 60000;
    }
}
