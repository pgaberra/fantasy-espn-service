package com.fantasy.espn.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /**
     * Defines a stable OpenAPI document. The server URL is pinned to "/" (rather than
     * springdoc's request-derived default, which leaks the runtime port) so that the
     * generated spec is deterministic — see OpenApiSpecSnapshotTest.
     */
    @Bean
    public OpenAPI espnServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fantasy ESPN Service API")
                        .version("v1")
                        .description("Integrates with the ESPN Fantasy v3 API: reads a user's fantasy "
                                + "hockey league settings (and stores the espn_s2 / SWID cookies needed "
                                + "for private leagues) for the BFF."))
                .servers(List.of(new Server().url("/")));
    }
}
