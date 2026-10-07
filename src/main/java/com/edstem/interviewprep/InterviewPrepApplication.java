package com.edstem.interviewprep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InterviewPrepApplication {

  public static void main(String[] args) {
    SpringApplication.run(InterviewPrepApplication.class, args);
  }
}
