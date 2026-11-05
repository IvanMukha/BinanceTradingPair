package com.mukha.tradingpair.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;

public record TradingPairResponse(
        String symbol,

        @JsonFormat(shape = STRING)
        BigDecimal price,

        Instant fetchedAt) {
}
