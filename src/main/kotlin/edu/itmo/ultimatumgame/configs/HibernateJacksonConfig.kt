package edu.itmo.ultimatumgame.configs

import com.fasterxml.jackson.databind.ObjectMapper
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration

/**
 * Плагинит Spring-managed ObjectMapper (с KotlinModule) в Hibernate JSON-mapper.
 * Без этого Kotlin data class'ы без no-arg конструктора не десериализуются из jsonb-колонок.
 */
@Configuration
class HibernateJacksonConfig(
    private val objectMapper: ObjectMapper,
) : HibernatePropertiesCustomizer {
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties["hibernate.type.json_format_mapper"] = JacksonJsonFormatMapper(objectMapper)
    }
}
