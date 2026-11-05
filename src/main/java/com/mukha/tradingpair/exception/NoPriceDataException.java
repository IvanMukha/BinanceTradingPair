package com.mukha.tradingpair.exception;

import org.springframework.http.HttpStatus;

public class NoPriceDataException extends GlobalServiceException {
    public NoPriceDataException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}