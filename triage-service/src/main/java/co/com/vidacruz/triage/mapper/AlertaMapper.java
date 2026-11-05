package co.com.vidacruz.triage.mapper;

import co.com.vidacruz.triage.domain.AlertaMedica;
import co.com.vidacruz.triage.dto.response.AlertaResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertaMapper {

    AlertaResponseDTO toResponse(AlertaMedica alerta);
}
