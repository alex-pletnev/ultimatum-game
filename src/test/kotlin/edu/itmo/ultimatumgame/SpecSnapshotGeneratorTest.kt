package edu.itmo.ultimatumgame

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.junit.jupiter.api.Tag
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
 * Отделён тегом "snapshot" — обычный `./gradlew test` его пропускает.
 * Запуск: `./gradlew generateApiSnapshots`.
 */
@Tag("snapshot")
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

        // Стабильный порядок ключей — иначе каждый прогон даёт шумный diff в git.
        // Читаем в plain Map/List (не JsonNode — ObjectNode не подчиняется ORDER_MAP_ENTRIES_BY_KEYS),
        // тогда SerializationFeature при записи применяется рекурсивно.
        val stableMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        val raw: Any = stableMapper.readValue(body, Any::class.java)
        val prettyJson = stableMapper.writerWithDefaultPrettyPrinter().writeValueAsString(raw)

        File(outputDir, filename).writeText(prettyJson + "\n")
    }
}
