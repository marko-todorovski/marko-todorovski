package com.example.aidiagramgenerator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .defaultHeader("User-Agent", "AIDiagramGenerator/1.0");
    }

    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .defaultHeader("User-Agent", "AIDiagramGenerator/1.0");
    }

    @Bean("openAiWebClient")
    public WebClient openAiWebClient(
            @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("User-Agent", "AIDiagramGenerator/1.0")
                .build();
    }
}
