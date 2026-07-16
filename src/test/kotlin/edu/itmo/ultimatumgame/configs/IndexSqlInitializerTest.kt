package edu.itmo.ultimatumgame.configs

import io.mockk.mockk
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexSqlInitializerTest {

    private val initializer = IndexSqlInitializer(mockk<JdbcTemplate>(), mockk<Resource>())

    @Test
    fun `splitSqlStatements — комменты перед statement'ом не съедают его (T-085)`() {
        val sql = """
            CREATE INDEX foo ON bar (baz);

            -- T-084 комментарий
            -- вторая строка
            ALTER TABLE session
                ADD COLUMN IF NOT EXISTS auto_advance_rounds BOOLEAN NOT NULL DEFAULT FALSE;
        """.trimIndent()
        val statements = initializer.splitSqlStatements(sql)
        assertEquals(2, statements.size)
        assertEquals(true, statements[1].startsWith("ALTER TABLE session"))
    }

    @Test
    fun `splitSqlStatements — пустые chunk'и отбрасываются`() {
        val sql = """
            -- только комментарий
            ;
            CREATE INDEX bar ON baz (qux);
        """.trimIndent()
        val statements = initializer.splitSqlStatements(sql)
        assertEquals(1, statements.size)
    }

    @Test
    fun `splitSqlStatements — inline комменты внутри строки не режут statement`() {
        val sql = """
            CREATE INDEX foo -- inline
                ON bar (baz);
        """.trimIndent()
        val statements = initializer.splitSqlStatements(sql)
        assertEquals(1, statements.size)
        assertEquals(true, statements[0].contains("ON bar"))
    }
}
