package co.com.vidacruz.triage.mapper;

import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import java.time.Duration;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface PacienteMapper {

    /** Solo copia lo que viene del request; id, nivel, estado y fechas los completa el servicio. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "nombreCompleto", source = "nombreCompleto", qualifiedByName = "recortar")
    @Mapping(target = "quejaPrincipal", source = "quejaPrincipal", qualifiedByName = "recortar")
    Paciente toEntity(PacienteRequestDTO request);

    PacienteRegistradoResponseDTO toRegistradoResponse(Paciente paciente);

    @Mapping(target = "fechaEstadoActual", source = "paciente.fechaEstado")
    @Mapping(target = "tiempoEnEstadoActualMinutos", source = "paciente", qualifiedByName = "minutosEnEstado")
    @Mapping(target = "tiempoEnEstadoActualSegundos", source = "paciente", qualifiedByName = "segundosEnEstado")
    PacienteResponseDTO toResponse(Paciente paciente, @Context Instant ahora);

    @Named("recortar")
    default String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }

    @Named("minutosEnEstado")
    default long minutosEnEstado(Paciente paciente, @Context Instant ahora) {
        return tiempoEnEstado(paciente, ahora).toMinutes();
    }

    @Named("segundosEnEstado")
    default long segundosEnEstado(Paciente paciente, @Context Instant ahora) {
        return tiempoEnEstado(paciente, ahora).toSeconds();
    }

    private Duration tiempoEnEstado(Paciente paciente, Instant ahora) {
        Duration transcurrido = Duration.between(paciente.getFechaEstado(), ahora);
        return transcurrido.isNegative() ? Duration.ZERO : transcurrido;
    }
}
