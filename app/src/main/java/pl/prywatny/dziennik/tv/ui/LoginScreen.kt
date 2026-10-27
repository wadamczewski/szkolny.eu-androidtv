package pl.prywatny.dziennik.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import pl.prywatny.dziennik.tv.R
import pl.prywatny.dziennik.tv.librus.DemoSource

/**
 * Login with a Synergia login and password (the method that works since Librus disabled the
 * password grant; see the iOS app). Konto Librus (e-mail) needs a web page with reCAPTCHA,
 * which isn't usable with a TV remote.
 */
@Composable
fun LoginScreen(vm: AppViewModel) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    fun run(l: String, p: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            error = vm.login(l, p)
            busy = false
        }
    }

    LaunchedEffect(Unit) { first.tryFocus() }
    // Adding another student: Back cancels instead of leaving the app.
    BackHandler(enabled = vm.account != null) { vm.addingAccount = false }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(500.dp).background(C.surface, RoundedCornerShape(16.dp)).padding(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.foundation.Image(
                painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            )
            Text("Dziennik", style = T.h1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Nieoficjalny klient Librus Synergia na telewizor", style = T.body.copy(color = C.muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Field("Login Synergii", login, { login = it }, placeholder = "np. 1234567u", focusRequester = first,
                imeAction = ImeAction.Next, onIme = { focus.moveFocus(FocusDirection.Down) })
            Field("Hasło", password, { password = it }, placeholder = "hasło do Synergii", secret = true,
                imeAction = ImeAction.Done, onIme = { run(login, password) })
            Text(error ?: "", style = T.body.copy(color = C.bad))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (busy) "Logowanie…" else "Zaloguj", onClick = { run(login, password) }, primary = true, modifier = Modifier.weight(1f))
                PillButton("Wypróbuj demo", onClick = { run(DemoSource.LOGIN, "demo") }, modifier = Modifier.weight(1f))
                if (vm.account != null) PillButton("Anuluj", onClick = { vm.addingAccount = false })
            }
            Text(
                "Użyj loginu i hasła do synergia.librus.pl. Logowanie Kontem Librus (e-mail) nie działa na telewizorze. " +
                    "Hasło jest zaszyfrowane kluczem tego urządzenia, bo bez niego nie da się odnowić sesji Synergii.",
                style = T.small,
            )
        }
    }
}

/**
 * TV text field: shows the value as a focusable tile; OK starts editing (and only then the
 * on-screen keyboard appears), the IME action or moving away ends it.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    secret: Boolean = false,
    imeAction: ImeAction,
    onIme: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val editRequester = remember { FocusRequester() }
    val tileRequester = focusRequester ?: remember { FocusRequester() }
    val shown = if (secret) "•".repeat(value.length) else value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = T.small)
        if (!editing) {
            FocusCard(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth(),
                container = C.bg,
                focusRequester = tileRequester,
                padding = 0,
            ) {
                Text(
                    shown.ifEmpty { placeholder },
                    style = T.body.copy(fontSize = 16.sp, color = if (value.isEmpty()) C.muted else C.text),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        } else {
            LaunchedEffect(Unit) { editRequester.tryFocus() }
            val finish = {
                editing = false
                hadFocus = false
                onIme()
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = T.body.copy(fontSize = 16.sp),
                cursorBrush = SolidColor(C.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
                    imeAction = imeAction,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onNext = { finish() }, onDone = { finish() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editRequester)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) {
                            hadFocus = false
                            editing = false
                        }
                    }
                    .background(C.bg, RoundedCornerShape(8.dp))
                    .border(2.dp, C.accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
