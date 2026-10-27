package pl.prywatny.dziennik.tv.librus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.Json

/**
 * Synergia login + password session and the API 2.0 gateway. Port of CredentialSynergiaSession
 * and LibrusAPI.gateway from the iOS app: api.librus.pl/OAuth/Token no longer accepts the
 * password grant (`unsupported_grant_type`), so we log in through the Synergia website form
 * (client_id 46, as in librus-apix) and call synergia.librus.pl/gateway/api/2.0 with cookies.
 */
class SynergiaSession(
    val login: String,
    private val password: String,
    private val endpoints: Endpoints = Endpoints(),
) {
    val http = HttpClient()
    private val mutex = Mutex()

    @Volatile
    private var sessionAt = 0L

    /** [observed] is the session the caller saw failing, so parallel callers re-login once. */
    suspend fun ensureLoggedIn(force: Boolean, observed: Long = sessionAt) = mutex.withLock {
        val fresh = System.currentTimeMillis() - sessionAt < Constants.SESSION_LIFETIME_MS - 60_000
        if (!force && fresh) return@withLock
        if (force && sessionAt != observed && fresh) return@withLock
        performLogin()
    }

    /** Short-lived `oauth_token` cookie needed by the gateway API. */
    suspend fun refreshGatewayToken(): Boolean =
        http.send("${endpoints.synergia}/refreshToken", userAgent = Constants.WEB_USER_AGENT).status == 200

    private suspend fun performLogin() {
        http.clearCookies()
        sessionAt = 0
        val ua = Constants.WEB_USER_AGENT
        val start = http.send("${endpoints.synergia}/loguj/portalRodzina?v=${System.currentTimeMillis() / 1000}", userAgent = ua)
        if (start.status == 503 || start.text.contains("Przerwa techniczna")) throw LibrusException(LibrusException.Code.MAINTENANCE)

        val response = http.send(
            "${endpoints.apiOAuth}/Authorization?client_id=${Constants.SYNERGIA_WEB_CLIENT_ID}",
            method = "POST",
            form = listOf("action" to "login", "login" to login, "pass" to password),
            userAgent = ua,
        )
        if (response.status == 503) throw LibrusException(LibrusException.Code.MAINTENANCE)
        val json = response.json()
        if (json["status"].str == "error") {
            val message = json["errors"].array.firstOrNull()["message"].str ?: "nieznany błąd"
            if (message.lowercase().contains("nieprawidłowy login")) throw LibrusException(LibrusException.Code.INVALID_LOGIN)
            throw LibrusException(LibrusException.Code.SYNERGIA_SESSION, message)
        }

        val finish = http.send("${endpoints.apiOAuth}/Authorization/2FA?client_id=${Constants.SYNERGIA_WEB_CLIENT_ID}", userAgent = ua)
        if (finish.url.contains("error=")) {
            val reason = if (finish.url.contains("access_denied")) "brak dostępu" else "błąd"
            throw LibrusException(LibrusException.Code.SYNERGIA_SESSION, "Librus odrzucił logowanie ($reason)")
        }
        if (!refreshGatewayToken()) {
            throw LibrusException(LibrusException.Code.SYNERGIA_SESSION, "nie udało się uzyskać tokenu API")
        }
        sessionAt = System.currentTimeMillis()
    }

    // MARK: API 2.0 through the gateway (port of LibrusAPI.get incl. error mapping)

    suspend fun get(endpoint: String): JsonElement = try {
        request(endpoint, forceRefresh = false)
    } catch (e: LibrusException) {
        if (e.code != LibrusException.Code.TOKEN_EXPIRED) throw e
        request(endpoint, forceRefresh = true)
    }

    private suspend fun request(endpoint: String, forceRefresh: Boolean): JsonElement {
        val seen = sessionAt
        if (forceRefresh) {
            if (!refreshGatewayToken()) ensureLoggedIn(force = true, observed = seen)
        } else {
            ensureLoggedIn(force = false)
        }
        val response = http.send("${endpoints.gateway}/$endpoint")
        if (response.status == 503) throw LibrusException(LibrusException.Code.MAINTENANCE)
        val json = runCatching { Json.parseToJsonElement(response.text) }.getOrNull()
        if (response.status == 200) {
            if (json != null && json !is JsonNull) return json
            // A dead session may be redirected to an HTML login page instead of getting 401.
            if (!forceRefresh) throw LibrusException(LibrusException.Code.TOKEN_EXPIRED)
            throw LibrusException(LibrusException.Code.INVALID_RESPONSE, "$endpoint: odpowiedź nie jest w formacie JSON")
        }
        // Expired token/session: retry once with fresh authentication.
        if (response.status == 401 && !forceRefresh) throw LibrusException(LibrusException.Code.TOKEN_EXPIRED)
        throwError(json, response.status)
    }

    companion object {
        fun throwError(json: JsonElement?, status: Int): Nothing {
            val code = json["Code"].str ?: json["Message"].str ?: json["Status"].str ?: "HTTP $status"
            val message = json["Message"].str
            throw when (code) {
                "TokenIsExpired" -> LibrusException(LibrusException.Code.TOKEN_EXPIRED)
                "Maintenance" -> LibrusException(LibrusException.Code.MAINTENANCE)
                "AccessDeny" ->
                    if (message == "Student timetable is not public") LibrusException(LibrusException.Code.TIMETABLE_NOT_PUBLIC)
                    else LibrusException(LibrusException.Code.ACCESS_DENIED, message ?: code)
                "Request is denied", "Insufficient scopes" -> LibrusException(LibrusException.Code.ACCESS_DENIED, code)
                "LuckyNumberIsNotActive" -> LibrusException(LibrusException.Code.FEATURE_NOT_ACTIVE, "szczęśliwy numerek")
                "NotesIsNotActive" -> LibrusException(LibrusException.Code.FEATURE_NOT_ACTIVE, "uwagi")
                else ->
                    if (status == 401) LibrusException(LibrusException.Code.TOKEN_EXPIRED)
                    else LibrusException(LibrusException.Code.API, if (message != null) "$code: $message" else code)
            }
        }
    }
}
