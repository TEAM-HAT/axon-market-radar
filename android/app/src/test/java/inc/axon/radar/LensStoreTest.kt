package inc.axon.radar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.LensResult
import inc.axon.radar.data.LensStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

/**
 * The private view's trip to GitHub, against a local stand-in for GitHub's API: the key goes only in the
 * Authorization header, the file comes back raw, an unchanged file is not downloaded again, and a refused key
 * is never kept. The lens here is made up: AXON's real view never enters this repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LensStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: TinyServer
    private val seen = mutableListOf<Map<String, String?>>()

    private val lens = """
        {"version":"test","companies":{"acme":{"roles":["competitor"],"threat":"high","markets":["UAE"],"note":"A made-up rival."}},
         "marks":{"acme-launch-2026-10-01":{"mark":"threat","why":"A made-up reason.","next":"Do a made-up thing","owner":"BD"}},
         "ecosystem":[],"markets":[],"watchlist":{"companies":["acme"],"private":[]},"decisions":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        LensStore.forget(ctx)
        server = TinyServer { h ->
            seen += mapOf("auth" to h["authorization"], "accept" to h["accept"], "etag" to h["if-none-match"])
            when {
                h["authorization"] != "Bearer good-key" -> Triple(401, emptyMap(), "{\"message\":\"Bad credentials\"}")
                h["if-none-match"] == "\"v1\"" -> Triple(304, emptyMap(), "")
                else -> Triple(200, mapOf("ETag" to "\"v1\"", "Content-Type" to "application/vnd.github.raw+json"), lens)
            }
        }
        LensStore.api = "http://127.0.0.1:${server.port}/lens"
    }

    @After
    fun tearDown() {
        server.stop()
        LensStore.forget(ctx)
    }

    @Test
    fun aRefusedKeyIsNotKept() {
        val r = LensStore.connect(ctx, "wrong-key")
        assertEquals(LensResult.Refused(401), r)
        assertFalse(LensStore.hasKey(ctx))
        assertNull(LensStore.cached(ctx))
        assertTrue(LensStore.explain(r).startsWith("GitHub didn't accept this key"))
    }

    @Test
    fun aGoodKeyBringsTheLensThenOnlyChanges() {
        val r = LensStore.connect(ctx, "  good-key \n")
        assertTrue(r is LensResult.Updated)
        val l = (r as LensResult.Updated).lens
        assertEquals("high", l.stance("acme")?.threat)
        assertEquals("threat", l.mark("acme-launch-2026-10-01")?.mark)
        assertEquals(1, l.steps.size)
        assertEquals("Bearer good-key", seen.last()["auth"])
        assertEquals("application/vnd.github.raw+json", seen.last()["accept"])
        assertTrue(LensStore.hasKey(ctx))
        assertEquals("test", LensStore.cached(ctx)?.version)

        // The next check sends the file's tag and downloads nothing when it has not changed.
        assertEquals(LensResult.Unchanged, LensStore.refresh(ctx))
        assertEquals("\"v1\"", seen.last()["etag"])

        // Removing the key removes the view with it.
        LensStore.forget(ctx)
        assertFalse(LensStore.hasKey(ctx))
        assertNull(LensStore.cached(ctx))
        assertEquals(LensResult.NoKey, LensStore.refresh(ctx))
    }

    @Test
    fun anEmptyKeyIsNotSent() {
        val r = LensStore.connect(ctx, "   ")
        assertTrue(r is LensResult.Failed)
        assertTrue(seen.isEmpty())
    }
}

/** A one-route HTTP server on a background thread, standing in for GitHub's API. */
class TinyServer(private val handler: (Map<String, String>) -> Triple<Int, Map<String, String>, String>) {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    init {
        Thread {
            while (!socket.isClosed) {
                val c = try { socket.accept() } catch (e: Exception) { break }
                c.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    reader.readLine() ?: return@use
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val i = line.indexOf(':')
                        if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                    }
                    val (code, extra, body) = handler(headers)
                    val bytes = if (code == 304) ByteArray(0) else body.toByteArray()
                    val reason = mapOf(200 to "OK", 304 to "Not Modified", 401 to "Unauthorized")[code] ?: "Error"
                    val head = StringBuilder("HTTP/1.1 $code $reason\r\n")
                    extra.forEach { (k, v) -> head.append("$k: $v\r\n") }
                    head.append("Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                    val out = s.getOutputStream()
                    out.write(head.toString().toByteArray())
                    out.write(bytes)
                    out.flush()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() = socket.close()
}
