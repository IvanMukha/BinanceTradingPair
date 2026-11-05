package com.mukha.tradingpair.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient binanceRestClient(RestClient.Builder builder, BinanceProperties properties) {
        return builder.baseUrl(properties.url()).build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}