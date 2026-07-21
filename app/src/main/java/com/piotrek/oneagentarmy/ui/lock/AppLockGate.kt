package com.piotrek.oneagentarmy.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.piotrek.oneagentarmy.data.repository.SettingsRepository

// Gates `content` behind a biometric/device-credential prompt when app lock is enabled in
// Settings. Locks again whenever the app is backgrounded (ON_STOP), not just on cold start.
@Composable
fun AppLockGate(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    // null while the DataStore flow hasn't emitted yet - avoids briefly flashing unlocked
    // content before we actually know whether the lock is on.
    val appLockEnabled by settingsRepository.observeAppLockEnabled().collectAsState(initial = null)
    var unlocked by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (appLockEnabled) {
        null -> Unit
        false -> content()
        true -> if (unlocked) {
            content()
        } else {
            AppLockScreen(onUnlockClick = { showBiometricPrompt(activity) { unlocked = true } })
        }
    }
}
