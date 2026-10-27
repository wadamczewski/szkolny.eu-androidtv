package pl.prywatny.dziennik.tv.librus

/**
 * Demo mode (login "demo"): SYNTHETIC, GENERATED DATA ONLY. Every name, grade and message here is
 * fake. It lets you try the app without a Librus account and is used for UI checks.
 */
class DemoSource : StudentSource {
    companion object {
        const val LOGIN = "demo"

        private val SUBJECTS = listOf(
            Subject(1, "Zachowanie", "zach"), Subject(10, "Matematyka", "mat"), Subject(11, "Język polski", "pol"),
            Subject(12, "Język angielski", "ang"), Subject(13, "Historia", "his"), Subject(14, "Biologia", "bio"),
            Subject(15, "Geografia", "geo"), Subject(16, "Fizyka", "fiz"), Subject(17, "Informatyka", "inf"),
            Subject(18, "Wychowanie fizyczne", "wf"),
        )
        private val TEACHERS = listOf("Jan Testowy", "Anna Przykładowa", "Ewa Fikcyjna", "Piotr Demo", "Maria Wzorcowa", "Tomasz Próbny")
        private val ROOMS = listOf("12", "14", "21 pracownia", "3", "sala gim.", "7")
        private val HOURS = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35", "10:55" to "11:40",
            "11:50" to "12:35", "12:45" to "13:30", "13:40" to "14:25",
        )
        private data class Category(val name: String, val weight: Double, val color: String)
        private val CATEGORIES = listOf(
            Category("Sprawdzian", 3.0, "#FF0000"), Category("Kartkówka", 2.0, "#87CEFA"),
            Category("Odpowiedź ustna", 2.0, "#32CD32"), Category("Praca domowa", 1.0, "#DAA520"),
            Category("Aktywność", 0.0, "#BA55D3"),
        )
        private val GRADE_NAMES = listOf("5", "4", "4+", "3", "5-", "6", "3+", "4-", "2", "5", "+")
    }

    /** Small deterministic generator so the demo looks the same every time. */
    private class Rng(seed: Long) {
        private var s = seed and 0xFFFFFFFFL
        fun next(): Double {
            s = (s * 1664525 + 1013904223) and 0xFFFFFFFFL
            return s / 4294967296.0
        }
        fun <T> pick(list: List<T>) = list[(next() * list.size).toInt()]
    }

    private fun teacherFor(subjectId: Int) = TEACHERS[subjectId % TEACHERS.size]
    private fun randomSubject(r: Rng) = SUBJECTS[1 + (r.next() * (SUBJECTS.size - 1)).toInt()]

    private fun schoolYearStart(today: String): String {
        val year = today.take(4).toInt() - if (today.substring(5, 7).toInt() < 9) 1 else 0
        return "$year-09-01"
    }

    private fun semesterOf(day: String) = if (day.substring(5, 7).toInt() in 2..8) 2 else 1

    override suspend fun profile() = Profile(LOGIN, "Uczeń Demonstracyjny", isParent = true)

    override suspend fun timetable(weekStart: String): List<TimetableDay> = (0..4).map { d ->
        val day = Day.addDays(Day.weekStart(weekStart), d.toLong())
        val r = Rng(day.replace("-", "").toLong())
        val count = 5 + (r.next() * 3).toInt()
        TimetableDay(day, (1..count).map { n ->
            val subject = randomSubject(r)
            val roll = r.next()
            val base = Lesson(day, n, HOURS[n - 1].first, HOURS[n - 1].second, LessonKind.NORMAL, subject.name, teacherFor(subject.id), r.pick(ROOMS))
            when {
                roll < 0.05 -> base.copy(kind = LessonKind.CANCELLED)
                roll < 0.1 -> base.copy(kind = LessonKind.CHANGE, oldSubject = SUBJECTS[2].name, oldTeacher = teacherFor(SUBJECTS[2].id))
                else -> base
            }
        })
    }

    override suspend fun sync(weekStart: String): StudentData {
        val today = Day.today()
        return StudentData(
            fetchedAt = System.currentTimeMillis(),
            studentName = "Uczeń Demonstracyjny",
            accountName = "Rodzic Demonstracyjny",
            subjects = SUBJECTS.sortedBy { it.name },
            grades = grades(today),
            events = events(today),
            attendance = attendance(today),
            notices = listOf(
                Notice(1, Day.addDays(today, -3), NoticeKind.POSITIVE, "Pochwała", "Pomoc w organizacji apelu (dane fikcyjne).", TEACHERS[1]),
                Notice(2, Day.addDays(today, -20), NoticeKind.NEGATIVE, "Uwaga", "Brak stroju na zajęciach (dane fikcyjne).", TEACHERS[4]),
            ),
            announcements = listOf(
                Announcement("a1", "Dzień otwarty szkoły", "Zapraszamy na dzień otwarty w sobotę o 10:00. To jest fikcyjne ogłoszenie demonstracyjne.", Day.addDays(today, -2), Day.addDays(today, 5), TEACHERS[0], read = false),
                Announcement("a2", "Zebranie z rodzicami", "Zebranie odbędzie się w sali 12. Treść demonstracyjna.", Day.addDays(today, -10), Day.addDays(today, 1), TEACHERS[2], read = true),
            ),
            luckyNumber = LuckyNumber(today, 13),
            timetableWeek = weekStart,
            timetable = timetable(weekStart),
            warnings = emptyList(),
        )
    }

    private fun grades(today: String): List<Grade> {
        val r = Rng(42)
        val start = schoolYearStart(today)
        val span = maxOf(1L, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(start), java.time.LocalDate.parse(today)))
        var id = 5000
        val list = mutableListOf<Grade>()
        for (subject in SUBJECTS.drop(1)) {
            val count = 3 + (r.next() * 6).toInt()
            for (i in 0 until count) {
                val name = r.pick(GRADE_NAMES)
                val category = r.pick(CATEGORIES)
                val day = Day.addDays(start, (((i + r.next()) / count) * span).toLong())
                list += Grade(
                    id = id++, name = name, kind = GradeKind.NORMAL, value = GradeMath.value(name),
                    weight = if (name == "+") 0.0 else category.weight, category = category.name, color = category.color,
                    comment = if (r.next() < 0.25) "Komentarz testowy (dane fikcyjne)" else null,
                    semester = semesterOf(day), teacher = teacherFor(subject.id), subjectId = subject.id,
                    addedDate = "$day 1${(r.next() * 4).toInt()}:00:00", isImprovement = false,
                )
            }
        }
        list += Grade(id, "bdb", GradeKind.SEMESTER_PROPOSED, null, 0.0, "Zachowanie", "#2196F3", null, 1, TEACHERS[0], 1, "$today 08:00:00", false)
        return list
    }

    private fun events(today: String): List<AgendaEvent> {
        val r = Rng(7)
        val kinds = listOf("Sprawdzian" to "#FF0000", "Kartkówka" to "#87CEFA", "Zadanie domowe" to "#DAA520", "Wycieczka" to "#32CD32")
        return (0 until 10).mapNotNull { i ->
            val kind = r.pick(kinds)
            val subject = randomSubject(r)
            val day = Day.addDays(today, (-6 + (r.next() * 24).toInt()).toLong())
            if (Day.weekday(day) >= 5) return@mapNotNull null
            AgendaEvent(
                7000 + i, day, null, 1 + (r.next() * 6).toInt(),
                "${kind.first}: rozdział ${1 + (r.next() * 9).toInt()} (dane fikcyjne)",
                kind.first, kind.second, subject.name, teacherFor(subject.id), "${Day.addDays(day, -7)} 12:00:00",
            )
        }.sortedBy { it.day }
    }

    private fun attendance(today: String): List<AttendanceEntry> {
        val r = Rng(99)
        data class Type(val name: String, val short: String, val base: AttendanceBase, val color: String?)
        val types = listOf(
            Type("obecność", "ob", AttendanceBase.PRESENT, null), Type("nieobecność", "nb", AttendanceBase.ABSENT, "#FF0000"),
            Type("nieobecność uspr.", "u", AttendanceBase.ABSENT_EXCUSED, "#FFA07A"), Type("spóźnienie", "sp", AttendanceBase.BELATED, "#DAA520"),
            Type("zwolnienie", "zw", AttendanceBase.RELEASED, "#87CEFA"),
        )
        val list = mutableListOf<AttendanceEntry>()
        var day = schoolYearStart(today)
        while (day < today) {
            if (Day.weekday(day) < 5) {
                for (n in 1..6) {
                    val roll = r.next()
                    val type = when {
                        roll < 0.9 -> types[0]
                        roll < 0.93 -> types[1]
                        roll < 0.97 -> types[2]
                        roll < 0.99 -> types[3]
                        else -> types[4]
                    }
                    val subject = randomSubject(r)
                    list += AttendanceEntry("$day-$n", day, n, type.name, type.short, type.base, type.color, subject.name, teacherFor(subject.id), semesterOf(day))
                }
            }
            day = Day.addDays(day, 1)
        }
        return list.reversed()
    }

    private val demoMessages = listOf(
        Triple("900001", "Testowy Jan (Nauczyciel)", "Wycieczka klasowa") to
            "Dzień dobry,\n\nw przyszły piątek planujemy wycieczkę do muzeum. Prosimy o zgody do środy.\n\nTo jest fikcyjna wiadomość demonstracyjna.",
        Triple("900002", "Przykładowa Anna (Wychowawca)", "Zebranie z rodzicami") to
            "Szanowni Państwo,\n\nzapraszam na zebranie w czwartek o 17:30.\n\nWiadomość demonstracyjna, dane fikcyjne.",
        Triple("900003", "Sekretariat (Administracja)", "Legitymacje szkolne") to
            "Legitymacje są do odbioru w sekretariacie.\n\n(dane fikcyjne)",
    )

    override suspend fun messages(folder: MessageFolder): List<MessageSummary> {
        if (folder == MessageFolder.SENT) return emptyList()
        val today = Day.today()
        return demoMessages.mapIndexed { i, (meta, _) ->
            MessageSummary(meta.first, folder, meta.second, meta.third, "${Day.addDays(today, -3L * i)} 1$i:15:00", unread = i == 0, hasAttachments = i == 0)
        }
    }

    override suspend fun message(summary: MessageSummary): MessageDetail {
        val (meta, text) = demoMessages.firstOrNull { it.first.first == summary.id } ?: demoMessages.first()
        val attachments = if (meta.first == "900001") listOf(MessageAttachment("1", meta.first, "zgoda-na-wycieczke.pdf")) else emptyList()
        return MessageDetail(meta.first, meta.second, meta.third, "${Day.today()} 10:15:00", text, attachments)
    }
}
