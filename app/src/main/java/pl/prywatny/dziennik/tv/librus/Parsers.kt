package pl.prywatny.dziennik.tv.librus

import kotlinx.serialization.json.JsonElement

/**
 * Pure JSON → model mapping, ported from LibrusParsers.swift (itself a port of the
 * `LibrusApi*.kt` endpoint classes of szkolny-android). No networking, checked against fixtures.
 */
object Parsers {
    data class GradeCategory(val name: String, val weight: Double, val color: String?)
    data class EventCategory(val name: String, val color: String?)
    data class AttendanceType(val name: String, val short: String, val base: AttendanceBase, val color: String?)
    data class LessonRef(val subjectId: Int?, val teacherId: Int?)

    data class Dictionaries(
        val subjects: Map<Int, Subject> = emptyMap(),
        val teachers: Map<Int, Teacher> = emptyMap(),
        val classrooms: Map<Int, String> = emptyMap(),
        val gradeCategories: Map<Int, GradeCategory> = emptyMap(),
        val gradeComments: Map<Int, String> = emptyMap(),
        val eventCategories: Map<Int, EventCategory> = emptyMap(),
        val attendanceTypes: Map<Int, AttendanceType> = emptyMap(),
        val noticeCategories: Map<Int, String> = emptyMap(),
        /** Lessons/Id → (subject, teacher), used by attendance entries. */
        val lessons: Map<Int, LessonRef> = emptyMap(),
    ) {
        fun subjectName(id: Int?): String? = id?.let { subjects[it]?.name }
        fun teacherName(id: Int?): String? = id?.let { teachers[it]?.fullName }
    }

    // MARK: Dictionaries

    fun subjects(json: JsonElement?): Map<Int, Subject> {
        val result = mutableMapOf<Int, Subject>()
        for (s in json["Subjects"].array) {
            val id = s["Id"].int ?: continue
            result[id] = Subject(id, s["Name"].str ?: "", s["Short"].str ?: "")
        }
        result.getOrPut(1) { Subject(1, "Zachowanie", "zach") }
        return result
    }

    fun teachers(json: JsonElement?): Map<Int, Teacher> =
        json["Users"].array.mapNotNull { u ->
            val id = u["Id"].int ?: return@mapNotNull null
            id to Teacher(id, (u["FirstName"].str ?: "").fixedName(), (u["LastName"].str ?: "").fixedName())
        }.toMap()

    fun classrooms(json: JsonElement?): Map<Int, String> =
        json["Classrooms"].array.mapNotNull { c ->
            val id = c["Id"].int ?: return@mapNotNull null
            val name = c["Name"].str ?: ""
            val symbol = c["Symbol"].str ?: ""
            id to if (symbol.isEmpty() || name.lowercase().contains(symbol.lowercase())) name else "$symbol $name"
        }.toMap()

    fun gradeCategories(json: JsonElement?): Map<Int, GradeCategory> =
        json["Categories"].array.mapNotNull { c ->
            val id = c["Id"].int ?: return@mapNotNull null
            val weight = if (c["CountToTheAverage"].bool == true) c["Weight"].double ?: 0.0 else 0.0
            id to GradeCategory((c["Name"].str ?: "").trim(), weight, librusColor(c["Color"].refId))
        }.toMap()

    fun gradeComments(json: JsonElement?): Map<Int, String> =
        json["Comments"].array.mapNotNull { c ->
            val id = c["Id"].int ?: return@mapNotNull null
            val text = c["Text"].str ?: return@mapNotNull null
            id to text.trim()
        }.toMap()

    fun eventCategories(json: JsonElement?): Map<Int, EventCategory> =
        json["Categories"].array.mapNotNull { c ->
            val id = c["Id"].int ?: return@mapNotNull null
            id to EventCategory(c["Name"].str ?: "", librusColor(c["Color"].refId))
        }.toMap()

    fun attendanceTypes(json: JsonElement?): Map<Int, AttendanceType> =
        json["Types"].array.mapNotNull { t ->
            val id = t["Id"].int ?: return@mapNotNull null
            val standard = t["Standard"].bool ?: false
            val base = when (t["StandardType"].refId ?: id) {
                1 -> AttendanceBase.ABSENT
                2 -> AttendanceBase.BELATED
                3 -> AttendanceBase.ABSENT_EXCUSED
                4 -> AttendanceBase.RELEASED
                else -> if (standard) AttendanceBase.PRESENT else AttendanceBase.PRESENT_CUSTOM
            }
            id to AttendanceType(t["Name"].str ?: "", t["Short"].str ?: "?", base, t["ColorRGB"].str?.let { "#$it" })
        }.toMap()

    fun noticeCategories(json: JsonElement?): Map<Int, String> =
        json["Categories"].array.mapNotNull { c ->
            val id = c["Id"].int ?: return@mapNotNull null
            id to (c["CategoryName"].str ?: "")
        }.toMap()

