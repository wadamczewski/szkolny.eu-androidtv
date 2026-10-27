package pl.prywatny.dziennik.tv.librus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** In-memory cookies shared across *.librus.pl (api. sets them, synergia. reads them). */
class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            // An expiry in the past means "delete".
            if (c.expiresAt > now) this.cookies.add(c)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}

/**
 * OkHttp counterpart of HTTP.swift. OkHttp follows redirects (POST → GET after 302/303, like
 * URLSession) and runs every hop through the cookie jar.
 */
class HttpClient {
    private val jar = MemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    class Response(val status: Int, val url: String, val text: String) {
        fun json(): JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw LibrusException(LibrusException.Code.INVALID_RESPONSE, "HTTP $status, ${text.take(200)}")
        }
    }

    fun clearCookies() = jar.clear()

    suspend fun send(
        url: String,
        method: String = "GET",
        form: List<Pair<String, String>>? = null,
        userAgent: String = Constants.USER_AGENT,
    ): Response = withContext(Dispatchers.IO) {
        val body = form?.let { formEncode(it).toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType()) }
        val request = Request.Builder()
            .url(url)
            .method(method, body)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "pl-PL,pl;q=0.9")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                // OkHttp decodes iso-8859-2 / windows-1250 from the Content-Type charset.
                Response(response.code, response.request.url.toString(), response.body?.string() ?: "")
            }
        } catch (e: IOException) {
            throw LibrusException(LibrusException.Code.NETWORK, e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /**
         * application/x-www-form-urlencoded with UTF-8 percent-encoding, identical to the Swift and
         * JS clients: only [A-Za-z0-9-._*] stay as they are, space becomes "+".
         */
        fun formEncode(pairs: List<Pair<String, String>>): String =
            pairs.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

        private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("~", "%7E")
    }
}
