package co.com.vidacruz.asistente.repository;

import static co.com.vidacruz.asistente.utilities.Constants.CLAVE_INICIALIZADO;
import static co.com.vidacruz.asistente.utilities.Constants.PREFIJO_CLAVE_ACTIVOS;

import co.com.vidacruz.asistente.domain.NivelTriage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Vista de pacientes activos: un set de Redis por nivel, de modo que reaplicar un evento no cambia nada. */
@Repository
@RequiredArgsConstructor
public class UrgenciasRepository {

    private final ReactiveStringRedisTemplate redis;

    public Mono<Void> agregar(NivelTriage nivel, String pacienteId) {
        return redis.opsForSet().add(clave(nivel), pacienteId).then();
    }

    public Mono<Void> quitar(NivelTriage nivel, String pacienteId) {
        return redis.opsForSet().remove(clave(nivel), pacienteId).then();
    }

    public Mono<Map<NivelTriage, Long>> contarPorNivel() {
        return Flux.fromArray(NivelTriage.values())
                .concatMap(nivel -> redis.opsForSet().size(clave(nivel)).map(total -> Map.entry(nivel, total)))
                .collect(LinkedHashMap::new, (mapa, entrada) -> mapa.put(entrada.getKey(), entrada.getValue()));
    }

    public Mono<Boolean> inicializado() {
        return redis.hasKey(CLAVE_INICIALIZADO);
    }

    public Mono<Void> marcarInicializado() {
        return redis.opsForValue().set(CLAVE_INICIALIZADO, "1").then();
    }

    private static String clave(NivelTriage nivel) {
        return PREFIJO_CLAVE_ACTIVOS + nivel.name();
    }
}
