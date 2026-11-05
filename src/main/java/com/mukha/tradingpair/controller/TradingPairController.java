package com.mukha.tradingpair.controller;

import com.mukha.tradingpair.config.BinanceProperties;
import com.mukha.tradingpair.dto.response.AveragePriceResponse;
import com.mukha.tradingpair.dto.response.PageResponse;
import com.mukha.tradingpair.dto.response.TradingPairResponse;
import com.mukha.tradingpair.dto.response.TrendResponse;
import com.mukha.tradingpair.model.TradingPair;
import com.mukha.tradingpair.service.TradingPairService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prices")
public class TradingPairController {

    private static final String SYMBOL_REGEX = "^[A-Z0-9]{5,20}$";
    private static final String SYMBOL_MESSAGE = "symbol must be 5-20 uppercase letters or digits, e.g. BTCUSDT";

    private final TradingPairService tradingPairService;
    private final BinanceProperties properties;

    @GetMapping("/latest")
    public TradingPairResponse getLatest(
            @RequestParam(required = false) @Pattern(regexp = SYMBOL_REGEX, message = SYMBOL_MESSAGE) String symbol) {
        return tradingPairService.getLastFetched(resolveSymbol(symbol));
    }

    @GetMapping("/average")
    public AveragePriceResponse getAveragePrice(
            @RequestParam(required = false) @Pattern(regexp = SYMBOL_REGEX, message = SYMBOL_MESSAGE) String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return tradingPairService.getAveragePriceForPeriod(resolveSymbol(symbol), from, to);
    }

    @GetMapping
    public PageResponse<TradingPairResponse> getAllWithinRange(
            @RequestParam(required = false) @Pattern(regexp = SYMBOL_REGEX, message = SYMBOL_MESSAGE) String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TradingPair.Fields.fetchedAt));
        return PageResponse.from(tradingPairService.getAllWithinRange(resolveSymbol(symbol), from, to, pageable));
    }

    @GetMapping("/trend")
    public TrendResponse getTrend(
            @RequestParam(required = false) @Pattern(regexp = SYMBOL_REGEX, message = SYMBOL_MESSAGE) String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return tradingPairService.getTrend(resolveSymbol(symbol), from, to);
    }

    private String resolveSymbol(String symbol) {
        return symbol != null ? symbol : properties.symbol();
    }
}
