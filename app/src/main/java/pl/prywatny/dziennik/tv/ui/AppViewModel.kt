package pl.prywatny.dziennik.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.prywatny.dziennik.tv.data.Account
import pl.prywatny.dziennik.tv.data.MessagesCache
import pl.prywatny.dziennik.tv.data.Store
import pl.prywatny.dziennik.tv.librus.Day
import pl.prywatny.dziennik.tv.librus.DemoSource
import pl.prywatny.dziennik.tv.librus.LibrusException
import pl.prywatny.dziennik.tv.librus.MessageDetail
import pl.prywatny.dziennik.tv.librus.MessageFolder
import pl.prywatny.dziennik.tv.librus.MessageSummary
import pl.prywatny.dziennik.tv.librus.StudentClient
import pl.prywatny.dziennik.tv.librus.StudentData
import pl.prywatny.dziennik.tv.librus.StudentSource
import pl.prywatny.dziennik.tv.librus.SynergiaSession
import pl.prywatny.dziennik.tv.librus.TimetableDay

enum class Section(val label: String) {
    HOME("Start"),
    TIMETABLE("Plan lekcji"),
    GRADES("Oceny"),
    AGENDA("Terminarz"),
    ATTENDANCE("Frekwencja"),
    MESSAGES("Wiadomości"),
    NOTICES("Uwagi"),
    ANNOUNCEMENTS("Ogłoszenia"),
    ACCOUNT("Konto"),
}

/** Loading state of something fetched on demand (a timetable week, a message folder). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    private val sources = mutableMapOf<String, StudentSource>()
    private var syncJob: Job? = null

    var accounts by mutableStateOf(store.accounts)
        private set
    var account by mutableStateOf(store.current)
        private set
    var data by mutableStateOf<StudentData?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var section by mutableStateOf(Section.HOME)
    var weekStart by mutableStateOf(Day.defaultTimetableWeek())
    val weeks = mutableStateMapOf<String, Load<List<TimetableDay>>>()
    val folders = mutableStateMapOf<MessageFolder, Load<List<MessageSummary>>>()
    var toast by mutableStateOf<Toast?>(null)
        private set
    var addingAccount by mutableStateOf(false)

    data class Toast(val text: String, val error: Boolean = false, val id: Long = System.nanoTime())

    init {
        loadCurrent()
        // Keep the data fresh while the app is open.
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                refresh(manual = false)
            }
        }
    }

    private fun source(account: Account): StudentSource = sources.getOrPut(account.login) {
        if (account.login == DemoSource.LOGIN) DemoSource()
        else StudentClient(SynergiaSession(account.login, account.password))
    }

    private fun loadCurrent() {
        syncJob?.cancel()
        syncing = false
        accounts = store.accounts
        account = store.current
        error = null
        section = Section.HOME
        weekStart = Day.defaultTimetableWeek()
        weeks.clear()
        folders.clear()
        val current = account ?: run { data = null; return }
        data = store.data(current.login)?.also { weeks[it.timetableWeek] = Load.Ready(it.timetable) }
        val cached = store.messages(current.login)
        cached.inbox?.let { folders[MessageFolder.INBOX] = Load.Ready(it) }
        cached.sent?.let { folders[MessageFolder.SENT] = Load.Ready(it) }
        if (isStale()) refresh(manual = false)
    }

    fun isStale(): Boolean = data.let { it == null || System.currentTimeMillis() - it.fetchedAt > STALE_MS }

    fun refresh(manual: Boolean) {
        val current = account ?: return
        if (syncing) return
        syncing = true
        error = null
        syncJob = viewModelScope.launch {
            try {
                val fresh = source(current).sync(Day.defaultTimetableWeek())
                if (account?.login != current.login) return@launch
                data = fresh
                weeks.clear()
                weeks[fresh.timetableWeek] = Load.Ready(fresh.timetable)
                store.saveData(current.login, fresh)
                if (fresh.warnings.isNotEmpty()) show("Część danych nie wczytała się (${fresh.warnings.size}). Szczegóły w zakładce Konto.", error = true)
                else if (manual) show("Dane zaktualizowane.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message
                val invalid = (e as? LibrusException)?.code == LibrusException.Code.INVALID_LOGIN
                show(if (invalid) "Librus nie przyjął hasła. Usuń ucznia i zaloguj się ponownie w zakładce Konto." else e.message ?: "Błąd", error = true)
            } finally {
                if (account?.login == current.login) syncing = false
            }
        }
    }

    fun loadWeek(start: String) {
        val current = account ?: return
        if (weeks[start] is Load.Ready || weeks[start] == Load.Loading) return
        weeks[start] = Load.Loading
        viewModelScope.launch {
            weeks[start] = try {
                Load.Ready(source(current).timetable(start))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Load.Failed(e.message ?: "Błąd")
            }
        }
    }

    fun loadMessages(folder: MessageFolder, force: Boolean = false) {
        val current = account ?: return
        if (!force && (folders[folder] is Load.Ready || folders[folder] == Load.Loading)) return
        folders[folder] = Load.Loading
        viewModelScope.launch {
            try {
                folders[folder] = Load.Ready(source(current).messages(folder))
                saveMessages(current.login)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                folders[folder] = Load.Failed(e.message ?: "Błąd")
            }
        }
    }

    suspend fun message(summary: MessageSummary): MessageDetail {
        val current = account ?: throw LibrusException(LibrusException.Code.NOT_LOGGED_IN)
        val detail = source(current).message(summary)
        if (summary.unread) {
            val folder = folders[summary.folder]
            if (folder is Load.Ready) {
                folders[summary.folder] = Load.Ready(folder.value.map { if (it.id == summary.id) it.copy(unread = false) else it })
                saveMessages(current.login)
            }
        }
        return detail
    }

    private fun saveMessages(login: String) {
        store.saveMessages(
            login,
            MessagesCache(
                inbox = (folders[MessageFolder.INBOX] as? Load.Ready)?.value,
                sent = (folders[MessageFolder.SENT] as? Load.Ready)?.value,
            ),
        )
    }

    /** First login of an account. Returns an error message or null on success. */
    suspend fun login(login: String, password: String): String? {
        val trimmed = login.trim()
        if (trimmed.isEmpty() || password.isEmpty()) return "Wpisz login i hasło."
        val candidate = Account(trimmed, password, trimmed, false)
        sources.remove(trimmed)
        return try {
            val profile = source(candidate).profile()
            store.save(Account(profile.login, password, profile.studentName, profile.isParent))
            addingAccount = false
            loadCurrent()
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            sources.remove(trimmed)
            e.message ?: "Błąd logowania"
        }
    }

    fun switchAccount(login: String) {
        store.select(login)
        loadCurrent()
        account?.let { show("Uczeń: ${it.studentName}") }
    }

    fun nextStudent() {
        val list = accounts
        if (list.size < 2) return
        val i = list.indexOfFirst { it.login == account?.login }
        switchAccount(list[(i + 1) % list.size].login)
    }

    fun removeCurrent() {
        val current = account ?: return
        sources.remove(current.login)
        store.remove(current.login)
        loadCurrent()
    }

    fun show(text: String, error: Boolean = false) {
        toast = Toast(text, error)
    }

    fun dismissToast(t: Toast) {
        if (toast?.id == t.id) toast = null
    }

    companion object {
        const val STALE_MS = 10 * 60 * 1000L
        const val AUTO_REFRESH_MS = 30 * 60 * 1000L
    }
}
