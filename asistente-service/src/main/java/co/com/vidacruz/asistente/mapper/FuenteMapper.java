package co.com.vidacruz.asistente.mapper;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.dto.response.FuenteDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FuenteMapper {

    FuenteDTO aDto(FuenteCitada fuente);
}
