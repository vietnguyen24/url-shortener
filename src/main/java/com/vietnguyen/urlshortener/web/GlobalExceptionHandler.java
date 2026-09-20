package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.service.InvalidDestinationException;
import com.vietnguyen.urlshortener.service.LinkCreationFailedException;
import com.vietnguyen.urlshortener.service.LinkNotFoundException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Central exception boundary for HTTP API errors. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(InvalidDestinationException.class)
  ProblemDetail invalidDestination(InvalidDestinationException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid destination", exception.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("Request is invalid");
    return handleExceptionInternal(
        exception,
        problem(HttpStatus.BAD_REQUEST, "Invalid request", detail),
        headers,
        status,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return handleExceptionInternal(
        exception,
        problem(HttpStatus.BAD_REQUEST, "Malformed request", "Request body is not valid JSON"),
        headers,
        status,
        request);
  }

  @ExceptionHandler(LinkCreationFailedException.class)
  ProblemDetail linkCreationFailed(LinkCreationFailedException exception) {
    LOGGER.error("Link creation failed", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Link creation failed", exception.getMessage());
  }

  @ExceptionHandler(LinkNotFoundException.class)
  ProblemDetail linkNotFound(LinkNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Link not found", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpectedException(Exception exception) {
    LOGGER.error("Unexpected request failure", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "The request could not be completed");
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("about:blank"));
    problem.setTitle(title);
    return problem;
  }
}
