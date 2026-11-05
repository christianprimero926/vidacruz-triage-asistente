package co.com.vidacruz.triage.service.impl;

import static co.com.vidacruz.triage.utilities.Constants.MSG_ALERTA_TEXTO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_WEBHOOK_NO_CONFIGURADO;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.AlertaNotificacion;
import co.com.vidacruz.triage.exception.CanalAlertaException;
import co.com.vidacruz.triage.service.CanalAlerta;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Envía un JSON con la clave "text", que es la que aceptan tal cual Slack y, vía Workflow, Teams.
 * La URL del webhook es un secreto: nunca se registra en logs ni en el último error.
 */
@Slf4j
@Component
public class WebhookCanalAlerta implements CanalAlerta {

    private final URI url;
    private final Duration timeout;
    private final WebClient webClient;

    public WebhookCanalAlerta(WebClient.Builder builder, TriageProperties properties) {
        TriageProperties.Alertas alertas = properties.getAlertas();
        this.url = StringUtils.hasText(alertas.getWebhookUrl()) ? URI.create(alertas.getWebhookUrl().trim()) : null;
        this.timeout = Duration.ofMillis(alertas.getTimeoutMs());
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, alertas.getTimeoutMs());
        this.webClient = builder.clone().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    @Override
    public Mono<Void> enviar(AlertaNotificacion notificacion) {
        if (url == null) {
            return Mono.error(CanalAlertaException.noConfigurado(MSG_WEBHOOK_NO_CONFIGURADO));
        }
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo(notificacion))
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .then()
                .onErrorMap(error -> new CanalAlertaException(describir(error), true, error));
    }

    private Map<String, Object> cuerpo(AlertaNotificacion n) {
        return Map.of(
                "text", String.format(MSG_ALERTA_TEXTO, n.pacienteId(), n.nivel(), n.slaMinutos(), n.minutosEspera()),
                "alertaId", n.alertaId(),
                "pacienteId", n.pacienteId(),
                "nivel", n.nivel(),
                "slaMinutos", n.slaMinutos(),
                "minutosEspera", n.minutosEspera(),
                "fechaEscalamiento", n.fechaEscalamiento());
    }

    private String describir(Throwable error) {
        if (error instanceof WebClientResponseException respuesta) {
            return "HTTP " + respuesta.getStatusCode().value();
        }
        if (error instanceof TimeoutException) {
            return "timeout tras " + timeout.toMillis() + " ms";
        }
        return error.getClass().getSimpleName();
    }
}
