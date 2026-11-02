package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * アイテムテンプレート、YAML ローダー、マネージャーの状態管理を検証する。
 *
 * サーバーを必要としない範囲だけを対象とし、実際の [org.bukkit.inventory.ItemStack] の生成やイベント発火は
 * サーバー統合テストに委ねる。
 */
class ShimeItemApiTest {
    private fun manager(): ShimeItemManager {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.name).thenReturn("shime-item-test")
        return ShimeItemManager(plugin, ShimeLogger(Logger.getLogger("item-test")))
    }

    private fun player(uuid: UUID): Player {
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(uuid)
        return player
    }

    private fun template(): ShimeItemTemplate = ShimeItemTemplate(
        material = Material.DIAMOND_SWORD,
        amount = 1,
        displayName = "Sword"
    )

    @Test
    fun `テンプレートはYAMLセクションから読み込める`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString(
            """
            material: DIAMOND_SWORD
            amount: 2
            name: "<aqua>Sword</aqua>"
            lore:
              - "<gray>Line</gray>"
            flags:
              - HIDE_ATTRIBUTES
            custom-model-data: 42
            unbreakable: true
            damage: 5
            persistent-data:
              key: value
            """.trimIndent()
        )

        val template = ShimeItemTemplate.fromSection(yaml)

        assertEquals(Material.DIAMOND_SWORD, template.material)
        assertEquals(2, template.amount)
        assertEquals("<aqua>Sword</aqua>", template.displayName)
        assertEquals(listOf("<gray>Line</gray>"), template.lore)
        assertEquals(setOf(ItemFlag.HIDE_ATTRIBUTES), template.flags)
        assertEquals(42, template.customModelData)
        assertTrue(template.unbreakable)
        assertEquals(5, template.damage)
        assertEquals(mapOf("key" to "value"), template.persistentData)
    }

    @Test
    fun `未知のMaterialは拒否される`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString("material: NOT_A_REAL_MATERIAL\n")

        assertFailsWith<ShimeValidationException> {
            ShimeItemTemplate.fromSection(yaml)
        }
    }

    @Test
    fun `YAMLローダーはitemsセクションを読み込む`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString(
            """
            items:
              example_sword:
                material: DIAMOND_SWORD
                name: "Example"
              example_apple:
                material: APPLE
                amount: 3
            """.trimIndent()
        )

        val templates = ShimeItemYamlLoader.load(yaml)

        assertEquals(setOf("example_sword", "example_apple"), templates.keys)
        assertEquals(3, templates.getValue("example_apple").amount)
    }

    @Test
    fun `itemsセクションがないYAMLは拒否される`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString("other: true\n")

        assertFailsWith<ShimeValidationException> {
            ShimeItemYamlLoader.load(yaml)
        }
    }

    @Test
    fun `テンプレート定義を登録できる`() {
        val manager = manager()
        manager.registerTemplate("example", template(), version = 2)

        assertTrue(manager.ids().contains("example"))
        val definition = manager.get("example")
        assertNotNull(definition)
        assertEquals(2, definition.version)
        assertEquals(template(), definition.template)
    }

    @Test
    fun `重複登録は拒否されreplaceで置き換えられる`() {
        val manager = manager()
        manager.registerTemplate("example", template())

        assertFailsWith<IllegalArgumentException> {
            manager.registerTemplate("example", template())
        }

        manager.registerTemplate("example", template().withAmount(5), replace = true)
        assertEquals(5, manager.get("example")?.template?.amount)
    }

    @Test
    fun `複数登録は重複時に何も登録しない`() {
        val manager = manager()
        manager.registerTemplate("existing", template())

        assertFailsWith<IllegalArgumentException> {
            manager.registerTemplates(
                mapOf(
                    "existing" to template(),
                    "new-entry" to template()
                )
            )
        }

        assertFalse(manager.ids().contains("new-entry"))
    }

    @Test
    fun `プレイヤーごとの有効設定を管理できる`() {
        val manager = manager()
        val player = player(UUID.randomUUID())

        assertTrue(manager.isEnabled(player, "example"))
        manager.setEnabled(player, "example", false)

        assertFalse(manager.isEnabled(player, "example"))
        assertEquals(setOf("example"), manager.disabledIds(player))

        manager.setEnabled(player, "example", true)
        assertTrue(manager.isEnabled(player, "example"))
        assertTrue(manager.disabledIds(player).isEmpty())
    }

    @Test
    fun `クールダウンを設定して解除できる`() {
        val manager = manager()
        val player = player(UUID.randomUUID())

        manager.setCooldown(player, "example", 60_000)
        assertTrue(manager.remainingCooldown(player, "example") > 0)

        manager.clearCooldown(player, "example")
        assertEquals(0L, manager.remainingCooldown(player, "example"))
    }

    @Test
    fun `未使用の統計はゼロを返す`() {
        val manager = manager()
        val player = player(UUID.randomUUID())

        assertEquals(0L, manager.usageCount("example"))
        assertEquals(0L, manager.usageCount(player, "example"))
        assertNull(manager.lastUsedMillis(player, "example"))
        assertTrue(manager.usageCounts().isEmpty())
    }

    @Test
    fun `カスタムアイテムでない場合は取引可能と判定される`() {
        val manager = manager()

        assertTrue(manager.canTrade(null, UUID.randomUUID()))
        assertFalse(manager.isSameInstance(null, null))
        assertNull(manager.instanceIdOf(null))
    }

    @Test
    fun `配布結果と検証結果は整合性を検証する`() {
        val distribution = ShimeItemDistribution(requested = 10, inserted = 7, dropped = 3)
        assertFalse(distribution.fullyInserted)
        assertTrue(ShimeItemDistribution(2, 2, 0).fullyInserted)

        assertFailsWith<IllegalArgumentException> {
            ShimeItemDistribution(requested = 10, inserted = 7, dropped = 2)
        }

        val validation = ShimeItemValidation(valid = false, issues = listOf("Material must not be air."))
        assertFalse(validation.valid)
        assertEquals(1, validation.issues.size)

        assertFailsWith<IllegalArgumentException> {
            ShimeItemValidation(valid = true, issues = listOf("unexpected"))
        }
    }
}
