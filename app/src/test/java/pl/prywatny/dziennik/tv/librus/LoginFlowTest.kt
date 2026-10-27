package pl.prywatny.dziennik.tv.librus

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.zip.GZIPOutputStream

/**
 * The Synergia login flow against a local fake server (no real Librus, no real accounts):
 * cookies across redirects, POST → GET after 302, gzip, session reuse and token renewal.
 */
class LoginFlowTest {
    private val server = MockWebServer()
    private val log = mutableListOf<String>()
    private var tokens = 0
    private val expired = mutableSetOf<String>()

    private fun cookies(request: RecordedRequest): Map<String, String> =
        (request.getHeader("Cookie") ?: "").split(';').mapNotNull {
            val parts = it.trim().split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

    private fun json(body: String, status: Int = 200, gzip: Boolean = false): MockResponse {
        val response = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
        if (!gzip) return response.setBody(body)
        val buffer = Buffer()
        GZIPOutputStream(buffer.outputStream()).use { it.write(body.toByteArray()) }
        return response.setHeader("Content-Encoding", "gzip").setBody(buffer)
    }

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val c = cookies(request)
                val body = request.body.readUtf8()
                log += "${request.method} $path"
                return when (path) {
                    "/loguj/portalRodzina" -> MockResponse().setResponseCode(302)
                        .setHeader("Location", "/OAuth/Authorization?client_id=46")
                        .addHeader("Set-Cookie", "DZIENNIKSID=start; Path=/")
                    "/OAuth/Authorization" -> when {
                        request.method == "GET" -> json("{}")
                        !body.contains("login=fikcyjny") || !body.contains("pass=haslo") ->
                            json("""{"status":"error","errors":[{"message":"Nieprawidłowy login i/lub hasło."}]}""")
                        else -> json("""{"status":"ok"}""").addHeader("Set-Cookie", "oauth_session=ok; Path=/")
                    }
                    "/OAuth/Authorization/2FA" ->
                        if (c["oauth_session"] != "ok") MockResponse().setResponseCode(302).setHeader("Location", "/landing?error=access_denied")
                        else MockResponse().setResponseCode(302).setHeader("Location", "/uczen/index").addHeader("Set-Cookie", "DZIENNIKSID=logged; Path=/")
                    "/uczen/index", "/landing" -> json("{}")
                    "/refreshToken" ->
                        if (c["DZIENNIKSID"] != "logged") json("{}", 401)
                        else MockResponse().setResponseCode(200).addHeader("Set-Cookie", "oauth_token=t${++tokens}; Path=/")
                    "/gateway/api/2.0/Me" ->
                        if (c["oauth_token"] == null || c["oauth_token"] in expired) json("""{"Code":"TokenIsExpired"}""", 401)
                        else json("""{"Me":{"Account":{"GroupId":5,"FirstName":"RODZIC","LastName":"FIKCYJNY"},"User":{"FirstName":"UCZEŃ","LastName":"FIKCYJNY"}}}""", gzip = true)
                    else -> json("""{"Code":"NotFound"}""", 404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun endpoints(): Endpoints {
        val base = server.url("/").toString().trimEnd('/')
        return Endpoints(synergia = base, apiOAuth = "$base/OAuth", gateway = "$base/gateway/api/2.0")
    }

    @Test
    fun loginReuseAndRenewal() = runTest {
        val session = SynergiaSession("fikcyjny", "haslo", endpoints())
        val profile = StudentClient(session, endpoints()).profile()
        assertEquals(Profile("fikcyjny", "Uczeń Fikcyjny", isParent = true), profile)
        assertEquals(
            listOf(
                "GET /loguj/portalRodzina", "GET /OAuth/Authorization", "POST /OAuth/Authorization",
                "GET /OAuth/Authorization/2FA", "GET /uczen/index", "GET /refreshToken", "GET /gateway/api/2.0/Me",
            ),
            log,
        )

        // The session is reused without logging in again.
        log.clear()
        session.get("Me")
        assertEquals(listOf("GET /gateway/api/2.0/Me"), log)

        // An expired API token is renewed through /refreshToken, not a full login.
        log.clear()
        expired += "t1"
        session.get("Me")
        assertEquals(listOf("GET /gateway/api/2.0/Me", "GET /refreshToken", "GET /gateway/api/2.0/Me"), log)
    }

    @Test
    fun wrongPasswordIsInvalidLogin() = runTest {
        try {
            StudentClient(SynergiaSession("fikcyjny", "zle", endpoints()), endpoints()).profile()
            fail("expected an exception")
        } catch (e: LibrusException) {
            assertEquals(LibrusException.Code.INVALID_LOGIN, e.code)
        }
    }
}
