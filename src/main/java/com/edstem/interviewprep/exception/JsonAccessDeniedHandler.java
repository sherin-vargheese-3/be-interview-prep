package com.edstem.interviewprep.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 as JSON: the caller is logged in but lacks the required role. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemWriter problemWriter;

  public JsonAccessDeniedHandler(ProblemWriter problemWriter) {
    this.problemWriter = problemWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    problemWriter.write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You do not have permission to access this resource");
  }
}
