package pl.prywatny.dziennik.tv.librus

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Synergia website messages (read-only on TV). Port of SynergiaWeb.swift / SynergiaParser:
 * pages are scraped like `LibrusSynergiaGetMessages` and the librus-apix project.
 */
class SynergiaWeb(private val session: SynergiaSession, private val endpoints: Endpoints = Endpoints()) {

    private suspend fun page(path: String, form: List<Pair<String, String>>? = null): String {
        session.ensureLoggedIn(force = false)
        var html = fetch(path, form)
        if (SynergiaParser.isLoggedOut(html)) {
            session.ensureLoggedIn(force = true)
            html = fetch(path, form)
            if (SynergiaParser.isLoggedOut(html)) throw LibrusException(LibrusException.Code.SYNERGIA_SESSION, "sesja wygasła")
        }
        return html
    }

    private suspend fun fetch(path: String, form: List<Pair<String, String>>?): String {
        val response = session.http.send(
            "${endpoints.synergia}/$path",
            method = if (form == null) "GET" else "POST",
            form = form,
            userAgent = Constants.WEB_USER_AGENT,
        )
        if (response.text.contains("Przerwa techniczna")) throw LibrusException(LibrusException.Code.MAINTENANCE)
        return response.text
    }

    suspend fun messages(folder: MessageFolder, page: Int = 0): List<MessageSummary> =
        SynergiaParser.messageList(
            page("wiadomosci/1/${folder.code}", listOf("numer_strony105" to page.toString(), "porcjowanie_pojemnik105" to "105")),
            folder,
        )

    suspend fun message(id: String, folder: MessageFolder): MessageDetail {
        require(id.all(Char::isDigit)) { "id wiadomości" }
        return SynergiaParser.messageDetail(page("wiadomosci/1/${folder.code}/$id/f0"), id)
    }
}

/** HTML parsing, separated for fixture checks. */
object SynergiaParser {
    fun isLoggedOut(html: String): Boolean {
        val doc = Jsoup.parse(html)
        if (doc.selectFirst("h2.inside")?.text()?.contains("Brak dostępu") == true) return true
        return html.contains("stop.png") || (html.contains("loguj/portalRodzina") && !html.contains("jesteś zalogowany"))
    }

    fun messageList(html: String, folder: MessageFolder): List<MessageSummary> {
        val doc: Document = Jsoup.parse(html)
        val table = doc.selectFirst("table.decorated.stretch") ?: throw LibrusException(LibrusException.Code.PARSE, "lista wiadomości")
        return table.select("tbody > tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 6) return@mapNotNull null
            val link = row.selectFirst("a[href*=/wiadomosci/]")?.attr("href") ?: ""
            val parts = link.split('/').filter { it.isNotEmpty() }
            // /wiadomosci/1/5/12345/f0
            val idx = parts.indexOf("wiadomosci")
            if (idx < 0 || parts.size <= idx + 3) return@mapNotNull null
            val title = cells[3]
            val unread = when (folder) {
                MessageFolder.INBOX -> (title.attr("style") + cells[2].attr("style")).replace(" ", "").contains("font-weight:bold")
                MessageFolder.SENT -> (if (cells.size >= 7) cells[5].text() else "").trim() == "NIE"
            }
            MessageSummary(
                id = parts[idx + 3],
                folder = folder,
                correspondent = clean(cells[2].text()),
                subject = clean(title.text()),
                date = clean(cells[4].text()),
                unread = unread,
                hasAttachments = cells[1].select("img").isNotEmpty(),
            )
        }
    }

    fun messageDetail(html: String, id: String): MessageDetail {
        val doc = Jsoup.parse(html)
        doc.outputSettings().prettyPrint(false) // keep the body's own line breaks only
        val content = doc.selectFirst("div.container-message-content") ?: throw LibrusException(LibrusException.Code.PARSE, "treść wiadomości")
        // Header table: rows "Nadawca", "Temat", "Wysłano" with values in td.left.
        val tables = doc.select("table.stretch")
        val table = tables.firstOrNull { it.className() == "stretch" } ?: tables.firstOrNull()
        val header = table?.select("tr")?.take(3)?.map { clean(it.selectFirst("td.left")?.text() ?: "") } ?: emptyList()
        val attachments = doc.select("img[onclick*=pobierz_zalacznik]").mapNotNull { img ->
            val onclick = img.attr("onclick").replace("\\/", "/")
            val match = Regex("""pobierz_zalacznik/(\d+)/(\d+)""").find(onclick) ?: return@mapNotNull null
            val row = img.parents().firstOrNull { it.tagName() == "tr" }
            MessageAttachment(match.groupValues[2], match.groupValues[1], clean(row?.selectFirst("td")?.text() ?: "załącznik"))
        }
        return MessageDetail(
            id = id,
            sender = header.getOrNull(0) ?: "",
            subject = header.getOrNull(1) ?: "",
            date = header.getOrNull(2) ?: "",
            text = htmlToText(content.html()),
            attachments = attachments,
        )
    }

    /** HTML fragment → readable text with line breaks (the TV shows plain text only). */
    fun htmlToText(html: String): String {
        val withBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6]|blockquote)>"), "\n")
        val doc = Jsoup.parseBodyFragment(withBreaks)
        doc.outputSettings().prettyPrint(false)
        return doc.body().wholeText()
            .replace(' ', ' ')
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun clean(s: String) = s.replace(' ', ' ').trim()
}
