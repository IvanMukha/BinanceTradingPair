package com.mukha.tradingpair.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAvgPriceResponse(
        BigDecimal price) {
}
