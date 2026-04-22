package com.example.event.exception;

import com.example.event.constant.ErrorCode;
import com.example.event.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalException {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handlerAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = ErrorResponse.builder()
                .code(errorCode.name())
                .message((exception.getMessage() != null && !exception.getMessage().isEmpty())
                        ? exception.getMessage()
                        : errorCode.getMessage())
                .details(exception.getDetails())
                .status(errorCode.getHttpStatus().value())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(errorCode.getHttpStatus().value()).body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            ObjectOptimisticLockingFailureException exception) {

        ErrorResponse response = ErrorResponse.builder()
                .code("OPTIMISTIC_LOCK_ERROR")
                .status(HttpStatus.CONFLICT.value())
                .message("Dữ liệu vừa có ai đó được cập nhật trước, vui lòng tải lại và thử lại.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handlerBadCredentialsExceptionException(BadCredentialsException exception) {
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Tài khoản đăng nhập hoặc mật khẩu không chính xác!")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handlerJwtAuthenticationException(JwtAuthenticationException exception) {
        ErrorResponse response = ErrorResponse.builder()
                .code(exception.getErrorCode().name())
                .status(HttpStatus.BAD_REQUEST.value())
                .message(exception.getErrorCode().getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
    }

//    @ExceptionHandler(HttpMessageNotReadableException.class)
//    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
//        String message = "Dữ liệu không hợp lệ.";
//        String code = "INVALID_JSON_FORMAT";
//        Throwable rootCause = ex.getMostSpecificCause();
//        if (rootCause instanceof InvalidFormatException invalidEx) {
//            if (invalidEx.getTargetType().isEnum()) {
//                message = String.format("Giá trị '%s' không hợp lệ. Phải là một trong các giá trị: %s",
//                        invalidEx.getValue(),
//                        Arrays.toString(invalidEx.getTargetType().getEnumConstants()));
//                code = "INVALID_ENUM_VALUE";
//            }
//        }
//        ErrorResponse response = ErrorResponse.builder()
//                .code(code)
//                .status(HttpStatus.BAD_REQUEST.value())
//                .message(message)
//                .timestamp(LocalDateTime.now())
//                .build();
//        return ResponseEntity.badRequest().body(response);
//    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handlerException(Exception exception) {
        exception.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(exception.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        Map<String, String> details = new HashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                details.put(fieldError.getField(), fieldError.getDefaultMessage())
        );

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code("VALIDATION_ERROR")
                .message("Dữ liệu không hợp lệ")
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Bạn không có quyền truy cập tài nguyên này")
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }
}
