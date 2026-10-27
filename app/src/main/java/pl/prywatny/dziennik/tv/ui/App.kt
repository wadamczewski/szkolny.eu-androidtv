package pl.prywatny.dziennik.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.prywatny.dziennik.tv.R

private val ICONS: Map<Section, ImageVector> = mapOf(
    Section.HOME to Icons.Filled.Home,
    Section.TIMETABLE to Icons.Filled.DateRange,
    Section.GRADES to Icons.Filled.Star,
    Section.AGENDA to Icons.Filled.List,
    Section.ATTENDANCE to Icons.Filled.Check,
    Section.MESSAGES to Icons.Filled.Email,
    Section.NOTICES to Icons.Filled.Warning,
    Section.ANNOUNCEMENTS to Icons.Filled.Notifications,
    Section.ACCOUNT to Icons.Filled.AccountCircle,
)

/** Requests focus without crashing when the target isn't on screen (yet). */
fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.isSuccess

@Composable
fun DziennikApp(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        if (vm.account == null || vm.addingAccount) {
            LoginScreen(vm)
        } else {
            MainScreen(vm)
        }
        ToastHost(vm)
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    val activity = LocalContext.current as? Activity
    val menuRequesters = remember { Section.entries.associateWith { FocusRequester() } }
    val contentRequester = remember { FocusRequester() }
    var contentFocused by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val ui = remember(vm.account?.login) { ScreenUi() }

    fun enterContent() {
        if (!contentRequester.tryFocus()) menuRequesters[vm.section]?.tryFocus()
    }

    LaunchedEffect(vm.account?.login) {
        delay(50)
        menuRequesters[Section.HOME]?.tryFocus()
    }

    BackHandler(enabled = dialog == null) {
        when {
            contentFocused -> menuRequesters[vm.section]?.tryFocus()
            vm.section != Section.HOME -> {
                vm.section = Section.HOME
                menuRequesters[Section.HOME]?.tryFocus()
            }
            else -> activity?.finish()
        }
    }

    val go: (Section) -> Unit = { section ->
        vm.section = section
        ui.pendingFocus = true
    }

    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || dialog != null) return@onPreviewKeyEvent false
            when (event.key) {
                Key.ProgramRed, Key.MediaPlayPause -> { vm.refresh(manual = true); true }
                Key.ProgramGreen -> { vm.nextStudent(); true }
                Key.ProgramYellow -> { go(Section.TIMETABLE); true }
                Key.ProgramBlue -> { go(Section.GRADES); true }
                Key.ChannelUp, Key.ChannelDown, Key.PageUp, Key.PageDown ->
                    if (vm.section == Section.TIMETABLE) {
                        val back = event.key == Key.ChannelUp || event.key == Key.PageUp
                        vm.weekStart = pl.prywatny.dziennik.tv.librus.Day.addDays(vm.weekStart, if (back) -7 else 7)
                        true
                    } else false
                else -> false
            }
        },
    ) {
        Sidebar(vm, menuRequesters, onOpen = { section -> go(section) })
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 28.dp, top = 24.dp, end = 40.dp)
                .onFocusChanged { contentFocused = it.hasFocus },
        ) {
            Box(Modifier.weight(1f)) {
                SectionContent(vm, ui, contentRequester, go, openDialog = { dialog = it })
            }
            Hints(vm)
        }
    }

    // Focus the first item of a section opened with OK (it composes a frame later).
    LaunchedEffect(vm.section, ui.pendingFocus, ui.contentVersion) {
        if (ui.pendingFocus) {
            delay(60)
            enterContent()
            ui.pendingFocus = false
        }
    }

    dialog?.let { content ->
        DialogFrame(onDismiss = { dialog = null }) { content() }
    }
}

@Composable
private fun Sidebar(vm: AppViewModel, requesters: Map<Section, FocusRequester>, onOpen: (Section) -> Unit) {
    val student = vm.data?.studentName ?: vm.account?.studentName ?: ""
    var focused by remember { mutableStateOf<Section?>(null) }

    // Browsing the menu previews the section; OK goes into it.
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        delay(150)
        if (vm.section != target) vm.section = target
    }

    Column(
        Modifier.width(196.dp).fillMaxHeight().background(C.surface).padding(start = 32.dp, top = 24.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            androidx.compose.foundation.Image(
                painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text("Dziennik", style = T.h2)
                Clock()
            }
        }
        FocusCard(
            onClick = { if (vm.accounts.size > 1) vm.nextStudent() else onOpen(Section.ACCOUNT) },
            container = C.surface2,
            padding = 8,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(C.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initials(student), style = T.bodyBold.copy(color = androidx.compose.ui.graphics.Color.White))
                }
                Column(Modifier.padding(start = 8.dp)) {
                    OneLine(student, T.bodyBold)
                    OneLine(if (vm.accounts.size > 1) "OK – zmień ucznia" else vm.account?.login ?: "", T.small)
                }
            }
        }
        for (section in Section.entries) {
            val active = vm.section == section
            FocusCard(
                onClick = { onOpen(section) },
                modifier = Modifier.fillMaxWidth(),
                container = if (active) C.accentSoft else C.surface,
                focusRequester = requesters[section],
                onFocus = { focused = section },
                padding = 0,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ICONS.getValue(section), contentDescription = null, tint = if (active) C.accent else C.muted, modifier = Modifier.size(18.dp))
                    Text(section.label, style = T.body.copy(color = if (active) C.text else C.muted), modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        Box(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(if (vm.error != null) C.bad else if (vm.syncing) C.warn else C.good)
            Text(
                when {
                    vm.syncing -> "Aktualizowanie…"
                    vm.error != null -> "Błąd odświeżania"
                    else -> "Dane: ${Fmt.since(vm.data?.fetchedAt)}"
                },
                style = T.small,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Fmt.time()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Fmt.time()
        }
    }
    Text(now, style = T.small)
}

@Composable
private fun Hints(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("OK – wybierz", style = T.small)
        Text("Wstecz – menu", style = T.small)
        Text("Odświeżanie: Konto lub ▶❚❚", style = T.small)
        if (vm.section == Section.TIMETABLE) Text("CH+/CH− – tydzień", style = T.small)
    }
}

private fun initials(name: String) =
    name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
private fun ToastHost(vm: AppViewModel) {
    val toast = vm.toast ?: return
    LaunchedEffect(toast.id) {
        delay(if (toast.error) 7000 else 3500)
        vm.dismissToast(toast)
    }
    Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .background(if (toast.error) androidx.compose.ui.graphics.Color(0xFF5C2321) else C.surface3, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text(toast.text, style = T.body) }
    }
}

@Composable
fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xDB06080B)), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
