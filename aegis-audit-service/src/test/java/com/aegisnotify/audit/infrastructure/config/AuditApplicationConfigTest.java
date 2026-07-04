package com.aegisnotify.audit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AuditApplicationConfigTest {

  @Test
  void applicationYmlDefinesRequiredAuditSettings() throws IOException {
    Path applicationYaml = Paths.get(System.getProperty("user.dir"), "src",
        "main", "resources", "application.yml");
    Path localApplicationYaml = Paths.get(System.getProperty("user.dir"),
        "src", "main", "resources", "application-local.yml");

    assertThat(applicationYaml).exists();
    assertThat(localApplicationYaml).exists();

    String content = Files.readString(applicationYaml, StandardCharsets.UTF_8);
    String localContent = Files.readString(localApplicationYaml,
        StandardCharsets.UTF_8);

    assertThat(content).contains("spring:");
    assertThat(content).contains("kafka:");
    assertThat(content).contains("bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}");
    assertThat(content).contains("mongodb:");
    assertThat(content).contains("uri: ${MONGODB_URI:mongodb://localhost:27017/aegisnotify-audit}");
    assertThat(content).contains("topic: notification-audit-events");
    assertThat(content).contains("group-id: audit-service");
    assertThat(content).contains("key: ${AUDIT_ENCRYPTION_KEY}");
    assertThat(content).doesNotContain("key: ${AUDIT_ENCRYPTION_KEY:}");
    assertThat(localContent).contains("on-profile: local");
    assertThat(localContent)
        .contains("key: ${AUDIT_ENCRYPTION_KEY:MDEyMzQ1Njc4OTAxMjM0",
            "NTY3ODkwMTIzNDU2Nzg5MDE=}");
  }
}
