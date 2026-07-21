package com.parrotworks.oneagentarmy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.parrotworks.oneagentarmy.ui.lock.AppLockGate
import com.parrotworks.oneagentarmy.ui.navigation.OneAgentArmyNavHost
import com.parrotworks.oneagentarmy.ui.theme.OneAgentArmyTheme

// FragmentActivity (not plain ComponentActivity) - androidx.biometric's stable BiometricPrompt
// API requires it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OneAgentArmyApplication).container
        setContent {
            OneAgentArmyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppLockGate(settingsRepository = container.settingsRepository) {
                        OneAgentArmyNavHost(
                            conversationRepository = container.conversationRepository,
                            settingsRepository = container.settingsRepository,
                            factRepository = container.factRepository,
                            aiProvider = container.aiProvider,
                            contextWindowStrategy = container.contextWindowStrategy,
                            exchangeRateRepository = container.exchangeRateRepository,
                            attachmentStore = container.attachmentStore,
                        )
                    }
                }
            }
        }
    }
}
