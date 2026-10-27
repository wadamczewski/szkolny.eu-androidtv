package pl.prywatny.dziennik.tv.librus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline checks against the synthetic (fake) fixtures shared with the iOS app and webOS.
 * The expected values are the same as in LibrusKitCheck/main.swift. No network, no real data.
 */
class ParsersTest {
    private fun text(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun json(name: String): JsonElement = Json.parseToJsonElement(text(name))

    private fun dict() = Parsers.Dictionaries(
        subjects = Parsers.subjects(json("subjects.json")),
        teachers = Parsers.teachers(json("users.json")),
        classrooms = Parsers.classrooms(json("classrooms.json")),
        gradeCategories = Parsers.gradeCategories(json("grade_categories.json")),
        gradeComments = Parsers.gradeComments(json("grade_comments.json")),
        attendanceTypes = Parsers.attendanceTypes(json("attendance_types.json")),
        lessons = Parsers.lessons(json("lessons.json")),
    )

    @Test
    fun gradeValues() {
        assertEquals(5.0, GradeMath.value("5"))
        assertEquals(4.5, GradeMath.value("4+"))
        assertEquals(2.75, GradeMath.value("3-"))
        assertNull(GradeMath.value("np"))
        assertNull(GradeMath.value("+"))
    }

    @Test
    fun formEncodingMatchesSwift() {
        assertEquals("wyslij=Wy%C5%9Blij", HttpClient.formEncode(listOf("wyslij" to "Wyślij")))
        assertEquals("t=a+b%2Bc%26d%3De", HttpClient.formEncode(listOf("t" to "a b+c&d=e")))
        assertEquals("x=1&x=2", HttpClient.formEncode(listOf("x" to "1", "x" to "2")))
    }

    @Test
    fun dictionaries() {
        val d = dict()
        assertEquals("subjects incl. synthetic Zachowanie", 3, d.subjects.size)
        assertEquals("Jan Testowy", d.teachers[500]?.fullName)
        assertEquals("Anna Przykładowa-Nowak", d.teachers[501]?.fullName)
        assertEquals(3.0, d.gradeCategories[1]?.weight)
        assertEquals("Sprawdzian", d.gradeCategories[1]?.name)
        assertEquals("string weight", 2.0, d.gradeCategories[2]?.weight)
        assertEquals("not counted → weight 0", 0.0, d.gradeCategories[3]?.weight)
        assertEquals("12 sala matematyczna", d.classrooms[3])
    }

    @Test
    fun grades() {
        val grades = Parsers.grades(json("grades.json"), dict())
        fun g(id: Int) = grades.first { it.id == id }
        assertEquals(6, grades.size)
        assertEquals("Bardzo dobra praca (dane fikcyjne)", g(1001).comment)
        assertEquals("#FF0000", g(1001).color)
        assertEquals("2025-10-01 10:00:00", g(1001).addedDate)
        assertEquals("'+' has no weight", 0.0, g(1003).weight, 0.0)
        assertEquals(1005, g(1004).improvedById)
        assertEquals("improved nb weight 0", 0.0, g(1004).weight, 0.0)
        assertTrue(g(1005).isImprovement)
        assertEquals(GradeKind.SEMESTER_PROPOSED, g(1006).kind)
        assertEquals((5.0 * 3 + 2.75 * 2) / 5, GradeMath.average(grades.filter { it.subjectId == 10 })!!, 0.0001)
        assertEquals(4.5, GradeMath.average(grades.filter { it.subjectId == 11 })!!, 0.0001)
    }

    @Test
    fun timetable() {
        val week = Parsers.timetable(json("timetable.json"), dict())
        assertEquals(2, week.size)
        val monday = week[0].lessons
        assertEquals(3, monday.size)
        assertEquals("Jan Testowy", monday[0].teacher)
        assertEquals("12 sala matematyczna", monday[0].classroom)
        assertEquals(LessonKind.CANCELLED, monday[1].kind)
        assertEquals(LessonKind.CHANGE, monday[2].kind)
        assertEquals("Język polski", monday[2].oldSubject)
        assertTrue(week[1].lessons.isEmpty())
    }

    @Test
    fun days() {
        assertEquals("2025-10-06", Day.weekStart("2025-10-08"))
        assertEquals("Sunday belongs to previous week", "2025-10-06", Day.weekStart("2025-10-12"))
        assertNull(Day.parse("2025-02-30"))
        assertEquals("2026-01-01", Day.addDays("2025-12-31", 1))
        assertEquals("Saturday shows next week", "2025-10-13", Day.defaultTimetableWeek("2025-10-11"))
        assertEquals("2025-10-06", Day.defaultTimetableWeek("2025-10-08"))
    }

    @Test
    fun attendanceAndLuckyNumber() {
        val attendance = Parsers.attendance(json("attendances.json"), dict())
        assertEquals(2, attendance.size)
        val absence = attendance.first { it.id == "t123" }
        assertEquals(AttendanceBase.ABSENT, absence.base)
        assertEquals("Matematyka", absence.subject)
        assertEquals("#FF0000", absence.color)
        assertEquals(13, Parsers.luckyNumber(json("lucky.json"))?.number)
        val stats = AttendanceStats.of(attendance)
        assertEquals(2, stats.total)
        assertEquals(50.0, stats.percent!!, 0.0001)
    }

    @Test
    fun synergiaMessagesHtml() {
        val inbox = SynergiaParser.messageList(text("inbox.html"), MessageFolder.INBOX)
        assertEquals(2, inbox.size)
        assertEquals("111111", inbox[0].id)
        assertTrue(inbox[0].unread && inbox[0].hasAttachments)
        assertEquals("nbsp cleaned", "Wycieczka klasowa", inbox[0].subject)
        assertFalse(inbox[1].unread || inbox[1].hasAttachments)

        val detail = SynergiaParser.messageDetail(text("message.html"), "111111")
        assertEquals("Testowy Jan (Nauczyciel)", detail.sender)
        assertEquals("Wycieczka klasowa", detail.subject)
        assertEquals("2025-10-01 12:00:00", detail.date)
        assertEquals("Dzień dobry,\nto jest fikcyjna wiadomość testowa.", detail.text)
        assertEquals(listOf(MessageAttachment("555", "111111", "regulamin.pdf")), detail.attachments)

        // Real Synergia pages omit optional end tags (</td>, </tr>); the parser must cope like iOS.
        val optional = SynergiaParser.messageList(text("inbox-optional-end-tags.html"), MessageFolder.INBOX)
        assertEquals(listOf("333333", "444444"), optional.map { it.id })
        assertEquals("Zebranie klasowe", optional[0].subject)
        assertTrue(optional[0].unread && optional[0].hasAttachments)
        assertFalse(optional[1].unread || optional[1].hasAttachments)

        assertFalse(SynergiaParser.isLoggedOut(text("inbox.html")))
        assertTrue(SynergiaParser.isLoggedOut("<a href=\"/loguj/portalRodzina\">Zaloguj</a>"))
    }

    @Test
    fun apiErrorMapping() {
        fun code(json: String, status: Int) = try {
            SynergiaSession.throwError(Json.parseToJsonElement(json), status)
        } catch (e: LibrusException) {
            e.code
        }
        assertEquals(LibrusException.Code.TOKEN_EXPIRED, code("""{"Code":"TokenIsExpired"}""", 401))
        assertEquals(LibrusException.Code.FEATURE_NOT_ACTIVE, code("""{"Code":"LuckyNumberIsNotActive"}""", 403))
        assertEquals(LibrusException.Code.TIMETABLE_NOT_PUBLIC, code("""{"Code":"AccessDeny","Message":"Student timetable is not public"}""", 403))
        assertEquals(LibrusException.Code.API, code("{}", 500))
    }
}
