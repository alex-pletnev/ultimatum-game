package edu.itmo.ultimatum_game.configs

import edu.itmo.ultimatum_game.dto.responses.ApiErrorResponse
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun ultimatumOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Ultimatum Game API")
                .version("v1")
                .description("REST API of Ultimatum Game. Generated from source annotations.")
        )

    @Bean
    fun addDefaultErrorResponses(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val errorSchemaRef = "#/components/schemas/${ApiErrorResponse::class.simpleName}"
        val errorContent = Content().addMediaType(
            "application/json",
            MediaType().schema(io.swagger.v3.oas.models.media.Schema<Any>().`$ref`(errorSchemaRef))
        )
        openApi.paths?.values?.forEach { pathItem ->
            pathItem.readOperations().forEach { op ->
                val responses = op.responses ?: return@forEach
                listOf("400", "401", "403", "404", "409", "500").forEach { code ->
                    if (!responses.containsKey(code)) {
                        responses.addApiResponse(
                            code,
                            ApiResponse().description(defaultDescription(code)).content(errorContent)
                        )
                    }
                }
            }
        }
    }

    private fun defaultDescription(code: String): String = when (code) {
        "400" -> "Bad Request — валидация тела / query / path"
        "401" -> "Unauthorized — отсутствует или невалидный JWT"
        "403" -> "Forbidden — недостаточно прав или CSRF"
        "404" -> "Not Found — ресурс не найден"
        "409" -> "Conflict — конфликт бизнес-состояния"
        "500" -> "Internal Server Error"
        else -> "Error"
    }
}
