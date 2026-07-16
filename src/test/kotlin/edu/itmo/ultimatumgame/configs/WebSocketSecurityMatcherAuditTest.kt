package edu.itmo.ultimatumgame.configs

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.util.AntPathMatcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T-071: аудит целостности `WebSocketSecurityConfig`.
 *
 * Тест собирает все `@MessageMapping`-destinations из пакета controllers.ws и все
 * `@SendToUser`-destinations во всём приложении, и проверяет что каждый destination
 * покрыт matcher'ом в `WebSocketSecurityConfig` (`simpDestMatchers` или
 * `simpSubscribeDestMatchers`).
 *
 * Зачем: раньше при добавлении STOMP-endpoint'а или `@SendToUser` забывали обновить
 * `WebSocketSecurityConfig` — destination попадал под `anyMessage().denyAll()`
 * (T-050, T-054 — оба вскрылись только когда фронт напоролся, регресс-миссы двух
 * self-review подряд).
 *
 * Test-подход выбран вместо process-rule (обновление pre-flight.md) — надёжнее:
 * enforce на каждом `./gradlew check`, не зависит от дисциплины агента.
 */
class WebSocketSecurityMatcherAuditTest {

    private val pathMatcher = AntPathMatcher()

    // Prefix для клиент→сервер (из WebSocketConfig.configureMessageBroker).
    private val appPrefix = "/app"

    // Prefix для @SendToUser — Spring клеит "/user" перед destination.
    private val userPrefix = "/user"

    @Test
    fun `все @MessageMapping и @SendToUser destinations покрыты matcher'ами`() {
        val patterns = extractMatcherPatterns()
        assertTrue(patterns.isNotEmpty(), "Не удалось извлечь ни одного pattern'а из WebSocketSecurityConfig.kt")

        val messageDestinations = collectMessageMappingDestinations()
        val sendToUserDestinations = collectSendToUserDestinations()

        val missing = mutableListOf<String>()

        // Sanity check: ClassPath-scan должен что-то найти. Если пусто — тест был бы
        // vacuously true и не поймал бы регрессии.
        assertTrue(
            messageDestinations.isNotEmpty(),
            "ClassPath-scan не нашёл ни одного @MessageMapping — тест сломан"
        )
        assertTrue(
            sendToUserDestinations.isNotEmpty(),
            "ClassPath-scan не нашёл ни одного @SendToUser — тест сломан"
        )

        messageDestinations.forEach { dest ->
            val normalized = normalizeVariables(dest)
            if (patterns.none { pathMatcher.match(it, normalized) }) {
                missing += "SEND $dest (нормализовано → $normalized) не покрыт"
            }
        }

        sendToUserDestinations.forEach { dest ->
            val normalized = normalizeVariables(dest)
            if (patterns.none { pathMatcher.match(it, normalized) }) {
                missing += "SUBSCRIBE $dest (нормализовано → $normalized) не покрыт"
            }
        }

        if (missing.isNotEmpty()) {
            val msg = "WebSocketSecurityConfig не покрывает следующие destinations:\n" +
                missing.joinToString("\n") +
                "\n\nИзвлечённые pattern'ы:\n" +
                patterns.joinToString("\n")
            fail(msg)
        }
    }

    // Читает WebSocketSecurityConfig.kt как текст и вытаскивает все pattern'ы из
    // .simpDestMatchers("...") и .simpSubscribeDestMatchers("...").
    // Reflection на AuthorizationManager не подходит — публичного API для enumerate'а
    // rules нет. Перед regex-парсингом удаляем `//`-комментарии, чтобы закомментированные
    // matcher'ы не считались активными.
    private fun extractMatcherPatterns(): List<String> {
        val configFile = File("src/main/kotlin/edu/itmo/ultimatumgame/configs/WebSocketSecurityConfig.kt")
        assertTrue(configFile.exists(), "Не найден файл $configFile — тест запускается из корня проекта?")
        val source = configFile.readText()
        val cleaned = source.lineSequence()
            .map { line -> line.replaceFirst(Regex("\\s*//.*"), "") }
            .joinToString("\n")
        val regex = Regex("""\.(?:simpDestMatchers|simpSubscribeDestMatchers)\(\s*"([^"]+)"\s*\)""")
        return regex.findAll(cleaned).map { it.groupValues[1] }.toList()
    }

    private fun collectMessageMappingDestinations(): List<String> =
        scanClasses("edu.itmo.ultimatumgame.controllers.ws")
            .flatMap { clazz ->
                clazz.methods.filter { it.isAnnotationPresent(MessageMapping::class.java) }
                    .flatMap { method ->
                        method.getAnnotation(MessageMapping::class.java).value
                            .map { "$appPrefix/${it.trimStart('/')}" }
                    }
            }

    private fun collectSendToUserDestinations(): List<String> =
        scanClasses("edu.itmo.ultimatumgame")
            .flatMap { clazz ->
                clazz.methods.filter { it.isAnnotationPresent(SendToUser::class.java) }
                    .flatMap { method ->
                        method.getAnnotation(SendToUser::class.java).value
                            .map { "$userPrefix/${it.trimStart('/')}" }
                    }
            }

    // useDefaultFilters=true подхватывает @Component, @Controller, @Service, @Repository,
    // @ControllerAdvice (наследует @Component) — всё что нам нужно для скана.
    private fun scanClasses(basePackage: String): List<Class<*>> {
        val provider = ClassPathScanningCandidateComponentProvider(true)
        return provider.findCandidateComponents(basePackage)
            .mapNotNull { def ->
                runCatching { Class.forName(def.beanClassName!!) }.getOrNull()
            }
    }

    // Заменяет `{var}`-плейсхолдеры на `*` для сравнения через AntPathMatcher.
    // Пример: session/{sessionId}/offer.create → session/*/offer.create.
    private fun normalizeVariables(destination: String): String =
        destination.replace(Regex("\\{[^}]+\\}"), "*")
}
