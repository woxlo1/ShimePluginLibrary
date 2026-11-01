package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * バージョン固有のコード向けの、キャッシュ付きで null 安全なリフレクションヘルパー。
 *
 * すべての検索は、使用前にクラス、メソッド、フィールドが存在することを検証する。失敗はキャッシュされるため、
 * 存在しないメンバーが毎回の呼び出しで検索されることはない。また、アクセスの問題は握りつぶさずに
 * [ShimeLogger] を通じて報告される。呼び出しの失敗は、元の原因を保持した [ShimeNmsReflectionException] に
 * 変換される。
 *
 * このヘルパーはスレッドセーフである。ネガティブキャッシュのエントリはこのインスタンスの生存期間中有効である。
 * クラスローダーが変わったとき（通常はサーバーの完全なリロード時のみ）は [clearCache] を呼ぶこと。
 */
class ShimeReflection @JvmOverloads constructor(
    private val logger: ShimeLogger? = null,
    private val classLoader: ClassLoader? = null
) {
    private val classes = ConcurrentHashMap<String, Optional<Class<*>>>()
    private val methods = ConcurrentHashMap<String, Optional<Method>>()
    private val fields = ConcurrentHashMap<String, Optional<Field>>()

    private fun loader(): ClassLoader {
        return classLoader
            ?: Thread.currentThread().contextClassLoader
            ?: ShimeReflection::class.java.classLoader
    }

    /**
     * クラスを初期化せずに [name] を検索する。
     *
     * クラスが存在しないか、ロードできない場合は `null` を返す。未サポートのサーバーバージョンは
     * 想定内の状態であり致命的なエラーではないため、失敗はデバッグレベルでログに記録される。
     */
    fun findClass(name: String): Class<*>? {
        require(name.isNotBlank()) { "Class name must not be blank." }
        val cached = classes.computeIfAbsent(name) {
            try {
                Optional.of(Class.forName(name, false, loader()))
            } catch (exception: ClassNotFoundException) {
                logger?.debug(ShimeLogCategory.NMS, "Class '$name' is not present: ${exception.message}")
                Optional.empty()
            } catch (error: LinkageError) {
                logger?.debug(ShimeLogCategory.NMS, "Class '$name' could not be linked: ${error.message}")
                Optional.empty()
            } catch (exception: SecurityException) {
                logger?.warn(ShimeLogCategory.NMS, "Access to class '$name' was denied.", exception)
                Optional.empty()
            }
        }
        return cached.orElse(null)
    }

    /** [name] を返す。存在しない場合は [ShimeNmsReflectionException] を投げる。 */
    fun requireClass(name: String): Class<*> {
        return findClass(name) ?: throw ShimeNmsReflectionException("Required class '$name' is not available.")
    }

    /** [name] をこのヘルパーでロードできるかどうかを返す。 */
    fun hasClass(name: String): Boolean = findClass(name) != null

    /** [owner] 上のメソッドを、名前と完全一致するパラメーター型で検索する。 */
    fun findMethod(owner: String, name: String, vararg parameterTypes: Class<*>): Method? {
        val type = findClass(owner) ?: return null
        return findMethod(type, name, *parameterTypes)
    }

    /**
     * [owner] 上のメソッドを、名前と完全一致するパラメーター型で検索する。
     *
     * メソッドはクラス階層内で検索され、その後、公開された継承メソッドの中から検索される。メソッドが
     * 存在しないか、アクセス可能にできない場合は `null` を返す。
     */
    fun findMethod(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        require(name.isNotBlank()) { "Method name must not be blank." }
        val key = methodKey(owner, name, parameterTypes)
        val cached = methods.computeIfAbsent(key) {
            val located = locateMethod(owner, name, parameterTypes)
            if (located == null) {
                logger?.debug(
                    ShimeLogCategory.NMS,
                    "Method '${owner.name}#$name' with ${parameterTypes.size} parameter(s) is not present."
                )
                Optional.empty()
            } else if (!makeAccessible(located)) {
                logger?.warn(
                    ShimeLogCategory.NMS,
                    "Method '${owner.name}#$name' exists but cannot be accessed."
                )
                Optional.empty()
            } else {
                Optional.of(located)
            }
        }
        return cached.orElse(null)
    }

    /** [owner] 上のフィールドを名前で検索する。 */
    fun findField(owner: String, name: String): Field? {
        val type = findClass(owner) ?: return null
        return findField(type, name)
    }

    /**
     * [owner] 上のフィールドを名前で検索し、クラス階層をたどる。
     *
     * フィールドが存在しないか、アクセス可能にできない場合は `null` を返す。
     */
    fun findField(owner: Class<*>, name: String): Field? {
        require(name.isNotBlank()) { "Field name must not be blank." }
        val key = "${owner.name}#$name"
        val cached = fields.computeIfAbsent(key) {
            val located = locateField(owner, name)
            if (located == null) {
                logger?.debug(ShimeLogCategory.NMS, "Field '${owner.name}#$name' is not present.")
                Optional.empty()
            } else if (!makeAccessible(located)) {
                logger?.warn(ShimeLogCategory.NMS, "Field '${owner.name}#$name' exists but cannot be accessed.")
                Optional.empty()
            } else {
                Optional.of(located)
            }
        }
        return cached.orElse(null)
    }

    /** 完全一致するパラメーター型のメソッドが存在し、アクセス可能かどうかを返す。 */
    fun hasMethod(owner: String, name: String, vararg parameterTypes: Class<*>): Boolean {
        return findMethod(owner, name, *parameterTypes) != null
    }

    /** フィールドが存在し、アクセス可能かどうかを返す。 */
    fun hasField(owner: String, name: String): Boolean = findField(owner, name) != null

    /**
     * [target] に対して [method] を呼び出す。
     *
     * @throws ShimeNmsReflectionException 呼び出しが失敗した場合。
     */
    fun invoke(method: Method, target: Any?, vararg arguments: Any?): Any? {
        return try {
            method.invoke(target, *arguments)
        } catch (exception: InvocationTargetException) {
            throw ShimeNmsReflectionException(
                "Invocation of '${method.declaringClass.name}#${method.name}' failed.",
                exception.targetException
            )
        } catch (exception: IllegalAccessException) {
            throw ShimeNmsReflectionException(
                "Invocation of '${method.declaringClass.name}#${method.name}' was denied.",
                exception
            )
        } catch (exception: IllegalArgumentException) {
            throw ShimeNmsReflectionException(
                "Arguments for '${method.declaringClass.name}#${method.name}' do not match its signature.",
                exception
            )
        }
    }

    /** 静的メソッドを呼び出す。 */
    fun invokeStatic(method: Method, vararg arguments: Any?): Any? = invoke(method, null, *arguments)

    /**
     * [target] から [field] を読み取る。
     *
     * @throws ShimeNmsReflectionException フィールドを読み取れない場合。
     */
    fun getField(field: Field, target: Any?): Any? {
        return try {
            field.get(target)
        } catch (exception: IllegalAccessException) {
            throw ShimeNmsReflectionException(
                "Reading '${field.declaringClass.name}#${field.name}' was denied.",
                exception
            )
        } catch (exception: IllegalArgumentException) {
            throw ShimeNmsReflectionException(
                "Target for '${field.declaringClass.name}#${field.name}' has the wrong type.",
                exception
            )
        }
    }

    /**
     * [target] 上の [field] に [value] を書き込む。
     *
     * @throws ShimeNmsReflectionException フィールドに書き込めない場合。
     */
    fun setField(field: Field, target: Any?, value: Any?) {
        try {
            field.set(target, value)
        } catch (exception: IllegalAccessException) {
            throw ShimeNmsReflectionException(
                "Writing '${field.declaringClass.name}#${field.name}' was denied.",
                exception
            )
        } catch (exception: IllegalArgumentException) {
            throw ShimeNmsReflectionException(
                "Value for '${field.declaringClass.name}#${field.name}' has the wrong type.",
                exception
            )
        }
    }

    /** キャッシュされたすべての検索結果をクリアする。 */
    fun clearCache() {
        classes.clear()
        methods.clear()
        fields.clear()
    }

    private fun locateMethod(owner: Class<*>, name: String, parameterTypes: Array<out Class<*>>): Method? {
        var current: Class<*>? = owner
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return try {
            owner.getMethod(name, *parameterTypes)
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun locateField(owner: Class<*>, name: String): Field? {
        var current: Class<*>? = owner
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return try {
            owner.getField(name)
        } catch (_: NoSuchFieldException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun makeAccessible(member: Member): Boolean {
        val accessible = member as? AccessibleObject
        if (accessible != null) {
            try {
                if (accessible.trySetAccessible()) {
                    return true
                }
            } catch (_: SecurityException) {
                // 下の public メンバー判定へ進む。
            }
        }
        return Modifier.isPublic(member.modifiers) &&
            Modifier.isPublic(member.declaringClass.modifiers)
    }

    private fun methodKey(owner: Class<*>, name: String, parameterTypes: Array<out Class<*>>): String {
        val parameters = parameterTypes.joinToString(",") { it.name }
        return "${owner.name}#$name($parameters)"
    }
}
