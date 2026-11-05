package com.mukha.tradingpair.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mukha.tradingpair.model.trend.TradingPairTrendType;

import java.time.Instant;

public record TrendResponse(
        String symbol,

        @JsonProperty("start_date")
        Instant startDate,

        @JsonProperty("end_date")
        Instant endDate,

        TradingPairTrendType trend) {
}
