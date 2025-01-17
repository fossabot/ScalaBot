package util

import com.google.gson.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lamgc.scalabot.MavenRepositoryConfig
import net.lamgc.scalabot.util.AuthenticationSerializer
import net.lamgc.scalabot.util.MavenRepositoryConfigSerializer
import net.lamgc.scalabot.util.ProxyTypeSerializer
import org.eclipse.aether.repository.Authentication
import org.eclipse.aether.repository.AuthenticationContext
import org.eclipse.aether.repository.Proxy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertThrows
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.net.URL
import kotlin.test.*

internal class SerializersKtTest {

    private val instance: Any
    private val method: Method

    init {
        val clazz = Class.forName("net.lamgc.scalabot.util.SerializerUtils")
        method = clazz.getDeclaredMethod("checkJsonKey", JsonObject::class.java, String::class.java)
        method.isAccessible = true
        instance = clazz.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.get(null)
    }

    private fun invoke(json: JsonObject, key: String): String {
        try {
            return method.invoke(instance, json, key) as String
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun `Json key checker test`() {
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject(), "NOT_EXIST_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("NULL_KEY", JsonNull.INSTANCE) }, "NULL_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("ARRAY_KEY", JsonArray()) }, "ARRAY_KEY")
        }
        assertThrows(JsonParseException::class.java) {
            invoke(JsonObject().apply { add("OBJECT_KEY", JsonObject()) }, "OBJECT_KEY")
        }

        val expectKey = "TEST"
        val expectString = "testString"
        val json = JsonObject().apply { addProperty(expectKey, expectString) }

        assertEquals(expectString, invoke(json, expectKey))
    }
}

internal class ProxyTypeSerializerTest {

    @Test
    fun `serialize test`() {
        for (type in DefaultBotOptions.ProxyType.values()) {
            assertEquals(
                JsonPrimitive(type.name), ProxyTypeSerializer.serialize(type, null, null),
                "ProxyType 序列化结果与预期不符."
            )
        }
    }

