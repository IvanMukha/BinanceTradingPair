package com.mukha.tradingpair.client;

import com.mukha.tradingpair.config.BinanceProperties;
import com.mukha.tradingpair.dto.response.BinanceAvgPriceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class BinanceClient {

    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";

    private final RestClient binanceRestClient;
    private final BinanceProperties properties;
    private final Clock clock;

    private volatile Instant pausedUntil = Instant.MIN;

    public Optional<BinanceAvgPriceResponse> getCurrentAvgPrice(String symbol) {
        if (clock.instant().isBefore(pausedUntil)) {
            log.debug("Binance polling is paused until {}", pausedUntil);
            return Optional.empty();
        }

        try {
            BinanceAvgPriceResponse response = binanceRestClient.get()
                    .uri(uri -> uri.path(AVG_PRICE_PATH).queryParam("symbol", symbol).build())
                    .retrieve()
                    .body(BinanceAvgPriceResponse.class);
            return validate(symbol, response);
        } catch (HttpClientErrorException e) {
            handleClientError(symbol, e);
        } catch (RestClientException e) {
            log.warn("Failed to call Binance for {}: {}. Skipping this tick.", symbol, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BinanceAvgPriceResponse> validate(String symbol, BinanceAvgPriceResponse response) {
        if (response == null || response.price() == null || response.price().signum() <= 0) {
            log.warn("Binance returned an unusable response for {}: {}", symbol, response);
            return Optional.empty();
        }
        return Optional.of(response);
    }

    private void handleClientError(String symbol, HttpClientErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        if (isRateLimited(status)) {
            Duration backoff = parseRetryAfter(e.getResponseHeaders())
                    .orElse(properties.rateLimitBackoff());
            pausedUntil = clock.instant().plus(backoff);
            log.warn("Binance rate limit hit for {} (status {}). Polling paused until {}.",
                    symbol, status.value(), pausedUntil);
            return;
        }
        log.error("Binance rejected request for {}: status {}, body {}",
                symbol, status.value(), e.getResponseBodyAsString());
    }

    private boolean isRateLimited(HttpStatusCode status) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || status.isSameCodeAs(HttpStatus.I_AM_A_TEAPOT);
    }

    private Optional<Duration> parseRetryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        } catch (NumberFormatException ex) {
            log.warn("Unparseable Retry-After header: {}", value);
            return Optional.empty();
        }
    }
}