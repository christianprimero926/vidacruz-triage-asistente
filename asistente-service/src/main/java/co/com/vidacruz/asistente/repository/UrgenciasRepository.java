package co.com.vidacruz.asistente.repository;

import static co.com.vidacruz.asistente.utilities.Constants.CLAVE_INICIALIZADO;
import static co.com.vidacruz.asistente.utilities.Constants.PREFIJO_CLAVE_ACTIVOS;

import co.com.vidacruz.asistente.domain.NivelTriage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Vista de pacientes activos: un set de Redis por nivel, de modo que reaplicar un evento no cambia nada. */
@Repository
public class UrgenciasRepository {

    private final ReactiveStringRedisTemplate redis;
    private final Scheduler redisScheduler;

    public UrgenciasRepository(ReactiveStringRedisTemplate redis, @Qualifier("redisScheduler") Scheduler redisScheduler) {
        this.redis = redis;
        this.redisScheduler = redisScheduler;
    }

    public Mono<Void> agregar(NivelTriage nivel, String pacienteId) {
        return Mono.defer(() -> redis.opsForSet().add(clave(nivel), pacienteId).then())
                .subscribeOn(redisScheduler);
    }

    public Mono<Void> quitar(NivelTriage nivel, String pacienteId) {
        return Mono.defer(() -> redis.opsForSet().remove(clave(nivel), pacienteId).then())
                .subscribeOn(redisScheduler);
    }

    public Mono<Map<NivelTriage, Long>> contarPorNivel() {
        return Flux.fromArray(NivelTriage.values())
                .concatMap(nivel -> redis.opsForSet().size(clave(nivel)).map(total -> Map.entry(nivel, total)))
                .<Map<NivelTriage, Long>>collect(LinkedHashMap::new,
                        (mapa, entrada) -> mapa.put(entrada.getKey(), entrada.getValue()))
                .subscribeOn(redisScheduler);
    }

    /** Particiones cuya vista ya se reconstruyó por completo (una marca por partición). */
    public Mono<Set<Integer>> particionesInicializadas() {
        return redis.scan(ScanOptions.scanOptions().match(CLAVE_INICIALIZADO + ":*").build())
                .map(clave -> Integer.parseInt(clave.substring(CLAVE_INICIALIZADO.length() + 1)))
                .collect(Collectors.<Integer>toSet())
                .subscribeOn(redisScheduler);
    }

    public Mono<Void> marcarParticion(int particion) {
        return Mono.defer(() -> redis.opsForValue().set(CLAVE_INICIALIZADO + ":" + particion, "1").then())
                .subscribeOn(redisScheduler);
    }

    private static String clave(NivelTriage nivel) {
        return PREFIJO_CLAVE_ACTIVOS + nivel.name();
    }
}
