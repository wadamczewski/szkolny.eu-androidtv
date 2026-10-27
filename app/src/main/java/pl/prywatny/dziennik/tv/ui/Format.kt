package pl.prywatny.dziennik.tv.ui

import pl.prywatny.dziennik.tv.librus.Day
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Polish date and number formatting. School days are "yyyy-MM-dd" strings. */
object Fmt {
    private val DAY_NAMES = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")
    private val DAY_SHORT = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")
    private val MONTHS = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    private val MONTHS_SHORT = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    private fun date(day: String) = LocalDate.parse(day)

    fun dayLong(day: String?): String {
        if (day == null) return ""
        val t = Day.today()
        return when (day) {
            t -> "Dzisiaj"
            Day.addDays(t, 1) -> "Jutro"
            Day.addDays(t, -1) -> "Wczoraj"
            else -> "${DAY_NAMES[Day.weekday(day)].replaceFirstChar(Char::uppercaseChar)}, ${date(day).dayOfMonth} ${MONTHS[date(day).monthValue - 1]}"
        }
    }

    /** "Dzisiaj", "Jutro" or "Śr 4 lis". */
    fun dayCompact(day: String): String {
        val t = Day.today()
        return when (day) {
            t -> "Dzisiaj"
            Day.addDays(t, 1) -> "Jutro"
            else -> "${DAY_SHORT[Day.weekday(day)]} ${dayShort(day)}"
        }
    }

    fun dayShort(day: String?): String = day?.let { "${date(it).dayOfMonth} ${MONTHS_SHORT[date(it).monthValue - 1]}" } ?: ""

    fun dayMedium(day: String): String = "${date(day).dayOfMonth} ${MONTHS[date(day).monthValue - 1]}"

    fun dayName(day: String): String = DAY_NAMES[Day.weekday(day)].replaceFirstChar(Char::uppercaseChar)

    fun weekRange(start: String): String {
        val a = date(start)
        val b = a.plusDays(4)
        return if (a.monthValue == b.monthValue) "${a.dayOfMonth}–${b.dayOfMonth} ${MONTHS[b.monthValue - 1]} ${b.year}"
        else "${a.dayOfMonth} ${MONTHS[a.monthValue - 1]} – ${b.dayOfMonth} ${MONTHS[b.monthValue - 1]} ${b.year}"
    }

    fun dateLong(): String {
        val d = LocalDate.now()
        return "${DAY_NAMES[d.dayOfWeek.value - 1].replaceFirstChar(Char::uppercaseChar)}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }

    fun time(): String = LocalTime.now().format(HHMM)

    /** Minutes since midnight for "08:55". */
    fun minutes(hhmm: String): Int = hhmm.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** "2025-10-01 10:00:00" → "1 paź". */
    fun addedShort(dateTime: String?): String = dateTime?.let { dayShort(it.take(10)) } ?: ""

    fun since(timestamp: Long?): String {
        if (timestamp == null) return "nigdy"
        val minutes = ((System.currentTimeMillis() - timestamp) / 60_000).toInt()
        return when {
            minutes < 1 -> "przed chwilą"
            minutes < 60 -> "$minutes min temu"
            else -> {
                val dt = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                val day = dt.toLocalDate().toString()
                (if (day == Day.today()) "dziś " else "${dayShort(day)} ") + dt.toLocalTime().format(HHMM)
            }
        }
    }

    fun average(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.2f", it).replace('.', ',') } ?: "—"

    fun percent(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.1f", it).replace('.', ',') + "%" } ?: "—"

    fun weight(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
}
