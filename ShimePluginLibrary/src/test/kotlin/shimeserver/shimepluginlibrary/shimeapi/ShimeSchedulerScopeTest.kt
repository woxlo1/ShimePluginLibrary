package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * スコープのスケジューラーがタスクを利用側プラグインに紐づけ、解放時に取り消すことを検証する。
 *
 * Bukkit のスケジューラーはモックし、タスクの所有者とキャンセルだけを対象にする。
 */
class ShimeSchedulerScopeTest {
    private lateinit var bukkitScheduler: BukkitScheduler
    private lateinit var tasks: MutableList<BukkitTask>
    private var nextTaskId = 0

    @BeforeEach
    fun installMockServer() {
        bukkitScheduler = Mockito.mock(BukkitScheduler::class.java)
        tasks = Collections.synchronizedList(ArrayList())
        nextTaskId = 0
        stubDelayedTasks()
        stubRepeatingTasks()

        val server = Mockito.mock(Server::class.java)
        Mockito.`when`(server.isPrimaryThread()).thenReturn(true)
        Mockito.`when`(server.scheduler).thenReturn(bukkitScheduler)
        setBukkitServer(server)
    }

    @AfterEach
    fun removeMockServer() {
        setBukkitServer(null)
    }

    private fun stubDelayedTasks() {
        Mockito.`when`(
            bukkitScheduler.runTask(Mockito.any(JavaPlugin::class.java), Mockito.any(Runnable::class.java))
        ).thenAnswer { newTask() }
        Mockito.`when`(
            bukkitScheduler.runTaskAsynchronously(Mockito.any(JavaPlugin::class.java), Mockito.any(Runnable::class.java))
        ).thenAnswer { newTask() }
        Mockito.`when`(
            bukkitScheduler.runTaskLater(
                Mockito.any(JavaPlugin::class.java),
                Mockito.any(Runnable::class.java),
                Mockito.anyLong()
            )
        ).thenAnswer { newTask() }
        Mockito.`when`(
            bukkitScheduler.runTaskLaterAsynchronously(
                Mockito.any(JavaPlugin::class.java),
                Mockito.any(Runnable::class.java),
                Mockito.anyLong()
            )
        ).thenAnswer { newTask() }
    }

    private fun stubRepeatingTasks() {
        Mockito.`when`(
            bukkitScheduler.runTaskTimer(
                Mockito.any(JavaPlugin::class.java),
                Mockito.any(Runnable::class.java),
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenAnswer { newTask() }
        Mockito.`when`(
            bukkitScheduler.runTaskTimerAsynchronously(
                Mockito.any(JavaPlugin::class.java),
                Mockito.any(Runnable::class.java),
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenAnswer { newTask() }
    }

    private fun newTask(): BukkitTask {
        val task = Mockito.mock(BukkitTask::class.java)
        val cancelled = AtomicBoolean(false)
        val id = ++nextTaskId
        Mockito.`when`(task.taskId).thenReturn(id)
        Mockito.`when`(task.isCancelled).thenAnswer { cancelled.get() }
        Mockito.doAnswer { cancelled.set(true) }.`when`(task).cancel()
        tasks += task
        return task
    }

    private fun setBukkitServer(server: Server?) {
        val field: Field = Bukkit::class.java.getDeclaredField("server")
        field.isAccessible = true
        field.set(null, server)
    }

    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-scheduler-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(name))
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        return plugin
    }

    private fun library(): ShimeLibrary = ShimeLibrary(plugin("shimepluginlibrary"))

    @Test
    fun `tasks are scheduled with the consuming plugin as their owner`() {
        val library = library()
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)

        scope.scheduler.runSync {}
        scope.scheduler.runLater(20L) {}
        scope.scheduler.runRepeating(20L, 40L) {}

        assertEquals(3, tasks.size)
        Mockito.verify(bukkitScheduler, Mockito.times(1))
            .runTask(Mockito.eq(consumer), Mockito.any(Runnable::class.java))
        Mockito.verify(bukkitScheduler, Mockito.times(1))
            .runTaskLater(Mockito.eq(consumer), Mockito.any(Runnable::class.java), Mockito.eq(20L))
        Mockito.verify(bukkitScheduler, Mockito.times(1))
            .runTaskTimer(Mockito.eq(consumer), Mockito.any(Runnable::class.java), Mockito.eq(20L), Mockito.eq(40L))
        Mockito.verifyNoMoreInteractions(bukkitScheduler)
    }

    @Test
    fun `closing a scope cancels only the tasks it created`() {
        val library = library()
        val consumerScope = library.forPlugin(plugin("QuestPluginV2"))
        val otherScope = library.forPlugin(plugin("OtherPlugin"))

        consumerScope.scheduler.runSync {}
        otherScope.scheduler.runSync {}
        val consumerTask = tasks[0]
        val otherTask = tasks[1]

        consumerScope.close()

        assertTrue(consumerTask.isCancelled)
        assertTrue(!otherTask.isCancelled)

        otherScope.close()

        assertTrue(otherTask.isCancelled)
    }

    @Test
    fun `shutdown cancels every task tracked by scopes`() {
        val library = library()
        val first = library.forPlugin(plugin("FirstPlugin"))
        val second = library.forPlugin(plugin("SecondPlugin"))

        first.scheduler.runAsync {}
        second.scheduler.runAsync {}

        library.shutdown()

        assertTrue(tasks.all { it.isCancelled })
        assertTrue(library.scopes().isEmpty())
    }
}
