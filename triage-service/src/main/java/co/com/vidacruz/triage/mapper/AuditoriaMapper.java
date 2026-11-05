package co.com.vidacruz.triage.mapper;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.dto.response.AuditoriaResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditoriaMapper {

    AuditoriaResponseDTO toResponse(AuditoriaClinica auditoria);
}
