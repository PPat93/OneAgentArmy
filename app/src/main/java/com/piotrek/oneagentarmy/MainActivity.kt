package com.piotrek.oneagentarmy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.piotrek.oneagentarmy.ui.navigation.OneAgentArmyNavHost
import com.piotrek.oneagentarmy.ui.theme.OneAgentArmyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OneAgentArmyApplication).container
        setContent {
            OneAgentArmyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OneAgentArmyNavHost(
                        conversationRepository = container.conversationRepository,
                        settingsRepository = container.settingsRepository,
                        factRepository = container.factRepository,
                        aiProvider = container.aiProvider,
                        contextWindowStrategy = container.contextWindowStrategy,
                    )
                }
            }
        }
    }
}
