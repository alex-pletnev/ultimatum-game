package edu.itmo.ultimatumgame.configs

import edu.itmo.ultimatumgame.util.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Применяет `src/main/resources/index.sql` при готовности приложения (T-001).
 *
 * Hibernate `ddl-auto=update` не подхватывает произвольные .sql файлы и не создаёт
 * PostgreSQL-специфичные вещи (extension `pg_trgm`, GIN-индексы для trgm). Раньше это
 * требовало ручного шага; теперь скрипт применяется автоматически и идемпотентно
 * (`CREATE EXTENSION IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).
 */
@Component
class IndexSqlInitializer(
    private val jdbcTemplate: JdbcTemplate,
    @Value("classpath:index.sql") private val indexSql: Resource,
) {

    private val log = logger()

    @EventListener(ApplicationReadyEvent::class)
    fun applyIndexSql() {
        // index.sql использует PostgreSQL-специфику (pg_trgm, GIN). В тестах на H2 просто пропускаем.
        val productName = jdbcTemplate.dataSource?.connection?.use { it.metaData.databaseProductName }
        if (productName == null || !productName.equals("PostgreSQL", ignoreCase = true)) {
            log.info("Пропуск index.sql: DB={} (требуется PostgreSQL)", productName)
            return
        }
        val sql = indexSql.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        // index.sql содержит несколько stmt'ов разделённых `;`. Простое разбиение подходит,
        // потому что в scripts проекта нет `;` внутри литералов/комментариев.
        val statements = sql.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("--") }
        log.info("Применяем index.sql: {} statements", statements.size)
        statements.forEach { stmt ->
            log.debug("execute: {}", stmt)
            jdbcTemplate.execute(stmt)
        }
        log.info("index.sql применён успешно")
    }
}
