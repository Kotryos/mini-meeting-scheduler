package dev.kotryos.minischeduler;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    OpenAPI miniSchedulerApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("mini-meeting-scheduler")
                        .version("v1")
                        .description("Publish the hours you are free, then turn them into meetings."))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY))
                .components(new Components().addSecuritySchemes(API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")));
    }
}
