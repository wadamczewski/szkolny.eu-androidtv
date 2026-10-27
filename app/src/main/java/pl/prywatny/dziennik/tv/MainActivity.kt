package pl.prywatny.dziennik.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import pl.prywatny.dziennik.tv.ui.AppViewModel
import pl.prywatny.dziennik.tv.ui.DziennikApp
import pl.prywatny.dziennik.tv.ui.DziennikTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DziennikTheme { DziennikApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the TV app after a while: refresh stale data.
        if (vm.account != null && vm.isStale()) vm.refresh(manual = false)
    }
}
