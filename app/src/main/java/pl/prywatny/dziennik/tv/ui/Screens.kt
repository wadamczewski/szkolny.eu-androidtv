package pl.prywatny.dziennik.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import pl.prywatny.dziennik.tv.librus.AttendanceBase
import pl.prywatny.dziennik.tv.librus.AttendanceStats
import pl.prywatny.dziennik.tv.librus.Day
import pl.prywatny.dziennik.tv.librus.GradeKind
import pl.prywatny.dziennik.tv.librus.GradeMath
import pl.prywatny.dziennik.tv.librus.Lesson
import pl.prywatny.dziennik.tv.librus.LessonKind
import pl.prywatny.dziennik.tv.librus.MessageDetail
import pl.prywatny.dziennik.tv.librus.MessageFolder
import pl.prywatny.dziennik.tv.librus.MessageSummary
import pl.prywatny.dziennik.tv.librus.NoticeKind
import pl.prywatny.dziennik.tv.librus.StudentData
import pl.prywatny.dziennik.tv.librus.Subject

/** Per-student UI choices (semester, folder…) and the "focus the content" request. */
class ScreenUi {
    var semester by mutableStateOf<Int?>(null)
    var agendaPast by mutableStateOf(false)
    var attendanceScope by mutableStateOf(0) // 0 = whole year, 1, 2
    var folder by mutableStateOf(MessageFolder.INBOX)
    var pendingFocus by mutableStateOf(false)
    var contentVersion by mutableStateOf(0)
}

@Composable
fun SectionContent(
    vm: AppViewModel,
    ui: ScreenUi,
    first: FocusRequester,
    go: (Section) -> Unit,
    openDialog: ((@Composable () -> Unit)?) -> Unit,
) {
    val data = vm.data
    when (vm.section) {
        Section.TIMETABLE -> Timetable(vm, first)
        Section.MESSAGES -> Messages(vm, ui, first, openDialog)
        Section.ACCOUNT -> Account(vm, first, openDialog)
        else -> if (data == null) {
            Column {
                Header(vm.section.label, null)
                Empty(if (vm.syncing) "Pobieranie danych z Librusa…" else vm.error ?: "Brak danych. Odśwież w zakładce Konto.")
            }
        } else when (vm.section) {
            Section.HOME -> Home(vm, data, first, go)
            Section.GRADES -> Grades(data, ui, first)
            Section.AGENDA -> Agenda(data, ui, first)
            Section.ATTENDANCE -> Attendance(data, ui, first)
            Section.NOTICES -> Notices(data, first)
            Section.ANNOUNCEMENTS -> Announcements(data, first, openDialog)
            else -> Unit
        }
    }
}

// MARK: Start

private fun lessonsOn(vm: AppViewModel, day: String): List<Lesson>? =
    (vm.weeks[Day.weekStart(day)] as? Load.Ready)?.value?.firstOrNull { it.day == day }?.lessons

/** Today's plan while lessons are on, else the next day with lessons in the loaded weeks. */
private fun upcomingSchoolDay(vm: AppViewModel): Pair<String, List<Lesson>>? {
    val today = Day.today()
    for (i in 0L until 14L) {
        val day = Day.addDays(today, i)
        val lessons = lessonsOn(vm, day)?.takeIf { it.isNotEmpty() } ?: continue
        if (i == 0L && Fmt.minutes(lessons.last().end) < Fmt.nowMinutes()) continue
        return day to lessons
    }
    return null
}

private enum class LessonTime { PAST, NOW, LATER }

private fun lessonTime(l: Lesson): LessonTime {
    if (l.day != Day.today()) return LessonTime.LATER
    val now = Fmt.nowMinutes()
    return when {
        now >= Fmt.minutes(l.end) -> LessonTime.PAST
        now >= Fmt.minutes(l.start) -> LessonTime.NOW
        else -> LessonTime.LATER
    }
}

