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
package com.soulfiremc.server.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.FieldBehavior;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.docs.FieldRequirement;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.soulfiremc.grpc.generated.UserServiceGrpc;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiSpecGeneratorTest {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Test
  void optionalBehaviorOverridesRequiredness() {
    assertFalse(OpenApiSpecGenerator.isRequired(
      FieldRequirement.REQUIRED,
      Set.of(FieldBehavior.OPTIONAL)
    ));
  }

  @Test
  void schemaMetadataMapsOpenApiKeywords() {
    var schema = JSON_MAPPER.createObjectNode();
    schema.put("type", "string");

    OpenApiSpecGenerator.applyFieldSchemaMetadata(
      schema,
      Set.of(FieldBehavior.OUTPUT_ONLY),
      "uuid",
      "\"550e8400-e29b-41d4-a716-446655440000\""
    );

    assertEquals("uuid", schema.path("format").asText());
    assertTrue(schema.path("readOnly").asBoolean());
    assertEquals("550e8400-e29b-41d4-a716-446655440000", schema.path("example").asText());
    assertFalse(schema.has("x-google-field-behaviors"));
  }

  @Test
  void unsupportedBehaviorsArePreservedAsExtension() {
    var schema = JSON_MAPPER.createObjectNode();
    schema.put("type", "array");

    OpenApiSpecGenerator.applyFieldSchemaMetadata(
      schema,
      EnumSet.of(FieldBehavior.UNORDERED_LIST, FieldBehavior.IMMUTABLE),
      "",
      ""
    );

    var behaviors = schema.path("x-google-field-behaviors");
    assertEquals(2, behaviors.size());
    assertEquals("IMMUTABLE", behaviors.get(0).asText());
    assertEquals("UNORDERED_LIST", behaviors.get(1).asText());
  }

  @Test
  void generatedSpecIncludesUnorderedListExtensionFromProto() {
    var grpcService = GrpcService.builder()
      .addService(new UserServiceGrpc.UserServiceImplBase() {
      })
      .enableUnframedRequests(true)
      .enableHttpJsonTranscoding(true)
      .build();
    var server = Server.builder()
      .service(grpcService)
      .build();

    try {
      var openApi = OpenApiSpecGenerator.generate(server.config().serviceConfigs(), "https://example.com");
      var usersSchema = (ObjectNode) openApi.at("/components/schemas/UserListResponse/properties/users");

      assertEquals("array", usersSchema.path("type").asText());
      assertEquals("UNORDERED_LIST", usersSchema.path("x-google-field-behaviors").get(0).asText());
    } finally {
      server.close();
    }
  }
}