    fun lessons(json: JsonElement?): Map<Int, LessonRef> =
        json["Lessons"].array.mapNotNull { l ->
            val id = l["Id"].int ?: return@mapNotNull null
            id to LessonRef(l["Subject"].refId, l["Teacher"].refId)
        }.toMap()

    // MARK: Data

    fun grades(json: JsonElement?, dict: Dictionaries): List<Grade> {
        val improvements = mutableMapOf<Int, Int>() // old grade id → new grade id
        val grades = json["Grades"].array.mapNotNull { g ->
            val id = g["Id"].int ?: return@mapNotNull null
            val semester = g["Semester"].int ?: return@mapNotNull null
            val name = g["Grade"].str ?: ""
            val category = g["Category"].refId?.let { dict.gradeCategories[it] }
            val kind = when {
                g["IsConstituent"].bool == true -> GradeKind.NORMAL
                g["IsSemester"].bool == true -> GradeKind.SEMESTER_FINAL
                g["IsSemesterProposition"].bool == true -> GradeKind.SEMESTER_PROPOSED
                g["IsFinal"].bool == true -> GradeKind.YEAR_FINAL
                g["IsFinalProposition"].bool == true -> GradeKind.YEAR_PROPOSED
                else -> GradeKind.NORMAL
            }
            val zeroWeight = name.lowercase() in setOf("-", "+", "np", "bz")
            g["Improvement"].refId?.let { improvements[it] = id }
            Grade(
                id = id,
                name = name,
                kind = kind,
                value = GradeMath.value(name),
                weight = if (zeroWeight) 0.0 else category?.weight ?: 0.0,
                category = category?.name ?: "",
                color = category?.color,
                comment = g["Comments"].array.firstOrNull()?.refId?.let { dict.gradeComments[it] },
                semester = semester,
                teacher = dict.teacherName(g["AddedBy"].refId),
                subjectId = g["Subject"].refId ?: -1,
                addedDate = Day.dateTime(g["AddDate"].str),
                isImprovement = !g["Improvement"].isNull,
            )
        }
        return grades.map { grade ->
            val improvedBy = improvements[grade.id]
            grade.copy(
                improvedById = improvedBy,
                weight = if (improvedBy != null && grade.name.lowercase() == "nb") 0.0 else grade.weight,
            )
        }
    }

    fun timetable(json: JsonElement?, dict: Dictionaries): List<TimetableDay> {
        val days = json["Timetable"].obj ?: return emptyList()
        return days.mapNotNull { (dateString, dayJson) ->
            val day = Day.parse(dateString) ?: return@mapNotNull null
            val lessons = dayJson.array.flatMap { it.array }.mapNotNull { lesson(day, it, dict) }
            TimetableDay(day, lessons.sortedWith(compareBy({ it.number }, { it.start })))
        }.sortedBy { it.day }
    }

    private fun lesson(day: String, l: JsonElement, dict: Dictionaries): Lesson? {
        val number = l["LessonNo"].int ?: return null
        val start = l["HourFrom"].str ?: return null
        val end = l["HourTo"].str ?: return null
        val isSubstitution = l["IsSubstitutionClass"].bool ?: false
        val isCancelled = l["IsCanceled"].bool ?: false
        val subject = l["Subject"]["Name"].str ?: dict.subjectName(l["Subject"].refId)
        val teacher = dict.teacherName(l["Teacher"].refId)
            ?: listOfNotNull(l["Teacher"]["FirstName"].str, l["Teacher"]["LastName"].str).joinToString(" ").ifEmpty { null }
        val classroom = l["Classroom"].refId?.let { dict.classrooms[it] }
        val base = Lesson(day, number, start, end, LessonKind.NORMAL, subject, teacher, classroom)

        if (isSubstitution && isCancelled) {
            // Lesson moved away from here.
            return base.copy(kind = LessonKind.SHIFTED_SOURCE, otherDay = Day.parse(l["NewDate"].str), otherNumber = l["NewLessonNo"].int)
        }
        if (isSubstitution) {
            val orgDay = Day.parse(l["OrgDate"].str)
            val orgNumber = l["OrgLessonNo"].int
            val moved = orgDay != null && (orgDay != day || orgNumber != number)
            return base.copy(
                kind = if (moved) LessonKind.SHIFTED_TARGET else LessonKind.CHANGE,
                oldSubject = dict.subjectName(l["OrgSubject"].refId),
                oldTeacher = dict.teacherName(l["OrgTeacher"].refId),
                otherDay = if (moved) orgDay else null,
                otherNumber = if (moved) orgNumber else null,
            )
        }
        return if (isCancelled) base.copy(kind = LessonKind.CANCELLED) else base
    }