    @Test
    fun `deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonObject(), null, null)
        }
        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonArray(), null, null)
        }

        assertThrows(JsonParseException::class.java) {
            ProxyTypeSerializer.deserialize(JsonPrimitive("NOT_IN_ENUM_VALUE"), null, null)
        }

        assertEquals(
            DefaultBotOptions.ProxyType.NO_PROXY,
            ProxyTypeSerializer.deserialize(JsonNull.INSTANCE, null, null)
        )

        for (type in DefaultBotOptions.ProxyType.values()) {
            assertEquals(
                type, ProxyTypeSerializer.deserialize(JsonPrimitive(type.name), null, null),
                "ProxyType 反序列化结果与预期不符."
            )

            assertEquals(
                type, ProxyTypeSerializer.deserialize(JsonPrimitive("  ${type.name}   "), null, null),
                "ProxyType 反序列化时未对 Json 字符串进行修剪(trim)."
            )
        }

    }

}

internal class MavenRepositoryConfigSerializerTest {

    @Test
    fun `unsupported json type deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            MavenRepositoryConfigSerializer.deserialize(
                JsonArray(),
                MavenRepositoryConfig::class.java,
                TestJsonDeserializationContext
            )
        }
        assertThrows(JsonParseException::class.java) {
            MavenRepositoryConfigSerializer.deserialize(
                JsonNull.INSTANCE,
                MavenRepositoryConfig::class.java,
                TestJsonDeserializationContext
            )
        }
    }

    @Test
    fun `json primitive deserialize test`() {
        val expectRepoUrl = "https://repo.example.org/maven"
        val config = MavenRepositoryConfigSerializer.deserialize(
            JsonPrimitive(expectRepoUrl),
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertNull(config.id)
        assertEquals(URL(expectRepoUrl), config.url)
        assertNull(config.proxy, "Proxy 默认值不为 null.")
        assertEquals("default", config.layout)
        assertTrue(config.enableReleases)
        assertTrue(config.enableSnapshots)
        assertNull(config.authentication)
    }

    @Test
    fun `json object default deserialize test`() {
        val expectRepoUrl = "https://repo.example.org/maven"
        val jsonObject = JsonObject()
        jsonObject.addProperty("url", expectRepoUrl)
        val config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertNull(config.id)
        assertEquals(URL(expectRepoUrl), config.url)
        assertNull(config.proxy, "Proxy 默认值不为 null.")
        assertEquals("default", config.layout)
        assertTrue(config.enableReleases)
        assertTrue(config.enableSnapshots)
        assertNull(config.authentication)
    }

    @Test
    fun `json object deserialize test`() {
        @Language("JSON5")
        val looksGoodJsonString = """
            {
              "id": "test-repository",
              "url": "https://repo.example.org/maven",
              "proxy": {
                "type": "http",
                "host": "127.0.1.1",
                "port": 10800
              },
              "layout": "default",
              "enableReleases": false,
              "enableSnapshots": true
            }
        """.trimIndent()

        val jsonObject = Gson().fromJson(looksGoodJsonString, JsonObject::class.java)
        var config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertEquals(Proxy("http", "127.0.1.1", 10800), config.proxy)
        assertEquals(jsonObject["layout"].asString, config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)

        // ------------------------------------

        jsonObject.add("proxy", JsonNull.INSTANCE)
        jsonObject.remove("layout")

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)

        // ------------------------------------

        jsonObject.add("authentication", JsonArray())
        jsonObject.add("layout", mockk<JsonPrimitive> {
            every { asString }.returns(null)
        })

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)
        assertNull(config.authentication)

        // ------------------------------------

        jsonObject.add("authentication", JsonObject().apply {
            addProperty("username", "testUsername")
            addProperty("password", "testPassword")
        })

        config = MavenRepositoryConfigSerializer.deserialize(
            jsonObject,
            MavenRepositoryConfig::class.java,
            TestJsonDeserializationContext
        )

        assertEquals(jsonObject["id"].asString, config.id)
        assertEquals(URL(jsonObject["url"].asString), config.url)
        assertNull(config.proxy)
        assertEquals("default", config.layout)
        assertEquals(jsonObject["enableReleases"].asBoolean, config.enableReleases)
        assertEquals(jsonObject["enableSnapshots"].asBoolean, config.enableSnapshots)
        assertNotNull(config.authentication)
    }

}

private object TestJsonDeserializationContext : JsonDeserializationContext {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Authentication::class.java, AuthenticationSerializer)
        .create()

    override fun <T : Any?> deserialize(json: JsonElement, typeOfT: Type): T {
        return gson.fromJson(json, typeOfT)
    }
}

internal class AuthenticationSerializerTest {

    @Test
    fun `deserialize test`() {
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonNull.INSTANCE,
                Authentication::class.java, TestJsonDeserializationContext
            )
        }
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonArray(),
                Authentication::class.java, TestJsonDeserializationContext
            )
        }
        assertThrows(JsonParseException::class.java) {
            AuthenticationSerializer.deserialize(
                JsonPrimitive("A STRING"),
                Authentication::class.java, TestJsonDeserializationContext
            )
        }

        val expectJsonObject = JsonObject().apply {
            addProperty("username", "testUsername")
            addProperty("password", "testPassword")
        }

        val mockContext = mockk<AuthenticationContext> {
            every { put(any(), any()) }.answers { }
        }

        val result = AuthenticationSerializer.deserialize(
            expectJsonObject,
            Authentication::class.java, TestJsonDeserializationContext
        )

        assertNotNull(result)
        result.fill(mockContext, "username", null)
        result.fill(mockContext, "password", null)

        verify {
            mockContext.put("username", "testUsername")
            mockContext.put("password", "testPassword".toCharArray())
        }
    }

}
