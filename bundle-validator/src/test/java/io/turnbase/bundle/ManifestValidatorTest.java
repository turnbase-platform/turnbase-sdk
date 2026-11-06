package io.turnbase.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** manifest-schema.md § Validation — Layer 1 (schema) and Layer 2 (supplementary) checks. */
class ManifestValidatorTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final ManifestValidator validator = new ManifestValidator();

  private static JsonNode parse(String json) throws Exception {
    return JSON.readTree(json);
  }

  private static final String VALID_MANIFEST =
      """
      {
        "manifestVersion": "1",
        "title": "Chess",
        "shortDescription": "Classic two-player strategy on an 8x8 board.",
        "version": "1.0.0",
        "gdkApiVersion": "0.3.0",
        "runtime": "jvm",
        "backend": { "path": "backend/chess.jar", "entryPoint": "com.example.ChessGame" },
        "frontend": { "entryPoint": "frontend/main.js" },
        "infoDoc": "INFO.md",
        "tags": ["strategy", "board", "classic"],
        "capabilities": { "ai": false, "spectators": true, "midGameJoin": false },
        "teamTemplates": [
          { "name": "White", "size": { "min": 1, "max": 1 }, "count": { "min": 1, "max": 1 } },
          { "name": "Black", "size": { "min": 1, "max": 1 }, "count": { "min": 1, "max": 1 } }
        ],
        "parameters": [
          { "key": "moveTimer", "displayName": "Move timer (s)", "type": "integer",
            "min": 0, "max": 600, "default": 60 }
        ]
      }
      """;

  @Test
  void acceptsTheSpecsOwnExample() throws Exception {
    ManifestValidationResult result = validator.validate(parse(VALID_MANIFEST));

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void rejectsCapabilitiesAiTrue() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest.get("capabilities")).put("ai", true);

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).extracting(Finding::code).contains("manifest.capabilities.ai");
  }

  @Test
  void rejectsShortDescriptionOverMaxLength() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest)
        .put("shortDescription", "x".repeat(142));

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors()).extracting(Finding::code).contains("manifest.shortDescription");
  }

  @Test
  void requiresNameWhenMultipleTeamTemplatesDeclared() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest.get("teamTemplates").get(1))
        .remove("name");

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors())
        .extracting(Finding::code)
        .contains("manifest.teamTemplates[1].name");
  }

  @Test
  void rejectsRangeWithMaxBelowMin() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    com.fasterxml.jackson.databind.node.ObjectNode size =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            manifest.get("teamTemplates").get(0).get("size");
    size.put("min", 5);
    size.put("max", 1);

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors())
        .extracting(Finding::code)
        .contains("manifest.teamTemplates[0].size");
  }

  @Test
  void rejectsIntegerParameterDefaultOutsideRange() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest.get("parameters").get(0))
        .put("default", 700);

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors())
        .extracting(Finding::code)
        .contains("manifest.parameters[0].default");
  }

  @Test
  void rejectsDuplicateParameterKeys() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    com.fasterxml.jackson.databind.node.ArrayNode parameters =
        (com.fasterxml.jackson.databind.node.ArrayNode) manifest.get("parameters");
    parameters.add(parameters.get(0).deepCopy());

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors()).extracting(Finding::code).contains("manifest.parameters");
  }

  @Test
  void rejectsEnumDefaultNotAmongOptions() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    com.fasterxml.jackson.databind.node.ObjectNode parameter =
        JSON.createObjectNode()
            .put("key", "difficulty")
            .put("displayName", "Difficulty")
            .put("type", "enum")
            .put("default", "extreme");
    parameter.putArray("options").add("easy").add("hard");
    ((com.fasterxml.jackson.databind.node.ArrayNode) manifest.get("parameters")).add(parameter);

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.errors())
        .extracting(Finding::code)
        .contains("manifest.parameters[1].default");
  }

  @Test
  void acceptsWellFormedCreditsAndTheirAbsence() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest)
        .set(
            "credits",
            parse(
                """
                { "designers": ["Antoine Bauza", "Bruno Cathala"], "artists": ["Miguel Coimbra"],
                  "publisher": "Repos Production", "year": 2015 }
                """));

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.valid()).isTrue();
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void rejectsMalformedCredits() throws Exception {
    for (String credits :
        List.of(
            "{ \"artists\": [\"A\"] }",
            "{ \"designers\": [] }",
            "{ \"designers\": [\"A\"], \"year\": \"2015\" }",
            "{ \"designers\": [\"A\"], \"publisher\": \"\" }",
            "\"Bauza\"")) {
      JsonNode manifest = parse(VALID_MANIFEST);
      ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).set("credits", parse(credits));

      ManifestValidationResult result = validator.validate(manifest);

      assertThat(result.valid()).as(credits).isFalse();
      assertThat(result.errors())
          .extracting(Finding::code)
          .anyMatch(c -> c.startsWith("manifest.credits"));
    }
  }

  @Test
  void warnsOnUnrecognizedKeyWithoutFailingValidation() throws Exception {
    JsonNode manifest = parse(VALID_MANIFEST);
    ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).put("capabilites", true);

    ManifestValidationResult result = validator.validate(manifest);

    assertThat(result.valid()).isTrue();
    assertThat(result.warnings()).extracting(Finding::code).contains("manifest.capabilites");
  }

  @Test
  void reportsEveryMissingRequiredTopLevelField() throws Exception {
    JsonNode manifest = parse("{}");

    ManifestValidationResult result = validator.validate(manifest);

    List<String> checks = result.errors().stream().map(Finding::code).toList();
    assertThat(checks).isNotEmpty();
    assertThat(result.valid()).isFalse();
  }
}