@Composable
private fun Home(vm: AppViewModel, d: StudentData, first: FocusRequester, go: (Section) -> Unit) {
    val today = Day.today()
    Column(Modifier.fillMaxSize()) {
        Header(Fmt.dateLong(), listOfNotNull(d.studentName, d.accountName?.let { "konto: $it" }).joinToString(" · "))
        Row(Modifier.weight(1f).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val plan = upcomingSchoolDay(vm)
            FocusCard(onClick = { go(Section.TIMETABLE) }, modifier = Modifier.weight(1.25f).fillMaxHeight(), focusRequester = first) {
                SectionLabel(if (plan != null) "Plan: ${Fmt.dayLong(plan.first)}" else "Plan lekcji")
                Gap(8)
                if (plan == null) Text("Brak lekcji w najbliższych dniach.", style = T.body.copy(color = C.muted))
                plan?.second?.forEach { l ->
                    val time = lessonTime(l)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (time == LessonTime.NOW) C.accentSoft else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dim = if (time == LessonTime.PAST) C.muted else C.text
                        Text("${l.number}", style = T.bodyBold.copy(color = C.muted), modifier = Modifier.width(20.dp))
                        Text("${l.start}–${l.end}", style = T.small, modifier = Modifier.width(74.dp))
                        OneLine(
                            (l.subject ?: "—") + (l.kind.label?.let { " · $it" } ?: ""),
                            T.body.copy(color = dim, textDecoration = if (l.kind == LessonKind.CANCELLED) TextDecoration.LineThrough else null),
                            Modifier.weight(1f),
                        )
                        OneLine(l.classroom ?: "", T.small.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), Modifier.width(44.dp))
                    }
                }
            }
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusCard(onClick = { go(Section.AGENDA) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SectionLabel("Najbliższe w terminarzu")
                        Gap(8)
                        val upcoming = d.events.filter { it.day >= today && it.day <= Day.addDays(today, 14) }.take(4)
                        if (upcoming.isEmpty()) Text("Nic na 2 tygodnie.", style = T.body.copy(color = C.muted))
                        upcoming.forEach { e ->
                            Row(Modifier.padding(vertical = 3.dp).height(IntrinsicSize.Min)) {
                                ColorBar(hexColor(e.color) ?: C.accent, Modifier.fillMaxHeight())
                                Column(Modifier.padding(start = 8.dp)) {
                                    OneLine(listOfNotNull(e.category, e.subject).joinToString(" · "), T.bodyBold)
                                    OneLine(Fmt.dayCompact(e.day) + (e.lessonNumber?.let { ", lekcja $it" } ?: ""), T.small)
                                }
                            }
                        }
                    }
                    FocusCard(onClick = { go(Section.GRADES) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SectionLabel("Ostatnie oceny")
                        Gap(8)
                        val names = d.subjects.associate { it.id to it.name }
                        val recent = d.grades.filter { it.addedDate != null }.sortedByDescending { it.addedDate }.take(5)
                        if (recent.isEmpty()) Text("Brak ocen.", style = T.body.copy(color = C.muted))
                        recent.forEach { g ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                GradeChip(g)
                                OneLine(names[g.subjectId] ?: "—", T.body, Modifier.weight(1f).padding(start = 8.dp))
                                Text(Fmt.addedShort(g.addedDate), style = T.small)
                            }
                        }
                    }
                }
                Row(Modifier.weight(0.85f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusCard(onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SectionLabel("Szczęśliwy numerek")
                        Text(d.luckyNumber?.number?.toString() ?: "—", style = T.h1.copy(fontSize = 54.sp, color = C.accent, fontWeight = FontWeight.ExtraBold))
                        Text(d.luckyNumber?.let { Fmt.dayLong(it.day) } ?: "niedostępny", style = T.small)
                    }
                    FocusCard(onClick = { go(Section.ATTENDANCE) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val stats = AttendanceStats.of(d.attendance)
                        SectionLabel("Frekwencja")
                        Text(Fmt.percent(stats.percent), style = T.h1.copy(fontSize = 40.sp, color = C.accent, fontWeight = FontWeight.ExtraBold))
                        Text("nieobecności: ${stats.absent + stats.absentExcused} · spóźnienia: ${stats.belated}", style = T.small)
                    }
                }
                val inbox = (vm.folders[MessageFolder.INBOX] as? Load.Ready)?.value
                val unreadMessages = inbox?.count { it.unread }
                FocusCard(onClick = { go(if ((unreadMessages ?: 0) > 0) Section.MESSAGES else Section.ANNOUNCEMENTS) }, modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("Wiadomości i ogłoszenia")
                    Gap(6)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(if (unreadMessages == null) "Wiadomości" else "Nowe wiadomości: $unreadMessages", style = T.body)
                        Text("Nowe ogłoszenia: ${d.announcements.count { !it.read }}", style = T.body)
                        d.notices.firstOrNull()?.let { Text("Ostatnia uwaga: ${Fmt.dayShort(it.day)}", style = T.body) }
                    }
                }
            }
        }
    }
}

