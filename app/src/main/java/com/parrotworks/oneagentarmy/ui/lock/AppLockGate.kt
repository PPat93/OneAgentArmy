package com.parrotworks.oneagentarmy.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository

// Gates `content` behind a biometric/device-credential prompt when app lock is enabled in
// Settings. Locks again whenever the app is backgrounded (ON_STOP), not just on cold start.
//
// Re-locking *covers* the content instead of removing it from composition, and that
// distinction is load-bearing. "Backgrounded" includes launching the camera or a file
// picker - so the old behaviour disposed the very screen that was waiting for that
// activity's result. Disposing it unregisters its rememberLauncherForActivityResult, and
// ActivityResultRegistry.unregister() deletes the pending result outright rather than
// holding it for a later re-register. The result was therefore never delivered: a photo you
// had just shot and confirmed was dropped on the way back, with the file left orphaned in
// the cache directory until the next capture wiped it.
//
// Keeping the content composed keeps that launcher registered, so the result still lands
// while the lock screen is up, and the attachment is simply there once the user unlocks.
//
// Content is still withheld entirely until the first successful unlock, so a cold start
// runs nothing behind the lock screen - only an already-unlocked session gets covered.
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
    var contentEverShown by remember { mutableStateOf(false) }

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

    val showContent = appLockEnabled == false || unlocked || contentEverShown
    val showLock = appLockEnabled == true && !unlocked
    LaunchedEffect(showContent) { if (showContent) contentEverShown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showContent) content()
        if (showLock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // AppLockScreen paints an opaque Surface over everything, but an opaque
                    // composable is not by itself a touch barrier - without a pointer input
                    // of its own, taps would hit-test straight through to the covered
                    // content. This swallows whatever the lock screen doesn't handle.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            ) {
                // Same reasoning for the back gesture: the covered NavHost still has its own
                // back handling registered, and letting it pop from behind the lock screen
                // would drop the user somewhere else entirely once they unlock. Registered
                // after the NavHost's, so it takes priority.
                BackHandler(enabled = true) {}
                AppLockScreen(onUnlockClick = { showBiometricPrompt(activity) { unlocked = true } })
            }
        }
    }
}
