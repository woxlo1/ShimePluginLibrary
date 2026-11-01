package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeReflectionTest {
    private class ShimeReflectionFixture(@JvmField var value: Int) {
        @Suppress("unused")
        private fun doubled(input: Int): Int = input * 2

        @Suppress("unused")
        private fun failing(): Nothing = throw IllegalStateException("fixture failure")
    }

    @Test
    fun `finds classes without initializing them`() {
        val reflection = ShimeReflection()

        assertEquals(String::class.java, reflection.findClass("java.lang.String"))
        assertTrue(reflection.hasClass("java.lang.String"))
        assertNull(reflection.findClass("shimeserver.missing.ShimeMissingClass"))
    }

    @Test
    fun `requireClass reports a missing class`() {
        val reflection = ShimeReflection()
        val exception = assertFailsWith<ShimeNmsReflectionException> {
            reflection.requireClass("shimeserver.missing.ShimeMissingClass")
        }
        assertTrue(exception.message!!.contains("ShimeMissingClass"))
    }

    @Test
    fun `finds and invokes methods including private members`() {
        val reflection = ShimeReflection()
        val fixture = ShimeReflectionFixture(3)

        val method = reflection.findMethod(
            ShimeReflectionFixture::class.java,
            "doubled",
            Int::class.javaPrimitiveType!!
        )
        assertNotNull(method)
        assertEquals(6, reflection.invoke(method, fixture, 3))
    }

    @Test
    fun `wraps invocation failures with the original cause`() {
        val reflection = ShimeReflection()
        val fixture = ShimeReflectionFixture(0)
        val method = reflection.findMethod(ShimeReflectionFixture::class.java, "failing")!!

        val exception = assertFailsWith<ShimeNmsReflectionException> {
            reflection.invoke(method, fixture)
        }
        assertTrue(exception.cause is IllegalStateException)
        assertEquals("fixture failure", exception.cause!!.message)
    }

    @Test
    fun `reads and writes fields`() {
        val reflection = ShimeReflection()
        val fixture = ShimeReflectionFixture(1)
        val field = reflection.findField(ShimeReflectionFixture::class.java, "value")!!

        assertEquals(1, reflection.getField(field, fixture))
        reflection.setField(field, fixture, 9)
        assertEquals(9, fixture.value)
    }

    @Test
    fun `returns null for missing members`() {
        val reflection = ShimeReflection()

        assertNull(reflection.findMethod(ShimeReflectionFixture::class.java, "missing"))
        assertNull(reflection.findField(ShimeReflectionFixture::class.java, "missing"))
    }

    @Test
    fun `rejects blank lookup names`() {
        val reflection = ShimeReflection()
        assertFailsWith<IllegalArgumentException> { reflection.findClass(" ") }
        assertFailsWith<IllegalArgumentException> {
            reflection.findMethod(ShimeReflectionFixture::class.java, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            reflection.findField(ShimeReflectionFixture::class.java, " ")
        }
    }

    @Test
    fun `clearCache allows repeated lookups`() {
        val reflection = ShimeReflection()
        assertNull(reflection.findClass("shimeserver.missing.ShimeMissingClass"))
        reflection.clearCache()
        assertNull(reflection.findClass("shimeserver.missing.ShimeMissingClass"))
    }
}
