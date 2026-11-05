package com.mukha.tradingpair.service.impl;

import com.mukha.tradingpair.dto.response.AveragePriceResponse;
import com.mukha.tradingpair.dto.response.TradingPairResponse;
import com.mukha.tradingpair.dto.response.TrendResponse;
import com.mukha.tradingpair.exception.InvalidPeriodException;
import com.mukha.tradingpair.exception.NoPriceDataException;
import com.mukha.tradingpair.exception.TradingPairNotFoundException;
import com.mukha.tradingpair.mapper.TradingPairMapper;
import com.mukha.tradingpair.model.TradingPair;
import com.mukha.tradingpair.model.trend.TradingPairTrendType;
import com.mukha.tradingpair.repository.TradingPairRepository;
import com.mukha.tradingpair.service.TradingPairService;
import com.mukha.tradingpair.specification.TradingPairSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingPairServiceImpl implements TradingPairService {

    private static final int PRICE_SCALE = 10;
    private static final int MIN_POINTS_FOR_TREND = 2;
    private static final BigDecimal FLAT_THRESHOLD_PERCENT = new BigDecimal("0.0005");

    private final TradingPairRepository tradingPairRepository;
    private final TradingPairMapper mapper;

    @Override
    @Transactional
    public void save(String symbol, BigDecimal price, Instant fetchedAt) {
        log.info("Attempting save price data for symbol: {}", symbol);
        TradingPair tradingPair = new TradingPair();
        tradingPair.setSymbol(symbol);
        tradingPair.setPrice(price);
        tradingPair.setFetchedAt(fetchedAt);
        tradingPairRepository.save(tradingPair);
        log.info("Successfully saved price data for symbol: {}", symbol);
    }

    @Override
    public TradingPairResponse getLastFetched(String symbol) {
        log.debug("Fetching trading pair by symbol: {}", symbol);

        TradingPair foundPair = tradingPairRepository.findFirstBySymbolOrderByFetchedAtDesc(symbol)
                .orElseThrow(() -> {
                    log.warn("Trading pair with symbol: {} not found", symbol);
                    return new TradingPairNotFoundException(symbol);
                });
        return mapper.toResponse(foundPair);
    }

    @Override
    public AveragePriceResponse getAveragePriceForPeriod(String symbol, Instant fetchedFrom, Instant fetchedTo) {
        log.debug("Fetching average price. symbol: {}, from: {}, to: {}", symbol, fetchedFrom, fetchedTo);

        validatePeriod(fetchedFrom, fetchedTo);

        BigDecimal avgPrice = tradingPairRepository.getAveragePrice(symbol, fetchedFrom, fetchedTo);
        if (avgPrice == null) {
            throw new NoPriceDataException("No price data for " + symbol
                    + " between " + fetchedFrom + " and " + fetchedTo);
        }
        return new AveragePriceResponse(avgPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public Page<TradingPairResponse> getAllWithinRange(String symbol, Instant fetchedFrom, Instant fetchedTo, Pageable pageable) {
        log.debug("Fetching pageable trading pairs. symbol: {}, from: {}, to: {}", symbol, fetchedFrom, fetchedTo);

        validatePeriod(fetchedFrom, fetchedTo);

        Specification<TradingPair> spec = TradingPairSpecification.fetchedWithinRange(symbol, fetchedFrom, fetchedTo);
        return tradingPairRepository.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Override
    public TrendResponse getTrend(String symbol, Instant fetchedFrom, Instant fetchedTo) {
        log.debug("Calculating price trend for symbol: {} from {} to {}", symbol, fetchedFrom, fetchedTo);

        validatePeriod(fetchedFrom, fetchedTo);

        List<TradingPair> dataPoints = tradingPairRepository
                .findAllBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(symbol, fetchedFrom, fetchedTo);

        if (dataPoints.size() < MIN_POINTS_FOR_TREND) {
            throw new NoPriceDataException("Not enough data to calculate trend for " + symbol
                    + " between " + fetchedFrom + " and " + fetchedTo
                    + ": at least " + MIN_POINTS_FOR_TREND + " records required");
        }

        int mid = dataPoints.size() / 2;
        BigDecimal firstHalfAverage = averageOf(dataPoints.subList(0, mid));
        BigDecimal secondHalfAverage = averageOf(dataPoints.subList(mid, dataPoints.size()));


        BigDecimal difference = secondHalfAverage.subtract(firstHalfAverage).abs();

        BigDecimal maxFlatDifference = firstHalfAverage.multiply(FLAT_THRESHOLD_PERCENT);

        TradingPairTrendType trend;
        if (difference.compareTo(maxFlatDifference) <= 0) {
            trend = TradingPairTrendType.FLAT;
        } else if (secondHalfAverage.compareTo(firstHalfAverage) > 0) {
            trend = TradingPairTrendType.UPWARD;
        } else {
            trend = TradingPairTrendType.DOWNWARD;
        }

        return new TrendResponse(symbol, fetchedFrom, fetchedTo, trend);
    }

    private BigDecimal averageOf(List<TradingPair> points) {
        BigDecimal sum = points.stream()
                .map(TradingPair::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(points.size()), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private void validatePeriod(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidPeriodException();
        }
    }
}

