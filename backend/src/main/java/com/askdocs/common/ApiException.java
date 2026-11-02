package com.askdocs.common;

import org.springframework.http.HttpStatus;

/** An error with a message that is safe to show to the user. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
