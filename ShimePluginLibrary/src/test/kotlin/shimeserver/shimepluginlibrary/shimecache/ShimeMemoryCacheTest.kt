package shimeserver.shimepluginlibrary.shimecache

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeMemoryCacheTest {
    @Test
    fun `stores and retrieves values`() {
        val cache = ShimeMemoryCache<String, Int>()
        assertNull(cache.put("a", 1))
        assertEquals(1, cache.get("a"))
        assertEquals(1, cache.size())
        assertTrue(cache.contains("a"))
        assertEquals(setOf("a"), cache.keys())
    }

    @Test
    fun `put returns the previous value`() {
        val cache = ShimeMemoryCache<String, Int>()
        cache.put("a", 1)
        assertEquals(1, cache.put("a", 2))
        assertEquals(2, cache.get("a"))
    }

    @Test
    fun `evicts least recently used entry beyond capacity`() {
        val cache = ShimeMemoryCache<String, Int>(maxSize = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)

        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
        assertEquals(1, cache.stats().evictions)
    }

    @Test
    fun `expires entries using the supplied clock`() {
        val now = AtomicLong(1_000)
        val cache = ShimeMemoryCache<String, Int>(timeSource = now::get)
        cache.put("a", 1, ttlMillis = 500)
        assertEquals(1, cache.get("a"))

        now.addAndGet(500)
        assertNull(cache.get("a"))
        assertEquals(1, cache.stats().expirations)
        assertFalse(cache.contains("a"))
    }

    @Test
    fun `zero ttl means no expiry`() {
        val now = AtomicLong(0)
        val cache = ShimeMemoryCache<String, Int>(defaultTtlMillis = 0, timeSource = now::get)
        cache.put("a", 1)
        now.set(Long.MAX_VALUE / 2)
        assertEquals(1, cache.get("a"))
    }

    @Test
    fun `rejects invalid construction and negative ttl`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ShimeMemoryCache<String, Int>(maxSize = 0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ShimeMemoryCache<String, Int>(defaultTtlMillis = -1)
        }
        val cache = ShimeMemoryCache<String, Int>()
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            cache.put("a", 1, ttlMillis = -1)
        }
    }

    @Test
    fun `tracks hit rate and clears state`() {
        val cache = ShimeMemoryCache<String, Int>()
        cache.put("a", 1)
        cache.get("a")
        cache.get("missing")

        val stats = cache.stats()
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(0.5, stats.hitRate)

        cache.clear()
        assertEquals(0, cache.size())
    }
}
