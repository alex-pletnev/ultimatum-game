package edu.itmo.ultimatumgame.util

import org.slf4j.MDC
import java.util.UUID

/**
 * Хелперы для расстановки MDC-полей вокруг игровых операций.
 * Каждый `withXxxMdc` кладёт ключ на входе и снимает на выходе (try/finally),
 * поэтому thread-local MDC не «протекает» между запросами/сообщениями.
 *
 * Ключи здесь строго доменные (sessionId/roundId). `userId`/`role` ставятся
 * в filter'ах аутентификации; `traceId`/`spanId` — Micrometer Tracing.
 */

const val MDC_SESSION_ID = "sessionId"
const val MDC_ROUND_ID = "roundId"

inline fun <T> withSessionMdc(sessionId: UUID, block: () -> T): T {
    MDC.put(MDC_SESSION_ID, sessionId.toString())
    try {
        return block()
    } finally {
        MDC.remove(MDC_SESSION_ID)
    }
}

inline fun <T> withRoundMdc(roundId: UUID, block: () -> T): T {
    MDC.put(MDC_ROUND_ID, roundId.toString())
    try {
        return block()
    } finally {
        MDC.remove(MDC_ROUND_ID)
    }
}
