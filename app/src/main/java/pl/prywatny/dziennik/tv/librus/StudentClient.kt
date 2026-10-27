package pl.prywatny.dziennik.tv.librus

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement

/**
 * Everything the app does for one Synergia login: API sync, timetable weeks and messages.
 * Port of StudentClient.swift, limited to what the TV shows.
 */
interface StudentSource {
    suspend fun profile(): Profile
    suspend fun sync(weekStart: String = Day.defaultTimetableWeek()): StudentData
    suspend fun timetable(weekStart: String): List<TimetableDay>
    suspend fun messages(folder: MessageFolder): List<MessageSummary>
    suspend fun message(summary: MessageSummary): MessageDetail
}

class StudentClient(private val session: SynergiaSession, private val endpoints: Endpoints = Endpoints()) : StudentSource {
    private val web = SynergiaWeb(session, endpoints)

    @Volatile
    private var dict: Parsers.Dictionaries? = null

    private data class Names(val studentName: String?, val accountName: String?, val isParent: Boolean)

    private fun names(meJson: JsonElement): Names {
        val me = meJson["Me"]
        val account = me["Account"]
        val user = me["User"]
        fun full(o: JsonElement?) = listOfNotNull(o["FirstName"].str, o["LastName"].str).joinToString(" ").fixedName().ifEmpty { null }
        val groupId = account["GroupId"].int ?: 0
        return Names(full(user), if (groupId == 5) full(account) else null, groupId in 5..6)
    }

    override suspend fun profile(): Profile {
        session.ensureLoggedIn(force = true)
        val names = names(session.get("Me"))
        return Profile(session.login, names.studentName ?: session.login, names.isParent)
    }

    override suspend fun sync(weekStart: String): StudentData = coroutineScope {
        val warnings = mutableListOf<String>()
        // Fail fast on auth problems before firing ~17 requests.
        val names = names(session.get("Me"))

        val limit = Semaphore(6)
        fun fetch(endpoint: String) = async { limit.withPermit { runCatching { session.get(endpoint) } } }
        val subjects = fetch("Subjects")
        val teachers = fetch("Users")
        val classrooms = fetch("Classrooms")
        val gradeCategories = fetch("Grades/Categories")
        val gradeComments = fetch("Grades/Comments")
        val eventCategories = fetch("HomeWorks/Categories")
        val attendanceTypes = fetch("Attendances/Types")
        val noticeCategories = fetch("Notes/Categories")
        val lessons = fetch("Lessons")
        val grades = fetch("Grades")
        val events = fetch("HomeWorks")
        val attendance = fetch("Attendances")
        val notes = fetch("Notes")
        val notices = fetch("SchoolNotices")
        val lucky = fetch("LuckyNumbers")
        val timetable = fetch("Timetables?weekStart=$weekStart")

        fun collect(label: String, result: Result<JsonElement>): JsonElement? = result.getOrElse { e ->
            // Disabled features are expected in some schools; don't nag about them.
            if ((e as? LibrusException)?.code != LibrusException.Code.FEATURE_NOT_ACTIVE) warnings += "$label: ${e.message}"
            null
        }

        val d = Parsers.Dictionaries(
            subjects = Parsers.subjects(collect("Przedmioty", subjects.await())),
            teachers = collect("Nauczyciele", teachers.await())?.let(Parsers::teachers) ?: emptyMap(),
            classrooms = collect("Sale", classrooms.await())?.let(Parsers::classrooms) ?: emptyMap(),
            gradeCategories = collect("Kategorie ocen", gradeCategories.await())?.let(Parsers::gradeCategories) ?: emptyMap(),
            gradeComments = collect("Komentarze ocen", gradeComments.await())?.let(Parsers::gradeComments) ?: emptyMap(),
            eventCategories = collect("Kategorie wydarzeń", eventCategories.await())?.let(Parsers::eventCategories) ?: emptyMap(),
            attendanceTypes = collect("Typy frekwencji", attendanceTypes.await())?.let(Parsers::attendanceTypes) ?: emptyMap(),
            noticeCategories = collect("Kategorie uwag", noticeCategories.await())?.let(Parsers::noticeCategories) ?: emptyMap(),
            lessons = collect("Lekcje", lessons.await())?.let(Parsers::lessons) ?: emptyMap(),
        )
        dict = d

        StudentData(
            fetchedAt = System.currentTimeMillis(),
            studentName = names.studentName,
            accountName = names.accountName,
            subjects = d.subjects.values.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("pl"))) { it.name }),
            grades = collect("Oceny", grades.await())?.let { Parsers.grades(it, d) } ?: emptyList(),
            events = collect("Terminarz", events.await())?.let { Parsers.events(it, d) } ?: emptyList(),
            attendance = collect("Frekwencja", attendance.await())?.let { Parsers.attendance(it, d) } ?: emptyList(),
            notices = collect("Uwagi", notes.await())?.let { Parsers.notices(it, d) } ?: emptyList(),
            announcements = collect("Ogłoszenia", notices.await())?.let { Parsers.announcements(it, d) } ?: emptyList(),
            luckyNumber = collect("Szczęśliwy numerek", lucky.await())?.let(Parsers::luckyNumber),
            timetableWeek = weekStart,
            timetable = collect("Plan lekcji", timetable.await())?.let { Parsers.timetable(it, d) } ?: emptyList(),
            warnings = warnings,
        )
    }

    /** One timetable week, using dictionaries from the last sync (fetched again if needed). */
    override suspend fun timetable(weekStart: String): List<TimetableDay> {
        val d = dict ?: Parsers.Dictionaries(
            subjects = Parsers.subjects(session.get("Subjects")),
            teachers = Parsers.teachers(session.get("Users")),
            classrooms = Parsers.classrooms(session.get("Classrooms")),
        ).also { dict = it }
        return Parsers.timetable(session.get("Timetables?weekStart=${Day.weekStart(weekStart)}"), d)
    }

    override suspend fun messages(folder: MessageFolder) = web.messages(folder)

    override suspend fun message(summary: MessageSummary) = web.message(summary.id, summary.folder)
}
