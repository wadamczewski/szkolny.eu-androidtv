package pl.prywatny.dziennik.tv.librus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Endpoints and user agents, the same as in the iOS app (LibrusConstants.swift), which took them
 * from szkolny-android `data/api/Constants.kt` (GPL-3.0). Synergia login goes through the website
 * form because api.librus.pl/OAuth/Token no longer accepts the password grant.
 */
object Constants {
    const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 LibrusMobileApp"
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
    const val SYNERGIA_WEB_CLIENT_ID = "46"

    /** A Synergia session is renewed with a full login after this time, like on iOS. */
    const val SESSION_LIFETIME_MS = 30 * 60 * 1000L
}

/** Base URLs; tests point them at a local fake server. */
data class Endpoints(
    val synergia: String = "https://synergia.librus.pl",
    val apiOAuth: String = "https://api.librus.pl/OAuth",
    val gateway: String = "https://synergia.librus.pl/gateway/api/2.0",
)

/** Port of LibrusError.swift. */
class LibrusException(val code: Code, detail: String? = null) : Exception(code.message(detail)) {
    enum class Code(private val format: (String?) -> String) {
        NETWORK({ "Błąd połączenia: $it" }),
        INVALID_RESPONSE({ "Nieprawidłowa odpowiedź serwera: $it" }),
        INVALID_LOGIN({ "Nieprawidłowy login lub hasło." }),
        TOKEN_EXPIRED({ "Sesja wygasła. Odśwież dane." }),
        MAINTENANCE({ "Librus ma przerwę techniczną. Spróbuj później." }),
        ACCESS_DENIED({ "Brak dostępu: $it" }),
        TIMETABLE_NOT_PUBLIC({ "Plan lekcji ucznia nie jest publiczny." }),
        FEATURE_NOT_ACTIVE({ "Funkcja nieaktywna w szkole: $it" }),
        API({ "Błąd API Librus ($it)" }),
        SYNERGIA_SESSION({ "Nie udało się zalogować do Synergii: $it" }),
        PARSE({ "Nie udało się odczytać danych: $it" }),
        NOT_LOGGED_IN({ "Nie jesteś zalogowany." });

        fun message(detail: String?) = format(detail)
    }
}

// Lenient JSON accessors mirroring JSON.swift: Librus payloads mix numbers and numeric strings.

operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private val JsonElement?.primitive: JsonPrimitive?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

val JsonElement?.str: String?
    get() = primitive?.content

val JsonElement?.int: Int?
    get() {
        val p = primitive ?: return null
        if (p.isString) return p.content.trim().toIntOrNull()
        p.booleanOrNull?.let { return if (it) 1 else 0 }
        return p.content.toDoubleOrNull()?.toInt()
    }

val JsonElement?.double: Double?
    get() {
        val p = primitive ?: return null
        if (p.isString) return p.content.replace(',', '.').trim().toDoubleOrNull()
        if (p.booleanOrNull != null) return null
        return p.content.toDoubleOrNull()
    }

val JsonElement?.bool: Boolean?
    get() {
        val p = primitive ?: return null
        if (p.isString) return p.content.lowercase() in setOf("1", "true")
        p.booleanOrNull?.let { return it }
        return p.content.toDoubleOrNull()?.let { it != 0.0 }
    }

val JsonElement?.array: List<JsonElement>
    get() = (this as? JsonArray) ?: emptyList()

val JsonElement?.obj: JsonObject?
    get() = this as? JsonObject

val JsonElement?.isNull: Boolean
    get() = this == null || this is JsonNull

/** `{"Id": 123, "Url": "..."}` reference → 123. */
val JsonElement?.refId: Int?
    get() = this["Id"].int

/** "KOWALSKI JAN" / "jan kowalski" → "Jan Kowalski" (port of `fixName()`). */
fun String.fixedName(): String =
    lowercase()
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.split('-').joinToString("-") { it.replaceFirstChar(Char::uppercaseChar) } }
