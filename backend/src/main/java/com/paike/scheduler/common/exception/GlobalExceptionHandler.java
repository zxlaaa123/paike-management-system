package com.paike.scheduler.common.exception;

import com.paike.scheduler.common.response.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<Integer, HttpStatus> BIZ_CODE_HTTP = Map.of(
            SystemErrorCode.AUTH_UNAUTHORIZED.getNumericCode(), HttpStatus.UNAUTHORIZED,
            SystemErrorCode.AUTH_FORBIDDEN.getNumericCode(), HttpStatus.FORBIDDEN,
            SystemErrorCode.RESOURCE_NOT_FOUND.getNumericCode(), HttpStatus.NOT_FOUND,
            SystemErrorCode.CONFLICT_ERROR.getNumericCode(), HttpStatus.CONFLICT,
            SystemErrorCode.AUTH_RATE_LIMITED.getNumericCode(), HttpStatus.TOO_MANY_REQUESTS
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = BIZ_CODE_HTTP.getOrDefault(ex.getCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(status.value()).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidationException(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException validEx) {
            message = validEx.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException bindEx) {
            message = bindEx.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        } else {
            message = "参数校验失败";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.VALIDATION_ERROR.getNumericCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.VALIDATION_ERROR.getNumericCode(), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.REQUEST_BODY_INVALID.getNumericCode(), "请求体格式错误"));
    }

    /** 业务层非法入参（Service 层 IllegalArgumentException 不该再被吞成 500） */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.VALIDATION_ERROR.getNumericCode(),
                        ex.getMessage() != null ? ex.getMessage() : "参数非法"));
    }

    /** 缺少必填的 query/form 参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.VALIDATION_ERROR.getNumericCode(), "缺少必填参数：" + ex.getParameterName()));
    }

    /** path/query 参数类型转换失败（如把字母传给 Long 参数） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(SystemErrorCode.VALIDATION_ERROR.getNumericCode(), "参数 " + ex.getName() + " 类型不正确"));
    }

    /** HTTP 方法不允许 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(SystemErrorCode.METHOD_NOT_ALLOWED.getNumericCode(), "方法不被允许：" + ex.getMethod()));
    }

    /** 找不到匹配的接口（需要把 spring.mvc.throw-exception-if-no-handler-found 打开才会触发） */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(SystemErrorCode.RESOURCE_NOT_FOUND.getNumericCode(), "接口不存在：" + ex.getRequestURL()));
    }

    /** Spring Boot 3 未匹配到接口时可能按静态资源缺失抛出 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(SystemErrorCode.RESOURCE_NOT_FOUND.getNumericCode(), "接口不存在：" + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(SystemErrorCode.SYSTEM_ERROR.getNumericCode(), SystemErrorCode.SYSTEM_ERROR.getDefaultMessage()));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ":" + error.getDefaultMessage();
    }
}
