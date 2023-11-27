import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.raysmith.notifierbot.botString
import ru.raysmith.notifierbot.inPeriod
import java.lang.reflect.Method
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class UnitTests {

    private fun durationOf(millis: Long): Duration = Duration.of(millis, ChronoUnit.MILLIS)

    @Test
    fun secondsFormat() {
        assertThat(durationOf(0).botString()).isEqualTo("0s.")
        assertThat(durationOf(38000).botString()).isEqualTo("38s.")
        assertThat(durationOf(38139).botString()).isEqualTo("38s.")
        assertThat(durationOf(329123).botString()).isEqualTo("5m. 29s.")
        assertThat(durationOf(300000).botString()).isEqualTo("5m.")
        assertThat(durationOf(7200000).botString()).isEqualTo("2h.")
        assertThat(durationOf(8354021).botString()).isEqualTo("2h. 19m. 14s.")
    }

    @Test
    fun inPeriodTwoDays() {
        val time = LocalTime.of(12, 0)

        fun assertOfHours(start: Int, end: Int) =
            assertThat(time.inPeriod(LocalTime.of(start, 0), LocalTime.of(end, 0)))

        assertOfHours(start = 0, end = 0).isEqualTo(true)
        assertOfHours(start = 10, end = 14).isEqualTo(true)
        assertOfHours(start = 10, end = 11).isEqualTo(false)
        assertOfHours(start = 20, end = 23).isEqualTo(false)
        assertOfHours(start = 20, end = 11).isEqualTo(false)
        assertOfHours(start = 12, end = 15).isEqualTo(true)
        assertOfHours(start = 10, end = 12).isEqualTo(true)

    }

    @Test
    fun `check that all button callback queries do not start with each other`() {
        val callbackQueriesClass = Class.forName("ru.raysmith.notifierbot.ButtonsKt") // set buttons file package
        val constructor = Class.forName("ru.raysmith.tgbot.model.network.CallbackQuery\$Companion")
            .getDeclaredConstructor().apply { isAccessible = true }

        val constructorInstance = constructor.newInstance()

        fun Method.fieldName() = this.name.substring(3)
        callbackQueriesClass.methods
            .filter { it.parameterTypes.firstOrNull()?.name == constructor.declaringClass.name }
            .also { methods ->
                methods.forEach { method ->
                    val value = method.invoke(constructorInstance, constructorInstance).toString()
                    methods.forEach { compMethod ->
                        val comp = compMethod.invoke(constructorInstance, constructorInstance).toString()
                        assertThat(comp.startsWith(value) && compMethod != method)
                            .withFailMessage("Found a query of extension <${method.fieldName()}> with an occurrence in <${compMethod.fieldName()}>")
                            .isEqualTo(false)
                    }
                }
            }
    }
}