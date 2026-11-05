package com.mukha.tradingpair.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        @NotBlank String url,
        @NotBlank String symbol,
        @NotNull @DurationMin(seconds = 1) Duration pollInterval,
        @NotNull Duration rateLimitBackoff
) {
}