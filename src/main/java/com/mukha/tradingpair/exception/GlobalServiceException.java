package com.mukha.tradingpair.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GlobalServiceException extends RuntimeException {
    private final HttpStatus statusCode;

    public GlobalServiceException(String message, HttpStatus statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
