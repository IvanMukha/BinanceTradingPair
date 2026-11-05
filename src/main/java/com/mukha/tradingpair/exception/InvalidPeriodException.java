package com.mukha.tradingpair.exception;

import org.springframework.http.HttpStatus;

public class InvalidPeriodException extends GlobalServiceException {
    public InvalidPeriodException() {
        super("Parameter 'from' must be before parameter 'to'", HttpStatus.BAD_REQUEST);
    }
}
