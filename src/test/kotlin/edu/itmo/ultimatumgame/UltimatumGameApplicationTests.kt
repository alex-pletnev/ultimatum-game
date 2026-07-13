package edu.itmo.ultimatumgame

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UltimatumGameApplicationTests {

    @Test
    fun contextLoads() {
        // Пустой smoke-тест: проверяет, что Spring-контекст поднимается.
        // EmptyFunctionBlock отключён для test-scope в detekt.yml.
    }
}
