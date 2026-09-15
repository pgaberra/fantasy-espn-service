package com.fantasy.espn.player;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped configuration keeps avatars off, so a deployment that sets nothing never has the
 * sync asking ESPN's CDN about photographs we hold no licence to show.
 *
 * <p>Read straight from {@code src/main/resources/application.yaml}: the tests run on their own
 * {@code application.yaml}, which would hide a changed default from any context-loading test.
 */
class PlayerAvatarsDefaultTest {

    @Test
    void avatarsAreOffWhenNothingIsSet() throws Exception {
        List<PropertySource<?>> shipped = new YamlPropertySourceLoader()
                .load("shipped", new FileSystemResource("src/main/resources/application.yaml"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        shipped.forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("players.avatars.enabled", Boolean.class)).isFalse();
    }
}
