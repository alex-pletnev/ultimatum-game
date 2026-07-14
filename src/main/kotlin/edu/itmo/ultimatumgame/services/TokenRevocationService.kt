package edu.itmo.ultimatumgame.services

import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Хранит `jti` отозванных JWT. In-memory реализация для MVP — состояние
 * теряется при рестарте приложения, TTL access-токена (15 мин, см. T-056)
 * ограничивает окно уязвимости. Для прод — заменить на Redis/DB.
 */
@Service
class TokenRevocationService {

    private val revoked: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun revoke(jti: UUID) {
        revoked.add(jti)
    }

    fun isRevoked(jti: UUID): Boolean = jti in revoked
}
