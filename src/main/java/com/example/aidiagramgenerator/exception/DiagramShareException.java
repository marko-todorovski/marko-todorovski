package com.example.aidiagramgenerator.exception;

import org.springframework.http.HttpStatus;

public class DiagramShareException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DiagramShareException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
