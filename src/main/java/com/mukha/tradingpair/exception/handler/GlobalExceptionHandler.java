package com.mukha.tradingpair.exception.handler;

import com.mukha.tradingpair.exception.GlobalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_FIELDS = "invalid_fields";
    private static final String VALIDATION_DETAIL = "One or more parameters are not valid";

    @ExceptionHandler(GlobalServiceException.class)
    public ProblemDetail handleServiceException(GlobalServiceException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getMessage());
    }

    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String field = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
            result.getResolvableErrors().forEach(error -> errors.put(field, error.getDefaultMessage()));
        });
        return validationResponse(ex, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return validationResponse(ex, errors, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<Object> validationResponse(
            Exception ex, Map<String, String> errors, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = createProblemDetail(ex, status, VALIDATION_DETAIL, null, null, request);
        problem.setProperty(INVALID_FIELDS, errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}