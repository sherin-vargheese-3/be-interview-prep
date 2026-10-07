package com.edstem.interviewprep.exception;

import com.edstem.interviewprep.dto.FieldError;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to one RFC 9457 {@link ProblemDetail} shape: {@code type, title, status, detail,
 * instance, code} plus {@code errors[]} (field, message) for invalid input.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(TaskNotFoundException.class)
  ProblemDetail handleNotFound(TaskNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(InvalidFieldException.class)
  ProblemDetail handleInvalidField(InvalidFieldException ex) {
    return validationProblem(List.of(new FieldError(ex.getField(), ex.getMessage())));
  }

  @ExceptionHandler(LinkNotFoundException.class)
  ProblemDetail handleLinkNotFound(LinkNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", ex.getMessage());
  }

  /** 410 Gone: the code existed but is permanently unusable, unlike 404 (never existed). */
  @ExceptionHandler(LinkExpiredException.class)
  ProblemDetail handleLinkExpired(LinkExpiredException ex) {
    return problem(HttpStatus.GONE, "LINK_EXPIRED", ex.getMessage());
  }

  @ExceptionHandler(UserNotFoundException.class)
  ProblemDetail handleUserNotFound(UserNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  ProblemDetail handleDuplicateEmail(EmailAlreadyRegisteredException ex) {
    return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ProblemDetail> handleBadCredentials(InvalidCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage()));
  }

  /**
   * Security exceptions raised inside a controller (e.g. a future {@code @PreAuthorize}) reach MVC
   * first; without these they would fall into the 500 handler below.
   */
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access this resource");
  }

  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail handleAuthentication(AuthenticationException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED",
        "Authentication is required to access this resource");
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage());
  }

  /** 409 with the numbers a client needs to react (e.g. lower the quantity). */
  @ExceptionHandler(InsufficientStockException.class)
  ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
    ProblemDetail detail = problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage());
    detail.setProperty("productId", ex.getProductId());
    detail.setProperty("requested", ex.getRequested());
    detail.setProperty("available", ex.getAvailable());
    return detail;
  }

  @ExceptionHandler(IdempotencyKeyInProgressException.class)
  ProblemDetail handleKeyInProgress(IdempotencyKeyInProgressException ex) {
    return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS", ex.getMessage());
  }

  @ExceptionHandler(IdempotencyKeyReusedException.class)
  ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", ex.getMessage());
  }

  @ExceptionHandler(InvalidIdempotencyKeyException.class)
  ProblemDetail handleInvalidKey(InvalidIdempotencyKeyException ex) {
    return validationProblem(List.of(new FieldError("Idempotency-Key", ex.getMessage())));
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex instanceof MissingRequestHeaderException missing) {
      String header = missing.getHeaderName();
      return ResponseEntity.badRequest()
          .body(validationProblem(List.of(new FieldError(header, header + " header is required"))));
    }
    return super.handleServletRequestBindingException(ex, headers, status, request);
  }

  @ExceptionHandler(InvalidSortException.class)
  ProblemDetail handleInvalidSort(InvalidSortException ex) {
    return validationProblem(List.of(new FieldError("sort", ex.getMessage())));
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    error.isBindingFailure()
                        ? new FieldError(
                            error.getField(),
                            error.getField()
                                + " "
                                + expectation(ex.getBindingResult().getFieldType(error.getField())))
                        : new FieldError(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      String field = fieldPath(mapping);
      return ResponseEntity.badRequest()
          .body(validationProblem(List.of(new FieldError(field, readableMessage(field, mapping)))));
    }
    return ResponseEntity.badRequest()
        .body(
            problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not valid JSON"));
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String field =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ex.getPropertyName();
    String message = field + " " + expectation(ex.getRequiredType());
    return ResponseEntity.badRequest()
        .body(validationProblem(List.of(new FieldError(field, message))));
  }

  /** Gives framework-handled errors (404 route, 405, 415, ...) the same {@code code} property. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail detail
        && (detail.getProperties() == null || !detail.getProperties().containsKey("code"))) {
      HttpStatus resolved = HttpStatus.resolve(status.value());
      detail.setProperty("code", resolved != null ? resolved.name() : "ERROR");
    }
    return response;
  }

  static ProblemDetail validationProblem(List<FieldError> errors) {
    ProblemDetail detail =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid");
    detail.setProperty("errors", errors);
    return detail;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String message) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setProperty("code", code);
    return detail;
  }

  private static String fieldPath(JsonMappingException ex) {
    return ex.getPath().stream()
        .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
        .collect(Collectors.joining("."));
  }

  private static String readableMessage(String field, JsonMappingException ex) {
    if (ex instanceof UnrecognizedPropertyException) {
      return field + " is not a recognised field";
    }
    if (ex instanceof InvalidFormatException format) {
      return field + " " + expectation(format.getTargetType());
    }
    return field + " has an invalid value";
  }

  private static String expectation(Class<?> type) {
    if (type == null) {
      return "has an invalid value";
    }
    if (type.isEnum()) {
      return "must be one of " + Arrays.toString(type.getEnumConstants());
    }
    if (Number.class.isAssignableFrom(type)) {
      return "must be a number";
    }
    if (type == Boolean.class) {
      return "must be true or false";
    }
    if (type == LocalDate.class) {
      return "must be a valid date in ISO format (yyyy-MM-dd)";
    }
    if (Temporal.class.isAssignableFrom(type)) {
      return "must be an ISO-8601 timestamp, e.g. 2030-01-01T00:00:00Z";
    }
    if (type == UUID.class) {
      return "must be a valid UUID";
    }
    return "has an invalid value";
  }
}
