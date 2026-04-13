/*
 * SoulFire
 * Copyright (C) 2026  AlexProgrammerDE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.soulfiremc.test;

import com.soulfiremc.server.SoulFireServer;
import com.soulfiremc.server.user.AuthSystem;
import com.soulfiremc.server.util.PortHelper;
import com.soulfiremc.shared.SFLogAppender;
import com.soulfiremc.test.utils.TestBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class AuthSystemRootDefaultEmailTest {
  private static final String ROOT_DEFAULT_EMAIL_PROPERTY = "sf.root.default.email";

  @TempDir
  public Path tempDir;

  @Test
  void jvmFlagTakesPrecedenceOverEnvOverride() {
    assertEquals(
      "jvm-admin@example.com",
      AuthSystem.resolveRootDefaultEmail("jvm-admin@example.com", "env-admin@example.com")
    );
  }

  @Test
  void envOverrideIsUsedWhenJvmFlagIsMissing() {
    assertEquals(
      "env-admin@example.com",
      AuthSystem.resolveRootDefaultEmail(null, " env-admin@example.com ")
    );
  }

  @Test
  void invalidOverridesFallBackToBuiltInDefault() {
    assertEquals(
      AuthSystem.ROOT_DEFAULT_EMAIL,
      AuthSystem.resolveRootDefaultEmail("not-an-email", "still-not-an-email")
    );
  }

  @Test
  void rootUserUsesJvmConfiguredDefaultEmailOnFirstStartup() {
    var previousBaseDir = System.getProperty("sf.baseDir");
    var previousUnitTest = System.getProperty("sf.unit.test");
    var previousRootDefaultEmail = System.getProperty(ROOT_DEFAULT_EMAIL_PROPERTY);

    try {
      System.setProperty("sf.baseDir", tempDir.toAbsolutePath().toString());
      System.setProperty("sf.unit.test", "true");
      System.setProperty(ROOT_DEFAULT_EMAIL_PROPERTY, "bootstrap-admin@example.com");

      TestBootstrap.bootstrapForTest();
      SFLogAppender.INSTANCE.start();

      var server = new SoulFireServer("127.0.0.1", PortHelper.getRandomAvailablePort(), Instant.now());
      try {
        assertEquals(
          "bootstrap-admin@example.com",
          server.authSystem().rootUserData().getEmail()
        );
      } finally {
        server.shutdownManager().shutdownSoftware(false);
        server.shutdownManager().awaitShutdown();
      }
    } finally {
      restoreProperty("sf.baseDir", previousBaseDir);
      restoreProperty("sf.unit.test", previousUnitTest);
      restoreProperty(ROOT_DEFAULT_EMAIL_PROPERTY, previousRootDefaultEmail);
    }
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
