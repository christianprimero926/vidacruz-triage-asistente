package co.com.vidacruz.asistente.config;

import static co.com.vidacruz.asistente.utilities.Constants.ESQUEMA_API_KEY;
import static co.com.vidacruz.asistente.utilities.Constants.HEADER_API_KEY;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        SecurityScheme esquema = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(HEADER_API_KEY)
                .description("API key del servicio (variable ASISTENTE_API_KEY)");
        return new OpenAPI()
                .info(new Info().title("Asistente clínico VidaCruz")
                        .description("Consulta de protocolos clínicos con RAG y estado de urgencias en tiempo real")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(ESQUEMA_API_KEY, esquema))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_API_KEY));
    }
}
