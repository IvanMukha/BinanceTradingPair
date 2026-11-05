package com.mukha.tradingpair.service;

import com.mukha.tradingpair.dto.response.AveragePriceResponse;
import com.mukha.tradingpair.dto.response.TradingPairResponse;
import com.mukha.tradingpair.dto.response.TrendResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface TradingPairService {

    void save(String symbol, BigDecimal price, Instant fetchedAt);

    TradingPairResponse getLastFetched(String symbol);

    AveragePriceResponse getAveragePriceForPeriod(String symbol, Instant from, Instant to);

    Page<TradingPairResponse> getAllWithinRange(String symbol, Instant fetchedFrom, Instant fetchedTo, Pageable pageable);

    TrendResponse getTrend(String symbol, Instant from, Instant to);
}
