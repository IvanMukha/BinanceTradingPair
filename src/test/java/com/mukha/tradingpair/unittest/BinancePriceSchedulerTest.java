package com.mukha.tradingpair.unittest;

import com.mukha.tradingpair.client.BinanceClient;
import com.mukha.tradingpair.config.BinanceProperties;
import com.mukha.tradingpair.dto.response.BinanceAvgPriceResponse;
import com.mukha.tradingpair.scheduler.BinancePriceScheduler;
import com.mukha.tradingpair.service.TradingPairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinancePriceSchedulerTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final BigDecimal PRICE = new BigDecimal("612.34500000");
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00.987654Z");

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private TradingPairService tradingPairService;

    private BinancePriceScheduler scheduler;

    @BeforeEach
    void setUp() {
        BinanceProperties properties = new BinanceProperties(
                "http://localhost",
                SYMBOL,
                Duration.ofSeconds(30),
                Duration.ofMinutes(1));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new BinancePriceScheduler(binanceClient, properties, tradingPairService, clock);
    }

    @Test
    void fetchPriceJob_shouldSavePriceWithFetchTime() {
        when(binanceClient.getCurrentAvgPrice(SYMBOL))
                .thenReturn(Optional.of(new BinanceAvgPriceResponse(PRICE)));

        scheduler.fetchPriceJob();

        verify(tradingPairService).save(SYMBOL, PRICE, Instant.parse("2026-09-10T12:00:00Z"));
    }

    @Test
    void fetchPriceJob_shouldNotSave_whenBinanceReturnsNothing() {
        when(binanceClient.getCurrentAvgPrice(SYMBOL)).thenReturn(Optional.empty());

        scheduler.fetchPriceJob();

        verifyNoInteractions(tradingPairService);
    }

    @Test
    void fetchPriceJob_shouldIgnoreSaveFailure() {
        when(binanceClient.getCurrentAvgPrice(SYMBOL))
                .thenReturn(Optional.of(new BinanceAvgPriceResponse(PRICE)));
        doThrow(new IllegalStateException("db is down"))
                .when(tradingPairService).save(any(), any(), any());

        assertThatCode(scheduler::fetchPriceJob).doesNotThrowAnyException();
    }

    @Test
    void fetchPriceJob_shouldSkipDuplicatesCorrectly() {
        when(binanceClient.getCurrentAvgPrice(SYMBOL))
                .thenReturn(Optional.of(new BinanceAvgPriceResponse(PRICE)));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(tradingPairService).save(any(), any(), any());

        assertThatCode(scheduler::fetchPriceJob).doesNotThrowAnyException();
    }

    @Test
    void fetchPriceJob_shouldWorkingAfterFailedTick() {
        when(binanceClient.getCurrentAvgPrice(SYMBOL))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new BinanceAvgPriceResponse(PRICE)));

        scheduler.fetchPriceJob();
        scheduler.fetchPriceJob();

        verify(binanceClient, times(2)).getCurrentAvgPrice(SYMBOL);
        verify(tradingPairService, times(1)).save(SYMBOL, PRICE, Instant.parse("2026-09-10T12:00:00Z"));
    }
}