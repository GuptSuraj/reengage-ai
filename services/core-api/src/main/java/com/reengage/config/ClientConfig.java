package com.reengage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ClientConfig {
    @Bean
    RestClient aiRestClient(@Value("${app.ai-base-url}") String baseUrl) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
