package tn.esprit.agentservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import tn.esprit.agentservice.dto.ErrorCode;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentServiceOpenAPI() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("M1 - Agent Management API")
                        .version("v1")
                        .description("API for creating, managing, and executing agents."))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .addTagsItem(new Tag().name("Administration des événements"));
    }

    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas("ErrorResponse", errorResponseSchema());
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
                if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                        && "/api/agents/{id}/execute".equals(path)) {
                    setRequestPropertyBounds(openApi, operation, "input", 1, 20000);
                } else if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                        && "/api/admin/agents/{id}/archive".equals(path)) {
                    setRequestPropertyBounds(openApi, operation, "reason", 1, 500);
                } else if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                        && "/api/agents/assist".equals(path)) {
                    setRequestPropertyBounds(openApi, operation, "description", 1, 2000);
                } else if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.DELETE
                        && "/api/admin/agents/{id}".equals(path)) {
                    operation.getParameters().stream()
                            .filter(parameter -> "reason".equals(parameter.getName()))
                            .forEach(parameter -> {
                                parameter.getSchema().setMinLength(1);
                                parameter.getSchema().setMaxLength(500);
                            });
                }
                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }
                responses.addApiResponse("401", new ApiResponse().description("Authentification requise"));
                responses.forEach((code, response) -> {
                    if (isNon2xx(code)) {
                        response.setContent(errorContent());
                    }
                });
                if (isCountedList(path, method)) {
                    ApiResponse success = responses.get("200");
                    if (success != null) {
                        success.addHeaderObject("X-Total-Count", new Header()
                                .description("Nombre total d'éléments correspondant aux filtres.")
                                .schema(new StringSchema()));
                    }
                }
            }));
        };
    }

    private void setRequestPropertyBounds(
            OpenAPI openApi, io.swagger.v3.oas.models.Operation operation, String propertyName,
            int minimum, int maximum
    ) {
        Schema<?> requestSchema = operation.getRequestBody().getContent().get("application/json").getSchema();
        if (requestSchema.get$ref() != null) {
            String schemaName = requestSchema.get$ref().substring(requestSchema.get$ref().lastIndexOf('/') + 1);
            requestSchema = openApi.getComponents().getSchemas().get(schemaName);
        }
        Schema<?> property = (Schema<?>) requestSchema.getProperties().get(propertyName);
        property.setMinLength(minimum);
        property.setMaxLength(maximum);
    }

    private boolean isNon2xx(String responseCode) {
        try {
            int code = Integer.parseInt(responseCode);
            return code < 200 || code >= 300;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isCountedList(String path, io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET
                && ("/api/agents/{id}/executions".equals(path)
                || "/api/executions".equals(path)
                || "/api/admin/audit".equals(path));
    }

    private Content errorContent() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
    }

    private Schema<?> errorResponseSchema() {
        ObjectSchema execution = new ObjectSchema();
        execution.setDescription("Informations de l'exécution associée à une erreur d'exécution.");
        execution.setProperties(java.util.Map.of(
                "executionId", nullableInteger("int64"),
                "versionId", nullableInteger("int64"),
                "durationMs", nullableInteger("int64"),
                "promptTokens", nullableInteger("int32"),
                "completionTokens", nullableInteger("int32"),
                "totalTokens", nullableInteger("int32"),
                "toolCallCount", new IntegerSchema().format("int32")
        ));
        execution.setRequired(List.of("toolCallCount"));

        StringSchema errorCode = new StringSchema();
        errorCode.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        errorCode.setNullable(true);

        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Structure commune des réponses d'erreur de l'API.");
        schema.setProperties(java.util.Map.of(
                "timestamp", new StringSchema().format("date-time"),
                "status", new IntegerSchema().format("int32"),
                "error", new StringSchema(),
                "errorCode", errorCode,
                "message", new StringSchema(),
                "path", new StringSchema(),
                "limit", nullableInteger("int32"),
                "usage", nullableInteger("int64"),
                "execution", execution
        ));
        schema.setRequired(List.of("timestamp", "status", "error", "message", "path"));
        return schema;
    }

    private IntegerSchema nullableInteger(String format) {
        IntegerSchema schema = new IntegerSchema();
        schema.setFormat(format);
        schema.setNullable(true);
        return schema;
    }
}
