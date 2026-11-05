package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.exception.BusinessException;
import co.com.vidacruz.asistente.exception.ErrorType;
import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstadoUrgenciasServiceImpl")
class EstadoUrgenciasServiceImplTest {

    @Mock
    private UrgenciasRepository repository;
    @InjectMocks
    private EstadoUrgenciasServiceImpl service;

    @Test
    void shouldReturnAllFiveLevelsAndTotal_WhenRedisResponds() {
        Map<NivelTriage, Long> conteo = new LinkedHashMap<>();
        conteo.put(NivelTriage.CRITICO, 2L);
        conteo.put(NivelTriage.URGENTE, 0L);
        conteo.put(NivelTriage.URGENTE_2, 3L);
        conteo.put(NivelTriage.NO_URGENTE, 1L);
        conteo.put(NivelTriage.NO_URGENTE_2, 0L);
        when(repository.contarPorNivel()).thenReturn(Mono.just(conteo));

        StepVerifier.create(service.consultar())
                .assertNext(estado -> {
                    assertThat(estado.getNiveles()).containsExactly(
                            Map.entry("CRITICO", 2L), Map.entry("URGENTE", 0L), Map.entry("URGENTE_2", 3L),
                            Map.entry("NO_URGENTE", 1L), Map.entry("NO_URGENTE_2", 0L));
                    assertThat(estado.getTotalActivos()).isEqualTo(6L);
                })
                .verifyComplete();
    }

    @Test
    void shouldFailAsServiceUnavailable_WhenRedisFails() {
        when(repository.contarPorNivel()).thenReturn(Mono.error(new IllegalStateException("redis caído")));

        StepVerifier.create(service.consultar())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getType()).isEqualTo(ErrorType.SERVICIO_NO_DISPONIBLE);
                    assertThat(((BusinessException) error).getCodigo()).isEqualTo("ESTADO_URGENCIAS_NO_DISPONIBLE");
                })
                .verify();
    }
}
