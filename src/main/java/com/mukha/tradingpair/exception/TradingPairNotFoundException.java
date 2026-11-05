package com.mukha.tradingpair.exception;

import org.springframework.http.HttpStatus;

public class TradingPairNotFoundException extends GlobalServiceException {
    public TradingPairNotFoundException(String symbol) {
        super("Trading pair with symbol: " + symbol + " not found", HttpStatus.NOT_FOUND);
    }
}
