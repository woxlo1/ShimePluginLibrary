package shimeserver.shimepluginlibrary.shimecache

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShimeLoadingCacheTest {
    @Test
    fun `loads a missing value once and caches it`() {
        val loads = AtomicInteger()
        val cache = ShimeLoadingCache<String, String>(ShimeMemoryCache()) { key ->
            loads.incrementAndGet()
            key.uppercase()
        }

        assertEquals("ABC", cache.getOrLoad("abc"))
        assertEquals("ABC", cache.getOrLoad("abc"))
        assertEquals(1, loads.get())
        assertEquals(1, cache.loadCount())
    }

    @Test
    fun `does not cache null results`() {
        val loads = AtomicInteger()
        val cache = ShimeLoadingCache<String, String>(ShimeMemoryCache()) {
            loads.incrementAndGet()
            null
        }

        assertNull(cache.getOrLoad("missing"))
        assertNull(cache.getOrLoad("missing"))
        assertEquals(2, loads.get())
    }

    @Test
    fun `propagates loader failures and counts them`() {
        val cache = ShimeLoadingCache<String, String>(ShimeMemoryCache()) {
            throw IllegalStateException("loader failed")
        }

        assertFailsWith<IllegalStateException> { cache.getOrLoad("key") }
        assertEquals(1, cache.loadFailureCount())
        assertEquals(0, cache.loadCount())
    }

    @Test
    fun `concurrent requests share one load`() {
        val loaderStarted = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val loads = AtomicInteger()
        val cache = ShimeLoadingCache<String, String>(ShimeMemoryCache()) { key ->
            loads.incrementAndGet()
            loaderStarted.countDown()
            releaseLoader.await(5, TimeUnit.SECONDS)
            key
        }
        val executor = Executors.newFixedThreadPool(4)

        try {
            val first = executor.submit<String?> { cache.getOrLoad("shared") }
            loaderStarted.await(5, TimeUnit.SECONDS)
            val second = executor.submit<String?> { cache.getOrLoad("shared") }
            Thread.sleep(100)
            releaseLoader.countDown()

            assertEquals("shared", first.get(5, TimeUnit.SECONDS))
            assertEquals("shared", second.get(5, TimeUnit.SECONDS))
            assertEquals(1, loads.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `async load completes through the supplied executor`() {
        val cache = ShimeLoadingCache<String, Int>(ShimeMemoryCache()) { it.length }
        val executor = Executors.newSingleThreadExecutor()

        try {
            assertEquals(5, cache.getOrLoadAsync("hello", executor).get(5, TimeUnit.SECONDS))
            assertEquals(5, cache.get("hello"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `delegates cache operations`() {
        val cache = ShimeLoadingCache<String, Int>(ShimeMemoryCache()) { it.length }
        cache.put("a", 7)

        assertEquals(7, cache.get("a"))
        assertEquals(true, cache.contains("a"))
        assertEquals(setOf("a"), cache.keys())
        assertEquals(7, cache.remove("a"))
        cache.clear()
        assertEquals(0, cache.size())
    }
}
