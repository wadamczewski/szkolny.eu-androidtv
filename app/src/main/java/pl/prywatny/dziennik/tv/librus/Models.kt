package pl.prywatny.dziennik.tv.librus

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Plain, cacheable models (port of Models.swift). School days are "yyyy-MM-dd" strings, so they
 * never shift with time zones.
 */
object Day {
    fun parse(s: String?): String? {
        val d = s?.take(10) ?: return null
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(d)) return null
        return runCatching { LocalDate.parse(d).toString() }.getOrNull()
    }

    fun today(): String = LocalDate.now().toString()

    fun addDays(day: String, n: Long): String = LocalDate.parse(day).plusDays(n).toString()

    /** Monday of this day's week. */
    fun weekStart(day: String): String =
        LocalDate.parse(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /** 0 = Monday … 6 = Sunday. */
    fun weekday(day: String): Int = LocalDate.parse(day).dayOfWeek.value - 1

    /** The current week, or the coming one on weekends (like Android and iOS). */
    fun defaultTimetableWeek(today: String = today()): String =
        if (weekday(today) >= 5) addDays(weekStart(today), 7) else weekStart(today)

    /** "2025-10-01 10:00:00" → the same, trimmed to 19 chars; a date alone → midnight. */
    fun dateTime(s: String?): String? {
        if (s == null) return null
        if (Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.*""").matches(s)) return s.take(19)
        return parse(s)?.let { "$it 00:00:00" }
    }
}

@Serializable
data class Subject(val id: Int, val name: String, val short: String)

@Serializable
data class Teacher(val id: Int, val firstName: String, val lastName: String) {
    val fullName: String get() = "$firstName $lastName".trim()
}

@Serializable
enum class GradeKind(val label: String) {
    NORMAL("Ocena"),
    SEMESTER_PROPOSED("Przewidywana śródroczna"),
    SEMESTER_FINAL("Śródroczna"),
    YEAR_PROPOSED("Przewidywana roczna"),
    YEAR_FINAL("Roczna");

    val isProposed: Boolean get() = this == SEMESTER_PROPOSED || this == YEAR_PROPOSED
}

@Serializable
data class Grade(
    val id: Int,
    val name: String,
    val kind: GradeKind,
    val value: Double?,
    val weight: Double,
    val category: String,
    val color: String?,
    val comment: String?,
    val semester: Int,
    val teacher: String?,
    val subjectId: Int,
    val addedDate: String?,
    val isImprovement: Boolean,
    /** Set on a grade that was later improved — the improvement grade's id. */
    val improvedById: Int? = null,
) {
    val countsToAverage: Boolean get() = kind == GradeKind.NORMAL && weight > 0 && value != null
}

@Serializable
enum class LessonKind(val label: String?) {
    NORMAL(null),
    CANCELLED("odwołana"),
    CHANGE("zastępstwo"),
    SHIFTED_SOURCE("przeniesiona"),
    SHIFTED_TARGET("przeniesiona tutaj"),
}

@Serializable
data class Lesson(
    val day: String,
    val number: Int,
    val start: String,
    val end: String,
    val kind: LessonKind,
    val subject: String?,
    val teacher: String?,
    val classroom: String?,
    val oldSubject: String? = null,
    val oldTeacher: String? = null,
    /** For shifted lessons: where it moves to / comes from. */
    val otherDay: String? = null,
    val otherNumber: Int? = null,
)

@Serializable
data class TimetableDay(val day: String, val lessons: List<Lesson>)

@Serializable
data class AgendaEvent(
    val id: Int,
    val day: String,
    val time: String?,
    val lessonNumber: Int?,
    val topic: String,
    val category: String,
    val color: String?,
    val subject: String?,
    val teacher: String?,
    val addedDate: String?,
)

@Serializable
enum class AttendanceBase {
    PRESENT, PRESENT_CUSTOM, ABSENT, ABSENT_EXCUSED, BELATED, RELEASED, UNKNOWN;

    val isAbsence: Boolean get() = this == ABSENT || this == ABSENT_EXCUSED
}

@Serializable
data class AttendanceEntry(
    val id: String,
    val day: String,
    val lessonNumber: Int,
    val typeName: String,
    val typeShort: String,
    val base: AttendanceBase,
    val color: String?,
    val subject: String?,
    val teacher: String?,
    val semester: Int,
)

@Serializable
enum class NoticeKind(val label: String) { POSITIVE("Pozytywna"), NEGATIVE("Negatywna"), NEUTRAL("Neutralna") }

@Serializable
data class Notice(val id: Int, val day: String, val kind: NoticeKind, val category: String, val text: String, val teacher: String?)

@Serializable
data class Announcement(
    val id: String,
    val subject: String,
    val text: String,
    val startDay: String?,
    val endDay: String?,
    val author: String?,
    val read: Boolean,
)

@Serializable
data class LuckyNumber(val day: String, val number: Int)

/** Everything fetched for one student, cached to disk for offline use. */
@Serializable
data class StudentData(
    val fetchedAt: Long,
    val studentName: String?,
    val accountName: String?,
    val subjects: List<Subject>,
    val grades: List<Grade>,
    val events: List<AgendaEvent>,
    val attendance: List<AttendanceEntry>,
    val notices: List<Notice>,
    val announcements: List<Announcement>,
    val luckyNumber: LuckyNumber?,
    val timetableWeek: String,
    val timetable: List<TimetableDay>,
    /** Sections that failed to load (shown, but don't block the rest). */
    val warnings: List<String>,
)

@Serializable
enum class MessageFolder(val code: String) { INBOX("5"), SENT("6") }

@Serializable
data class MessageSummary(
    val id: String,
    val folder: MessageFolder,
    /** Sender (inbox) or recipient (sent). */
    val correspondent: String,
    val subject: String,
    val date: String,
    val unread: Boolean,
    val hasAttachments: Boolean,
)

@Serializable
data class MessageAttachment(val id: String, val messageId: String, val filename: String)

@Serializable
data class MessageDetail(
    val id: String,
    val sender: String,
    val subject: String,
    val date: String,
    /** Plain text with line breaks; the TV doesn't render HTML. */
    val text: String,
    val attachments: List<MessageAttachment>,
)

/** Student profile behind one Synergia login. */
@Serializable
data class Profile(val login: String, val studentName: String, val isParent: Boolean)

object GradeMath {
    private const val PLUS_VALUE = 0.5
    private const val MINUS_VALUE = 0.25

    /** "4+" → 4.5, "3-" → 2.75, "np" → null. */
    fun value(grade: String): Double? {
        val trimmed = grade.trim()
        val base = trimmed.firstOrNull()?.digitToIntOrNull()?.takeIf { it in 0..6 } ?: return null
        val rest = trimmed.drop(1)
        return when {
            rest.isEmpty() -> base.toDouble()
            rest == "+" -> base + PLUS_VALUE
            rest == "-" -> base - MINUS_VALUE
            else -> trimmed.replace(',', '.').toDoubleOrNull() ?: base.toDouble()
        }
    }

    /** Weighted average of grades that count (normal grades with weight > 0). */
    fun average(grades: List<Grade>): Double? {
        val counted = grades.filter { it.countsToAverage }
        val weights = counted.sumOf { it.weight }
        if (weights <= 0) return null
        return counted.sumOf { (it.value ?: 0.0) * it.weight } / weights
    }
}

data class AttendanceStats(
    val total: Int,
    val absent: Int,
    val absentExcused: Int,
    val belated: Int,
    val released: Int,
) {
    /** Same formula as the iOS AttendanceView. */
    val percent: Double? get() = if (total > 0) (total - absent - absentExcused) * 100.0 / total else null

    companion object {
        fun of(entries: List<AttendanceEntry>) = AttendanceStats(
            total = entries.size,
            absent = entries.count { it.base == AttendanceBase.ABSENT },
            absentExcused = entries.count { it.base == AttendanceBase.ABSENT_EXCUSED },
            belated = entries.count { it.base == AttendanceBase.BELATED },
            released = entries.count { it.base == AttendanceBase.RELEASED },
        )
    }
}
