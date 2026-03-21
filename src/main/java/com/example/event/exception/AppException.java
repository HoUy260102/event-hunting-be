package com.example.event.exception;

import com.example.event.constant.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class AppException extends RuntimeException{
    private ErrorCode errorCode;
    private Map<String, String> details;
    private String message;
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, Map<String, String> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.message = message;
    }
}
