package edu.itmo.ultimatum_game

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.io.File

/**
 * Не тест в обычном смысле — генератор снапшотов OpenAPI/AsyncAPI.
 * Дампит src/main/resources/doc/openapi.json и asyncapi.json из живого Spring-контекста
 * (springdoc + springwolf). Артефакты коммитятся в git и служат источником правды
 * для внешних потребителей API + видимости drift в PR-diff.
 *
 * Запуск: ./gradlew generateApiSnapshots
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "spec-gen", roles = ["ADMIN"])
class SpecSnapshotGeneratorTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val outputDir = File("src/main/resources/doc").also { it.mkdirs() }

    @Test
    fun `dump openapi and asyncapi snapshots`() {
        dumpJson("/v3/api-docs", "openapi.json")
        dumpJson("/springwolf/docs", "asyncapi.json")
    }

    private fun dumpJson(endpoint: String, filename: String) {
        val body = mockMvc.perform(get(endpoint))
            .andReturn()
            .response
            .contentAsString

        val prettyJson = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(objectMapper.readTree(body))

        File(outputDir, filename).writeText(prettyJson + "\n")
    }
}
