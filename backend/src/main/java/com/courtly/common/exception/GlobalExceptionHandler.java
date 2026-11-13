package com.courtly.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Doi moi loai loi thanh cung mot hinh dang {@link ApiError}.
 *
 * <p>Khong bao gio tra stacktrace, cau SQL hay ten constraint ra ngoai.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_MISMATCH = "typeMismatch";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getCode(), exception.getMessage(), exception.getFieldErrors()));
    }

    /** Loi tu Bean Validation o DTO - lop validate thu nhat. */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(BindException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), readableMessage(error)));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Du lieu gui len chua hop le, vui long kiem tra lai cac o duoc danh dau.",
                fieldErrors));
    }

    /**
     * Thong bao cua Spring khi ep kieu that bai co chua ten class Java
     * ("required type 'java.lang.Double'"), khong duoc de lo ra ngoai.
     */
    private String readableMessage(FieldError error) {
        if (Arrays.asList(error.getCodes() == null ? new String[0] : error.getCodes()).contains(TYPE_MISMATCH)) {
            return "Gia tri khong hop le";
        }
        return error.getDefaultMessage();
    }

    /** Tham so duong dan hoac query sai kieu, vi du ?page=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Du lieu gui len chua hop le, vui long kiem tra lai cac o duoc danh dau.",
                Map.of(exception.getName(), "Gia tri khong hop le")));
    }

    /** Bean Validation tren query/path parameter cua controller @Validated. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            fieldErrors.putIfAbsent(field, violation.getMessage());
        });
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Du lieu gui len chua hop le, vui long kiem tra lai cac o duoc danh dau.",
                fieldErrors));
    }

    /**
     * JSON sai cu phap, sai kieu du lieu, hoac gia tri enum khong hop le.
     *
     * <p>Jackson bao loi truoc khi Bean Validation chay, nen phai tu trich duong dan
     * truong bi sai de frontend van hien duoc loi ngay duoi o nhap tuong ung.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        log.debug("Request body khong doc duoc: {}", exception.getMessage());

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String field = extractFieldPath(exception);
        if (field != null) {
            fieldErrors.put(field, "Gia tri khong hop le");
        }

        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                fieldErrors.isEmpty()
                        ? "Du lieu gui len khong doc duoc."
                        : "Du lieu gui len chua hop le, vui long kiem tra lai cac o duoc danh dau.",
                fieldErrors));
    }

    /** Doi duong dan cua Jackson thanh dang "availability[0].dayOfWeek". */
    private String extractFieldPath(HttpMessageNotReadableException exception) {
        if (!(exception.getCause() instanceof JsonMappingException mappingException)) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : mappingException.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? null : path.toString();
    }

    /**
     * Lop validate thu ba: rang buoc o database.
     *
     * <p>Lop nghiep vu da kiem tra truoc, nhung hai request dong thoi van co the
     * cung vuot qua va cham unique index. Truong hop do ket thuc o day.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Vi pham rang buoc database", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Du lieu bi trung hoac xung dot, vui long tai lai trang va thu lai.",
                Map.of()));
    }

    /** Thieu tham so hoac phan multipart bat buoc, vd tai anh ma khong gui file (2.3.6). */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingPart(Exception exception) {
        String name = exception instanceof MissingServletRequestPartException part
                ? part.getRequestPartName()
                : ((MissingServletRequestParameterException) exception).getParameterName();
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Du lieu gui len chua hop le, vui long kiem tra lai cac o duoc danh dau.",
                Map.of(name, "Thieu du lieu bat buoc")));
    }

    /** 2.3.6 - file tai len vuot gioi han multipart (5MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.IMAGE_INVALID,
                "Anh vuot qua dung luong toi da 5MB.",
                Map.of("file", "Anh toi da 5MB")));
    }

    /** Goi sai HTTP method, vd POST vao endpoint chi co GET/PUT: loi phia client, khong phai 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                "Thao tac nay khong duoc ho tro.",
                Map.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Dinh dang du lieu gui len khong duoc ho tro.",
                Map.of()));
    }

    /** Duong dan khong ton tai. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                ErrorCode.NOT_FOUND,
                "Khong tim thay duong dan yeu cau.",
                Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                ErrorCode.FORBIDDEN,
                "Ban khong co quyen thuc hien thao tac nay.",
                Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Loi khong luong truoc tai {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                ErrorCode.INTERNAL_ERROR,
                "He thong dang gap su co, vui long thu lai sau.",
                Map.of()));
    }
}
