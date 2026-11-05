package co.com.vidacruz.triage.config;

import static co.com.vidacruz.triage.utilities.Constants.ESQUEMA_API_KEY;
import static co.com.vidacruz.triage.utilities.Constants.HEADER_API_KEY;

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
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de Triage - VidaCruz")
                        .version("1.0.0")
                        .description("Registro y clasificación de pacientes, SLA, escalamiento, auditoría y reportes"))
                .components(new Components().addSecuritySchemes(ESQUEMA_API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(HEADER_API_KEY)))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_API_KEY));
    }
}
