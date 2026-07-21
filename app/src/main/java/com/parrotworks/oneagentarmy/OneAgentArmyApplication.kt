package com.parrotworks.oneagentarmy

import android.app.Application

class OneAgentArmyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
