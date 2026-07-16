package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.dto.responses.MyRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Регресс-тест на T-072: MapStruct-сгенерированный `toDto` не должен передавать `null`
 * в non-null-параметры конструктора `RoundResponse`. Раньше `myRole` и `myPendingActions`
 * были в первичном конструкторе Kotlin `data class` с default'ами — Java-код от MapStruct
 * не умеет использовать defaults и передавал `null`, что вызывало `NullPointerException`
 * при `checkNotNullParameter`. Fix — вынести эти поля из первичного конструктора в тело
 * класса.
 */
class RoundMapperTest {

    // Реальные (не mockk) MapStruct-сгенерированные imp'ы.
    private val userMapper = UserMapperImpl()
    private val offerPrewMapper = OfferPrewMapperImpl().apply {
        // MapStruct-полей нет — простой конструктор
    }
    private val sessionConfigMapper = SessionConfigMapperImpl()
    private val sessionPrewMapper = SessionPrewMapperImpl()
    private val decisionPrewMapper = DecisionPrewMapperImpl()

    private val mapper: RoundMapper = RoundMapperImpl()

    init {
        // MapStruct-imp'ы для nested-мапперов инжектятся через сеттеры —
        // в тесте вручную (иначе NPE на nested-мапперах).
        RoundMapperImpl::class.java.getDeclaredField("offerPrewMapper").apply {
            isAccessible = true
            set(mapper, offerPrewMapper)
        }
        RoundMapperImpl::class.java.getDeclaredField("decisionPrewMapper").apply {
            isAccessible = true
            set(mapper, decisionPrewMapper)
        }
        RoundMapperImpl::class.java.getDeclaredField("sessionPrewMapper").apply {
            isAccessible = true
            set(mapper, sessionPrewMapper)
        }
        OfferPrewMapperImpl::class.java.getDeclaredField("userMapper").apply {
            isAccessible = true
            set(offerPrewMapper, userMapper)
        }
        DecisionPrewMapperImpl::class.java.getDeclaredField("userMapper").apply {
            isAccessible = true
            set(decisionPrewMapper, userMapper)
        }
        DecisionPrewMapperImpl::class.java.getDeclaredField("offerPrewMapper").apply {
            isAccessible = true
            set(decisionPrewMapper, offerPrewMapper)
        }
        SessionPrewMapperImpl::class.java.getDeclaredField("sessionConfigMapper").apply {
            isAccessible = true
            set(sessionPrewMapper, sessionConfigMapper)
        }
        SessionPrewMapperImpl::class.java.getDeclaredField("userMapper").apply {
            isAccessible = true
            set(sessionPrewMapper, userMapper)
        }
    }

    @Test
    fun `toDto — myRole default NONE, myPendingActions default emptyList (T-072 regression)`() {
        val s = session()
        val r = round(session = s)

        // До fix'а этот вызов падал NPE:
        // "Parameter specified as non-null is null: parameter myRole"
        val dto = mapper.toDto(r)

        assertEquals(MyRole.NONE, dto.myRole)
        assertTrue(dto.myPendingActions.isEmpty())
        assertEquals(r.id, dto.id)
        assertEquals(r.roundPhase, dto.roundPhase)
    }
}
