package com.banking.api.exception;

import org.springframework.http.HttpStatus;

/**
 * A business error that knows both its error code and the HTTP status to report.
 * Defaults to 400 Bad Request when no status is supplied.
 */
public class ApiException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus status;

    public ApiException(String errorCode, String errorMessage) {
        this(errorCode, errorMessage, HttpStatus.BAD_REQUEST);
    }

    public ApiException(String errorCode, String errorMessage, HttpStatus status) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