    fun events(json: JsonElement?, dict: Dictionaries): List<AgendaEvent> =
        json["HomeWorks"].array.mapNotNull { e ->
            val id = e["Id"].int ?: return@mapNotNull null
            val day = Day.parse(e["Date"].str) ?: return@mapNotNull null
            var topic = (e["Content"].str ?: "").trim()
            val online = e["onlineLessonUrl"]
            online["url"].str?.let { url -> topic += "\n\n" + (online["text"].str?.let { "$it - " } ?: "") + url }
            val category = e["Category"].refId?.let { dict.eventCategories[it] }
            AgendaEvent(
                id = id,
                day = day,
                time = e["TimeFrom"].str?.take(5),
                lessonNumber = e["LessonNo"].int,
                topic = topic,
                category = category?.name ?: "Wydarzenie",
                color = category?.color,
                subject = dict.subjectName(e["Subject"].refId),
                teacher = dict.teacherName(e["CreatedBy"].refId),
                addedDate = Day.dateTime(e["AddDate"].str),
            )
        }.sortedBy { it.day }

    fun attendance(json: JsonElement?, dict: Dictionaries): List<AttendanceEntry> =
        json["Attendances"].array.mapNotNull { a ->
            val rawId = a["Id"].str ?: return@mapNotNull null
            val number = a["LessonNo"].int ?: return@mapNotNull null
            val day = Day.parse(a["Date"].str) ?: return@mapNotNull null
            val semester = a["Semester"].int ?: return@mapNotNull null
            val typeId = a["Type"].refId ?: return@mapNotNull null
            val type = dict.attendanceTypes[typeId]
            val lesson = a["Lesson"].refId?.let { dict.lessons[it] }
            AttendanceEntry(
                id = rawId,
                day = day,
                lessonNumber = number,
                typeName = type?.name ?: "nieznany rodzaj",
                typeShort = type?.short ?: "?",
                base = type?.base ?: AttendanceBase.UNKNOWN,
                color = type?.color,
                subject = dict.subjectName(lesson?.subjectId),
                teacher = dict.teacherName(a["AddedBy"].refId ?: lesson?.teacherId),
                semester = semester,
            )
        }.sortedWith(compareByDescending<AttendanceEntry> { it.day }.thenByDescending { it.lessonNumber })

    fun notices(json: JsonElement?, dict: Dictionaries): List<Notice> =
        json["Notes"].array.mapNotNull { n ->
            val id = n["Id"].int ?: return@mapNotNull null
            val day = Day.parse(n["Date"].str) ?: return@mapNotNull null
            val kind = when (n["Positive"].int) {
                0 -> NoticeKind.NEGATIVE
                1 -> NoticeKind.POSITIVE
                else -> NoticeKind.NEUTRAL
            }
            Notice(
                id, day, kind,
                category = n["Category"].refId?.let { dict.noticeCategories[it] } ?: "",
                text = n["Text"].str ?: "",
                teacher = dict.teacherName(n["Teacher"].refId),
            )
        }.sortedByDescending { it.day }

    fun announcements(json: JsonElement?, dict: Dictionaries): List<Announcement> =
        json["SchoolNotices"].array.mapNotNull { a ->
            val id = a["Id"].str ?: return@mapNotNull null
            Announcement(
                id = id,
                subject = a["Subject"].str ?: "",
                text = a["Content"].str ?: "",
                startDay = Day.parse(a["StartDate"].str),
                endDay = Day.parse(a["EndDate"].str),
                author = dict.teacherName(a["AddedBy"].refId),
                read = a["WasRead"].bool ?: false,
            )
        }.sortedByDescending { it.startDay ?: "" }

    fun luckyNumber(json: JsonElement?): LuckyNumber? {
        val l = json["LuckyNumber"]
        val number = l["LuckyNumber"].int ?: return null
        val day = Day.parse(l["LuckyNumberDay"].str) ?: return null
        return LuckyNumber(day, number)
    }

    /** Librus color ids → hex (table from `DataLibrus.getColor`). */
    private val COLORS = mapOf(
        1 to "#F0E68C", 2 to "#87CEFA", 3 to "#B0C4DE", 4 to "#F0F8FF", 5 to "#F0FFFF", 6 to "#F5F5DC",
        7 to "#FFEBCD", 8 to "#FFF8DC", 9 to "#A9A9A9", 10 to "#BDB76B", 11 to "#8FBC8F", 12 to "#DCDCDC",
        13 to "#DAA520", 14 to "#E6E6FA", 15 to "#FFA07A", 16 to "#32CD32", 17 to "#66CDAA", 18 to "#66CDAA",
        19 to "#C0C0C0", 20 to "#D2B48C", 21 to "#3333FF", 22 to "#7B68EE", 23 to "#BA55D3", 24 to "#FFB6C1",
        25 to "#FF1493", 26 to "#DC143C", 27 to "#FF0000", 28 to "#FF8C00", 29 to "#FFD700", 30 to "#ADFF2F",
        31 to "#7CFC00",
    )

    fun librusColor(id: Int?): String = id?.let { COLORS[it] } ?: "#2196F3"
}
