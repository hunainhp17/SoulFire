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
package com.soulfiremc.server.script.nodes.encoding;

import com.soulfiremc.server.script.*;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import·javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/// Encoding node that decrypts an AES-encrypted string.
public final class DecryptNode extends AbstractScriptNode {
  private static final byte[] FORMAT_MARKER = "SFENC1".getBytes(StandardCharsets.UTF_8);
  private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
  private static final String LEGACY_ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int LEGACY_IV_LENGTH_BYTES = 16;

  public static final NodeMetadata METADATA = NodeMetadata.builder()
    .type("encoding.decrypt")
    .displayName("Decrypt")
    .category(CategoryRegistry.ENCODING)
    .addInputs(
      PortDefinition.input("ciphertext", "Ciphertext", PortType.STRING, "Base64-encoded encrypted data"),
      PortDefinition.input("key", "Key", PortType.STRING, "Decryption key")
    )
    .addOutputs(
      PortDefinition.output("plaintext", "Plaintext", PortType.STRING, "Decrypted text"),
      PortDefinition.output("success", "Success", PortType.BOOLEAN, "Whether decryption succeeded"),
      PortDefinition.output("errorMessage", "Error Message", PortType.STRING, "Error message if failed")
    )
    .description("Decrypts an AES-encrypted string")
    .icon("unlock")
    .color("#64748B")
    .addKeywords("decrypt", "aes", "cipher", "decipher")
    .build();

  @Override
  public Mono<Map<String, NodeValue>> executeReactive(NodeRuntime runtime, Map<String, NodeValue> inputs) {
    var ciphertext = getStringInput(inputs, "ciphertext", "");
    var key = getStringInput(inputs, "key", "");

    if (key.isEmpty()) {
      return completedMono(results(
        "plaintext", "",
        "success", false,
        "errorMessage", "Key is required"
      ));
    }

    try {
      var keyHash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
      var secretKey = new SecretKeySpec(keyHash, "AES");

      var combined = Base64.getDecoder().decode(ciphertext);

      if (startsWithFormatMarker(combined)) {
        return completedMono(decryptAuthenticated(secretKey, combined));
      }

      return completedMono(decryptLegacy(secretKey, combined));
    } catch (Exception e) {
      return completedMono(results(
        "plaintext", "",
        "success", false,
        "errorMessage", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
      ));
    }
  }

  private static boolean startsWithFormatMarker(byte[] combined) {
    if (combined.length < FORMAT_MARKER.length) {
      return false;
    }

    for (var i = 0; i < FORMAT_MARKER.length; i++) {
      if (combined[i] != FORMAT_MARKER[i]) {
        return false;
      }
    }

    return true;
  }

  private Map<String, NodeValue> decryptAuthenticated(SecretKeySpec secretKey, byte[] combined)
    throws Exception {
    var gcmPayloadOffset = FORMAT_MARKER.length;
    if (combined.length <= gcmPayloadOffset + GCM_IV_LENGTH_BYTES) {
      return results(
        "plaintext", "",
        "success", false,
        "errorMessage", "Invalid ciphertext"
      );
    }

    // Extract IV after the format marker.
    var iv = new byte[GCM_IV_LENGTH_BYTES];
    var encrypted = new byte[combined.length - gcmPayloadOffset - GCM_IV_LENGTH_BYTES];
    System.arraycopy(combined, gcmPayloadOffset, iv, 0, GCM_IV_LENGTH_BYTES);
    System.arraycopy(combined, gcmPayloadOffset + GCM_IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

    var cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
    var decrypted = cipher.doFinal(encrypted);

    return results(
      "plaintext", new String(decrypted, StandardCharsets.UTF_8),
      "success", true,
      "errorMessage", ""
    );
  }

  private Map<String, NodeValue> decryptLegacy(SecretKeySpec secretKey, byte[] combined) throws Exception {
    if (combined.length < LEGACY_IV_LENGTH_BYTES) {
      return results(
        "plaintext", "",
        "success", false,
        "errorMessage", "Invalid ciphertext"
      );
    }

    // Legacy payloads used CBC and stored the IV in the first 16 bytes.
    var iv = new byte[LEGACY_IV_LENGTH_BYTES];
    var encrypted = new byte[combined.length - LEGACY_IV_LENGTH_BYTES];
    System.arraycopy(combined, 0, iv, 0, LEGACY_IV_LENGTH_BYTES);
    System.arraycopy(combined, LEGACY_IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

    var cipher = Cipher.getInstance(LEGACY_ENCRYPTION_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
    var decrypted = cipher.doFinal(encrypted);

    return results(
      "plaintext", new String(decrypted, StandardCharsets.UTF_8),
      "success", true,
      "errorMessage", ""
    );
  }
}