// MARK: Plan lekcji

@Composable
private fun Timetable(vm: AppViewModel, first: FocusRequester) {
    val start = vm.weekStart
    LaunchedEffect(start, vm.account?.login) { vm.loadWeek(start) }
    Column(Modifier.fillMaxSize()) {
        Header("Plan lekcji", Fmt.weekRange(start)) {
            PillButton("◀", onClick = { vm.weekStart = Day.addDays(start, -7) })
            PillButton("Bieżący tydzień", onClick = { vm.weekStart = Day.defaultTimetableWeek() })
            PillButton("▶", onClick = { vm.weekStart = Day.addDays(start, 7) })
        }
        when (val week = vm.weeks[start]) {
            null, Load.Loading -> Empty("Wczytywanie tygodnia…")
            is Load.Failed -> Empty(week.message)
            is Load.Ready -> {
                val days = week.value
                val weekDays = (0L..4L).map { Day.addDays(start, it) } +
                    (5L..6L).map { Day.addDays(start, it) }.filter { day -> days.any { it.day == day && it.lessons.isNotEmpty() } }
                val numbers = days.flatMap { d -> d.lessons.map { it.number } }.distinct().sorted()
                if (numbers.isEmpty()) {
                    Empty("Brak lekcji w tym tygodniu.")
                    return@Column
                }
                val today = Day.today()
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Box(Modifier.width(26.dp))
                    weekDays.forEach { day ->
                        Row(Modifier.weight(1f).padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.dayName(day), style = T.bodyBold.copy(color = if (day == today) C.accent else C.text))
                            Text("  ${Fmt.dayShort(day)}", style = T.small)
                        }
                    }
                }
                val firstLesson = weekDays.firstNotNullOfOrNull { day ->
                    days.firstOrNull { it.day == day }?.lessons?.firstOrNull { it.number == numbers.first() }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(numbers) { number ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(Modifier.width(26.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text("$number", style = T.h2.copy(color = C.muted))
                            }
                            weekDays.forEach { day ->
                                val lessons = days.firstOrNull { it.day == day }?.lessons?.filter { it.number == number }.orEmpty()
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 3.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    lessons.forEach { l ->
                                        LessonCell(l, if (l === firstLesson) first else null, Modifier.weight(1f, fill = true))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCell(l: Lesson, focusRequester: FocusRequester?, modifier: Modifier) {
    val time = lessonTime(l)
    FocusCard(
        onClick = {},
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        container = if (time == LessonTime.NOW) C.accentSoft else C.surface,
        selected = time == LessonTime.NOW,
        focusRequester = focusRequester,
        padding = 7,
    ) {
        val dim = time == LessonTime.PAST
        Text("${l.start}–${l.end}", style = T.small)
        OneLine(
            l.subject ?: "—",
            T.bodyBold.copy(
                color = if (dim || l.kind == LessonKind.CANCELLED) C.muted else C.text,
                textDecoration = if (l.kind == LessonKind.CANCELLED) TextDecoration.LineThrough else null,
            ),
        )
        OneLine(listOfNotNull(l.classroom?.let { "s. $it" }, l.teacher).joinToString(" · "), T.small)
        l.kind.label?.let { label ->
            val parts = mutableListOf(label)
            if (l.kind == LessonKind.CHANGE && l.oldSubject != null) parts += "zamiast: ${l.oldSubject}"
            l.otherDay?.let { parts += "${if (l.kind == LessonKind.SHIFTED_SOURCE) "na" else "z"}: ${Fmt.dayShort(it)}, lekcja ${l.otherNumber ?: "?"}" }
            val color = when (l.kind) {
                LessonKind.CANCELLED -> C.bad
                LessonKind.CHANGE -> C.warn
                else -> C.info
            }
            Text(parts.joinToString(" · "), style = T.small.copy(color = color, fontWeight = FontWeight.SemiBold))
        }
    }
}

// MARK: Oceny

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Grades(d: StudentData, ui: ScreenUi, first: FocusRequester) {
    // Default: the semester with the newest grade (like the iOS app).
    val semester = ui.semester ?: d.grades.filter { it.addedDate != null }.maxByOrNull { it.addedDate!! }?.semester ?: 1
    val rows = d.subjects.mapNotNull { s ->
        val grades = d.grades.filter { it.subjectId == s.id && it.semester == semester }.sortedBy { it.addedDate ?: "" }
        if (grades.isEmpty()) null else s to grades
    }
    val averages = rows.mapNotNull { GradeMath.average(it.second) }
    val overall = if (averages.isEmpty()) null else averages.average()
    var selected by remember(semester) { mutableStateOf<Subject?>(rows.firstOrNull()?.first) }

    Column(Modifier.fillMaxSize()) {
        Header("Oceny", "Średnia ze średnich: ${Fmt.average(overall)}") {
            PillButton("Semestr 1", onClick = { ui.semester = 1 }, selected = semester == 1)
            PillButton("Semestr 2", onClick = { ui.semester = 2 }, selected = semester == 2)
        }
        if (rows.isEmpty()) {
            Empty("Brak ocen w semestrze $semester.")
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(rows, key = { it.first.id }) { (subject, grades) ->
                    FocusCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        selected = selected?.id == subject.id,
                        focusRequester = if (subject.id == rows.first().first.id) first else null,
                        onFocus = { selected = subject },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                OneLine(subject.name, T.bodyBold)
                                Gap(4)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    grades.filter { it.kind == GradeKind.NORMAL }.takeLast(10).forEach { GradeChip(it) }
                                }
                            }
                            grades.lastOrNull { it.kind != GradeKind.NORMAL }?.let {
                                Text("${it.kind.short()} ${it.name}", style = T.small.copy(color = C.text), modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            Text(Fmt.average(GradeMath.average(grades)), style = T.h2.copy(fontSize = 18.sp))
                        }
                    }
                }
            }
            val detail = rows.firstOrNull { it.first.id == selected?.id } ?: rows.first()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(detail.first.name, style = T.h2, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("średnia", style = T.small)
                            Text(Fmt.average(GradeMath.average(detail.second)), style = T.h1.copy(color = C.accent))
                        }
                    }
                    val finals = detail.second.filter { it.kind != GradeKind.NORMAL }
                    if (finals.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            finals.forEach { Text("${it.kind.label}: ${it.name}", style = T.body, modifier = Modifier.background(C.surface2, RoundedCornerShape(6.dp)).padding(6.dp)) }
                        }
                    }
                }
                items(detail.second.filter { it.kind == GradeKind.NORMAL }.reversed(), key = { it.id }) { g ->
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GradeChip(g, large = true)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(g.category.ifEmpty { "Ocena" }, style = T.bodyBold)
                                Text(
                                    listOfNotNull(
                                        "waga ${Fmt.weight(g.weight)}", Fmt.addedShort(g.addedDate), g.teacher,
                                        if (g.improvedById != null) "poprawiona" else null, if (g.isImprovement) "poprawa" else null,
                                    ).joinToString(" · "),
                                    style = T.small,
                                )
                                g.comment?.let { Text(it, style = T.small.copy(color = C.muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Terminarz

@Composable
private fun Agenda(d: StudentData, ui: ScreenUi, first: FocusRequester) {
    val today = Day.today()
    val past = ui.agendaPast
    val events = if (past) d.events.filter { it.day < today }.reversed() else d.events.filter { it.day >= today }
    Column(Modifier.fillMaxSize()) {
        Header("Terminarz", if (past) "Minione wydarzenia" else "Sprawdziany, kartkówki i wydarzenia") {
            PillButton("Nadchodzące", onClick = { ui.agendaPast = false }, selected = !past)
            PillButton("Minione", onClick = { ui.agendaPast = true }, selected = past)
        }
        if (events.isEmpty()) {
            Empty(if (past) "Brak minionych wydarzeń." else "Nic nie zaplanowano.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            val groups = events.groupBy { it.day }
            groups.entries.forEachIndexed { gi, (day, list) ->
                item(key = "h-$day") {
                    val label = Fmt.dayLong(day)
                    SectionLabel(if (label.contains(',')) label else "$label · ${Fmt.dayMedium(day)}", Modifier.padding(top = if (gi == 0) 0.dp else 8.dp, bottom = 2.dp))
                }
                items(list, key = { "e-${it.id}" }) { e ->
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth(), focusRequester = if (e === events.first()) first else null) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            ColorBar(hexColor(e.color) ?: C.accent, Modifier.fillMaxHeight())
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(listOfNotNull(e.category, e.subject).joinToString(" · "), style = T.bodyBold)
                                if (e.topic.isNotEmpty()) Text(e.topic, style = T.body)
                                Text(listOfNotNull(e.lessonNumber?.let { "lekcja $it" }, e.time, e.teacher).joinToString(" · "), style = T.small)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Frekwencja

@Composable
private fun Attendance(d: StudentData, ui: ScreenUi, first: FocusRequester) {
    val scope = ui.attendanceScope
    val all = if (scope == 0) d.attendance else d.attendance.filter { it.semester == scope }
    val stats = AttendanceStats.of(all)
    Column(Modifier.fillMaxSize()) {
        Header("Frekwencja", "Obecność: ${Fmt.percent(stats.percent)}") {
            PillButton("Cały rok", onClick = { ui.attendanceScope = 0 }, selected = scope == 0)
            PillButton("Semestr 1", onClick = { ui.attendanceScope = 1 }, selected = scope == 1)
            PillButton("Semestr 2", onClick = { ui.attendanceScope = 2 }, selected = scope == 2)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth(), focusRequester = first) {
                        Text(Fmt.percent(stats.percent), style = T.h1.copy(fontSize = 40.sp, color = C.accent, fontWeight = FontWeight.ExtraBold))
                        Gap(6)
                        listOf(
                            "Lekcje" to stats.total, "Nieobecności" to stats.absent, "Usprawiedliwione" to stats.absentExcused,
                            "Spóźnienia" to stats.belated, "Zwolnienia" to stats.released,
                        ).forEach { (label, value) ->
                            Row { Text(label, style = T.body.copy(color = C.muted), modifier = Modifier.weight(1f)); Text("$value", style = T.bodyBold) }
                        }
                    }
                }
                item { SectionLabel("Według przedmiotów", Modifier.padding(top = 8.dp)) }
                items(all.groupBy { it.subject ?: "—" }.toSortedMap().entries.toList(), key = { it.key }) { (subject, entries) ->
                    val s = AttendanceStats.of(entries)
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                OneLine(subject, T.bodyBold)
                                Text("${s.total} lekcji · nieobecności: ${s.absent + s.absentExcused}", style = T.small)
                            }
                            Text(Fmt.percent(s.percent), style = T.h2)
                        }
                    }
                }
            }
            val entries = all.filter { it.base != AttendanceBase.PRESENT }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (entries.isEmpty()) item { Empty("Brak nieobecności, spóźnień ani zwolnień.") }
                items(entries, key = { it.id }) { e ->
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(width = 34.dp, height = 28.dp).background(hexColor(e.color) ?: C.surface3, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(e.typeShort, style = T.bodyBold.copy(color = if (e.color != null) Color(0xFF111111) else C.text)) }
                            Column(Modifier.padding(start = 10.dp)) {
                                OneLine(e.typeName + (e.subject?.let { " · $it" } ?: ""), T.bodyBold)
                                Text("${Fmt.dayLong(e.day)} · lekcja ${e.lessonNumber}" + (e.teacher?.let { " · $it" } ?: ""), style = T.small)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Wiadomości

@Composable
private fun Messages(vm: AppViewModel, ui: ScreenUi, first: FocusRequester, openDialog: ((@Composable () -> Unit)?) -> Unit) {
    val folder = ui.folder
    LaunchedEffect(folder, vm.account?.login) { vm.loadMessages(folder) }
    Column(Modifier.fillMaxSize()) {
        Header("Wiadomości", "Tylko do odczytu. Odpowiadaj w telefonie.") {
            PillButton("Odebrane", onClick = { ui.folder = MessageFolder.INBOX }, selected = folder == MessageFolder.INBOX)
            PillButton("Wysłane", onClick = { ui.folder = MessageFolder.SENT }, selected = folder == MessageFolder.SENT)
            PillButton("Odśwież", onClick = { vm.loadMessages(folder, force = true) })
        }
        when (val state = vm.folders[folder]) {
            null, Load.Loading -> Empty("Wczytywanie wiadomości…")
            is Load.Failed -> Empty(state.message)
            is Load.Ready -> if (state.value.isEmpty()) Empty("Brak wiadomości.") else LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.value, key = { it.id }) { m ->
                    FocusCard(
                        onClick = { openDialog { MessageReader(vm, m, onClose = { openDialog(null) }) } },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (m === state.value.first()) first else null,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(if (m.unread) C.accent else Color.Transparent)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                OneLine(m.subject.ifEmpty { "(bez tematu)" }, if (m.unread) T.bodyBold else T.body)
                                OneLine("${if (folder == MessageFolder.INBOX) "Od" else "Do"}: ${m.correspondent}", T.small)
                            }
                            if (m.hasAttachments) Text("📎", style = T.body, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(m.date.take(16), style = T.small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageReader(vm: AppViewModel, summary: MessageSummary, onClose: () -> Unit) {
    val detail by produceState<Result<MessageDetail>?>(null, summary.id) {
        value = runCatching { vm.message(summary) }
    }
    Reader(
        title = summary.subject.ifEmpty { "(bez tematu)" },
        subtitle = "${summary.correspondent} · ${summary.date}",
        text = when (val r = detail) {
            null -> "Wczytywanie…"
            else -> r.fold(
                onSuccess = { d ->
                    d.text.ifEmpty { "(pusta wiadomość)" } +
                        if (d.attachments.isNotEmpty()) "\n\nZałączniki (pobierz w telefonie):\n" + d.attachments.joinToString("\n") { "📎 ${it.filename}" } else ""
                },
                onFailure = { it.message ?: "Błąd" },
            )
        },
        onClose = onClose,
    )
}

/** Full-screen reading panel. Each paragraph is focusable, so ▲▼ scrolls through long texts. */
@Composable
fun Reader(title: String, subtitle: String, text: String, onClose: () -> Unit) {
    val firstParagraph = remember { FocusRequester() }
    val paragraphs = text.split(Regex("\n{2,}"))
    LaunchedEffect(text) { firstParagraph.tryFocus() }
    Column(
        Modifier.width(720.dp).fillMaxHeight(0.88f).background(C.surface, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = T.h1)
        Text(subtitle, style = T.body.copy(color = C.muted))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().background(C.bg, RoundedCornerShape(10.dp)),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(paragraphs.size) { i ->
                var focused by remember { mutableStateOf(false) }
                Text(
                    paragraphs[i],
                    style = T.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(firstParagraph) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .background(if (focused) C.surface2 else Color.Transparent, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▲▼ przewijanie · Wstecz – zamknij", style = T.small, modifier = Modifier.weight(1f))
            PillButton("Zamknij", onClick = onClose)
        }
    }
}

// MARK: Uwagi, Ogłoszenia

@Composable
private fun Notices(d: StudentData, first: FocusRequester) {
    Column(Modifier.fillMaxSize()) {
        Header("Uwagi", "${d.notices.size} wpisów")
        if (d.notices.isEmpty()) {
            Empty("Brak uwag.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(d.notices, key = { it.id }) { n ->
                FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth(), focusRequester = if (n === d.notices.first()) first else null) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        ColorBar(
                            when (n.kind) {
                                NoticeKind.POSITIVE -> C.good
                                NoticeKind.NEGATIVE -> C.bad
                                NoticeKind.NEUTRAL -> C.muted
                            },
                            Modifier.fillMaxHeight(),
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(listOf(n.kind.label, n.category).filter { it.isNotEmpty() }.joinToString(" · "), style = T.bodyBold)
                            Text(n.text, style = T.body)
                            Text(listOfNotNull(Fmt.dayLong(n.day), n.teacher).joinToString(" · "), style = T.small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Announcements(d: StudentData, first: FocusRequester, openDialog: ((@Composable () -> Unit)?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Ogłoszenia", "${d.announcements.count { !it.read }} nowych")
        if (d.announcements.isEmpty()) {
            Empty("Brak ogłoszeń.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(d.announcements, key = { it.id }) { a ->
                val subtitle = listOfNotNull(a.startDay?.let { "od ${Fmt.dayShort(it)}" }, a.endDay?.let { "do ${Fmt.dayShort(it)}" }, a.author).joinToString(" · ")
                FocusCard(
                    onClick = { openDialog { Reader(a.subject, subtitle, a.text, onClose = { openDialog(null) }) } },
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (a === d.announcements.first()) first else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (a.read) Color.Transparent else C.accent)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(a.subject, style = T.bodyBold)
                            Text(a.text, style = T.body, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(listOfNotNull(a.startDay?.let { Fmt.dayLong(it) }, a.author).joinToString(" · "), style = T.small)
                        }
                    }
                }
            }
        }
    }
}

// MARK: Konto

@Composable
private fun Account(vm: AppViewModel, first: FocusRequester, openDialog: ((@Composable () -> Unit)?) -> Unit) {
    val current = vm.account ?: return
    Column(Modifier.fillMaxSize()) {
        Header("Konto", "Uczniowie, odświeżanie i informacje")
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { SectionLabel("Uczniowie na tym urządzeniu") }
                items(vm.accounts, key = { it.login }) { a ->
                    val active = a.login == current.login
                    FocusCard(
                        onClick = { if (!active) vm.switchAccount(a.login) },
                        modifier = Modifier.fillMaxWidth(),
                        selected = active,
                        focusRequester = if (a === vm.accounts.first()) first else null,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.studentName, style = T.bodyBold)
                                Text(if (a.login == pl.prywatny.dziennik.tv.librus.DemoSource.LOGIN) "dane demonstracyjne" else "login: ${a.login}", style = T.small)
                            }
                            Text(if (active) "aktywny" else "OK – przełącz", style = T.small.copy(color = if (active) C.accent else C.muted))
                        }
                    }
                }
                item {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillButton("+ Dodaj ucznia", onClick = { vm.addingAccount = true }, modifier = Modifier.fillMaxWidth())
                        PillButton("Odśwież dane teraz", onClick = { vm.refresh(manual = true) }, modifier = Modifier.fillMaxWidth())
                        PillButton("Usuń z urządzenia: ${current.studentName}", onClick = {
                            openDialog {
                                ConfirmRemove(current.studentName, onCancel = { openDialog(null) }, onConfirm = { openDialog(null); vm.removeCurrent() })
                            }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(10.dp)).padding(14.dp)) {
                    SectionLabel("Stan danych")
                    Gap(6)
                    Text("Ostatnia aktualizacja: ${Fmt.since(vm.data?.fetchedAt)}", style = T.body)
                    vm.error?.let { Text(it, style = T.body.copy(color = C.bad)) }
                    vm.data?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
                        Text("Nie wczytały się:", style = T.body)
                        warnings.forEach { Text("• $it", style = T.small.copy(color = C.warn)) }
                    }
                }
                Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(10.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("O aplikacji")
                    Text(
                        "Dziennik 1.0 na Android TV. Nieoficjalny klient Librus® Synergia oparty na otwartym kodzie Szkolny.eu (GPL-3.0). " +
                            "Nie jest powiązany z Librus Sp. z o.o.",
                        style = T.body,
                    )
                    Text("Login i hasło są zaszyfrowane kluczem tego urządzenia. Aplikacja łączy się wyłącznie z serwerami *.librus.pl.", style = T.body)
                    Text("Pilot: OK wybiera, Wstecz wraca do menu. Kolorowe przyciski (jeśli są): czerwony odświeża, zielony zmienia ucznia, żółty plan, niebieski oceny.", style = T.small)
                }
            }
        }
    }
}

@Composable
private fun ConfirmRemove(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.tryFocus() }
    Column(
        Modifier.width(460.dp).background(C.surface, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Usunąć ucznia z urządzenia?", style = T.h2)
        Text("$name: login, hasło i pobrane dane zostaną usunięte z tego urządzenia. Konto w Librusie się nie zmieni.", style = T.body)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Anuluj", onClick = onCancel, focusRequester = cancel)
            PillButton("Usuń", onClick = onConfirm, primary = true)
        }
    }
}
