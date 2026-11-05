package com.mukha.tradingpair.scheduler;

import com.mukha.tradingpair.client.BinanceClient;
import com.mukha.tradingpair.config.BinanceProperties;
import com.mukha.tradingpair.service.TradingPairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BinancePriceScheduler {

    private final BinanceClient binanceClient;
    private final BinanceProperties properties;
    private final TradingPairService tradingPairService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${binance.poll-interval}")
    public void fetchPriceJob() {
        String symbol = properties.symbol();

        binanceClient.getCurrentAvgPrice(symbol).ifPresent(response -> {
            Instant fetchedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            log.info("Price fetched for {}: {}", symbol, response.price());
            try {
                tradingPairService.save(symbol, response.price(), fetchedAt);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate record skipped, symbol: {}, fetchedAt: {}", symbol, fetchedAt);
            } catch (Exception e) {
                log.error("Failed to save price, symbol: {}, price: {}, fetchedAt: {}",
                        symbol, response.price(), fetchedAt, e);
            }
        });
    }
}