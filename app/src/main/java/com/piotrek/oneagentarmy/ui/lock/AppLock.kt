package com.piotrek.oneagentarmy.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.piotrek.oneagentarmy.R

// Accepts either a biometric (fingerprint/face) or the device's own lock screen credential
// (PIN/pattern/password) - broader compatibility than requiring a fingerprint sensor.
const val APP_LOCK_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(APP_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    // setNegativeButtonText() can't be combined with DEVICE_CREDENTIAL in setAllowedAuthenticators -
    // the system back button / gesture is the only way to dismiss the prompt.
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.app_lock_prompt_title))
        .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
        .build()
    prompt.authenticate(promptInfo)
}
