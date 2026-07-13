package edu.itmo.ultimatumgame.util

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Единственный «жёсткий» канал доменных событий.
 *
 * На каждый [emit]:
 *  1. Пишется структурированная запись в logger `domain-events` — поля события
 *     попадают в JSON (в prod-профиле) через logstash StructuredArguments.
 *  2. Инкрементится Micrometer-counter с именем `ultimatum.<event.type>`; все
 *     UUID-поля идут в теги (для доступа в Prometheus/Grafana в будущем).
 *
 * Технические логи оставляем на обычный SLF4J (см. `logger()` extension).
 */
@Component
class DomainEventLogger(private val meter: MeterRegistry) {

    private val log = LoggerFactory.getLogger("domain-events")

    fun emit(event: DomainEvent) {
        log.info(
            "{} {}",
            event.type,
            StructuredArguments.entries(event.fields + ("event_type" to event.type))
        )

        meter.counter("ultimatum.${event.type}", eventTags(event)).increment()
    }

    private fun eventTags(event: DomainEvent): Tags {
        // В теги Prometheus кладём только «оси разреза» с ограниченной кардинальностью.
        // UUID-поля с высокой кардинальностью НЕ в теги, чтобы не взорвать Prometheus.
        var tags = Tags.empty()
        for ((k, v) in event.fields) {
            if (k in SAFE_TAG_KEYS && v != null) {
                tags = tags.and(k, v.toString())
            }
        }
        return tags
    }

    private companion object {
        val SAFE_TAG_KEYS = setOf("role", "sessionType", "accepted")
    }
}
