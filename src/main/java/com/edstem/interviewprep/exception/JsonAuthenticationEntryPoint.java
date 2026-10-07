package com.edstem.interviewprep.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 as JSON: no token, or a token that is expired, tampered with or otherwise invalid. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemWriter problemWriter;

  public JsonAuthenticationEntryPoint(ProblemWriter problemWriter) {
    this.problemWriter = problemWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    boolean badToken = authException instanceof InvalidBearerTokenException;
    response.setHeader(
        HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
    problemWriter.write(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        badToken ? "INVALID_TOKEN" : "UNAUTHENTICATED",
        badToken
            ? "The access token is invalid or has expired; log in again"
            : "Authentication is required to access this resource");
  }
}
