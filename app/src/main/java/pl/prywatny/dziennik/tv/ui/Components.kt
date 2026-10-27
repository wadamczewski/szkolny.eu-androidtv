package pl.prywatny.dziennik.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import pl.prywatny.dziennik.tv.librus.Grade
import pl.prywatny.dziennik.tv.librus.GradeKind

/** Colors shared with the webOS version (style.css). */
object C {
    val bg = Color(0xFF0E1116)
    val surface = Color(0xFF171B22)
    val surface2 = Color(0xFF1F242D)
    val surface3 = Color(0xFF2A303B)
    val line = Color(0xFF343B48)
    val text = Color(0xFFF2F4F7)
    val muted = Color(0xFF9AA3B2)
    val accent = Color(0xFFE86B2E)
    val accentSoft = Color(0x2EE86B2E)
    val good = Color(0xFF3FB97F)
    val bad = Color(0xFFEF5350)
    val warn = Color(0xFFF4B740)
    val info = Color(0xFF5AA9F0)
}

@Composable
fun DziennikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = Color.White,
            background = C.bg,
            onBackground = C.text,
            surface = C.surface,
            onSurface = C.text,
            surfaceVariant = C.surface2,
            onSurfaceVariant = C.muted,
            border = C.accent,
        ),
        content = content,
    )
}

fun hexColor(hex: String?): Color? {
    val s = hex?.removePrefix("#") ?: return null
    if (s.length != 6) return null
    return s.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

object T {
    val h1 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.text)
    val h2 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.text)
    val label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.muted, letterSpacing = 0.8.sp)
    val body = TextStyle(fontSize = 14.sp, color = C.text)
    val bodyBold = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    val small = TextStyle(fontSize = 12.sp, color = C.muted)
}

/** Anything selectable with the remote: a tile/row with a clear focus ring. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    container: Color = C.surface,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    padding: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    if (onFocus != null) m = m.onFocusChanged { if (it.isFocused) onFocus() }
    Surface(
        onClick = onClick,
        modifier = m,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = C.text,
            focusedContainerColor = C.surface3,
            focusedContentColor = C.text,
            pressedContainerColor = C.surface3,
            pressedContentColor = C.text,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, C.accent), shape = RoundedCornerShape(10.dp)) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, C.accent), shape = RoundedCornerShape(10.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** Pill button (header actions, toggles). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        container = when {
            primary -> C.accent
            selected -> C.accentSoft
            else -> C.surface2
        },
        focusRequester = focusRequester,
        padding = 0,
    ) {
        Text(
            text,
            style = T.bodyBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
fun Header(title: String, subtitle: String?, actions: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.h1)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = T.body.copy(color = C.muted))
        }
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(10.dp)).padding(20.dp),
    ) { Text(text, style = T.body.copy(color = C.muted)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.label, modifier = modifier)
}

@Composable
fun GradeChip(grade: Grade, large: Boolean = false) {
    val border = hexColor(grade.color) ?: C.line
    val size = if (large) 38.dp else 24.dp
    Box(
        Modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(C.surface2, RoundedCornerShape(if (large) 8.dp else 6.dp))
            .border(if (large) 2.dp else 1.5.dp, border, RoundedCornerShape(if (large) 8.dp else 6.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            grade.name,
            style = TextStyle(
                fontSize = if (large) 17.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (grade.improvedById != null) C.muted else C.text,
                textDecoration = if (grade.improvedById != null) TextDecoration.LineThrough else null,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun ColorBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(4.dp).background(color, RoundedCornerShape(2.dp)))
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(7.dp).background(color, CircleShape))
}

@Composable
fun OneLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun Gap(dp: Int) = Spacer(Modifier.size(dp.dp))

fun GradeKind.short(): String = if (isProposed) "przew." else "końc."
