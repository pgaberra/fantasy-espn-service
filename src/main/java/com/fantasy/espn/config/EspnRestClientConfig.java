package com.fantasy.espn.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class EspnRestClientConfig {

    /** RestClient for the ESPN Fantasy v3 API (league settings + teams). */
    @Bean
    public RestClient espnApiRestClient(EspnProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(props.apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * RestClient for ESPN's image CDN, used only to ask whether a player's headshot exists.
     * Short timeouts: it is a courtesy check over the whole pool, and one slow answer must not
     * hold up a sync.
     */
    @Bean
    public RestClient espnImageRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
