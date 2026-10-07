package com.edstem.interviewprep.config;

import com.edstem.interviewprep.enums.Role;
import com.edstem.interviewprep.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Self-registration only ever creates USERs, so the first ADMIN is seeded at startup from {@code
 * ADMIN_EMAIL} / {@code ADMIN_PASSWORD}. Skipped when either is unset or the email already exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final AdminProperties admin;
  private final UserService userService;

  public AdminBootstrap(AdminProperties admin, UserService userService) {
    this.admin = admin;
    this.userService = userService;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!admin.isConfigured()) {
      log.info("No ADMIN_EMAIL/ADMIN_PASSWORD set; skipping admin seeding");
      return;
    }
    boolean created =
        userService.createIfAbsent(admin.email(), admin.password(), "Administrator", Role.ADMIN);
    log.info(created ? "Seeded admin user {}" : "Admin user {} already exists", admin.email());
  }
}
