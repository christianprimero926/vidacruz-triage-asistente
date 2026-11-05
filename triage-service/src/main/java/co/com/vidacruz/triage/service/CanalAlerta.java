package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.domain.AlertaNotificacion;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida de las alertas: cada canal (webhook, correo, SMS...) es un adaptador.
 * Un fallo se señala con CanalAlertaException; completar sin error significa entregada.
 */
public interface CanalAlerta {

    Mono<Void> enviar(AlertaNotificacion notificacion);
}
