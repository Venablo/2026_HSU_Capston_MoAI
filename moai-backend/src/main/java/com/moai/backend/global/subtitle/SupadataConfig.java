package com.moai.backend.global.subtitle;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Supadata Transcript API 전용 WebClient 빈.
 * subtitle.provider=supadata 일 때만 활성화된다.
 *
 * BASE_URL 은 Supadata 의 호스트만 지정하고, 경로(/v1/transcript)는 호출부에서 추가한다.
 */
@Configuration
@ConditionalOnProperty(name = "subtitle.provider", havingValue = "supadata")
public class SupadataConfig {

    private static final String BASE_URL = "https://api.supadata.ai";

    @Bean
    public WebClient supadataWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }
}
