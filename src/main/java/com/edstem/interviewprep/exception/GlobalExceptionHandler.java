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
import org.springframework.web.bind.MethodArgumentNotValidException;
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
