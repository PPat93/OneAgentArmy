package com.parrotworks.oneagentarmy.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

// A rapid double-tap on a back/nav button can fire navigate()/popBackStack() twice before
// the first transition finishes - the second call then acts on a back stack that's already
// mid-change, either pushing the same destination twice or popping one screen further than
// intended. The originating screen's own back-stack entry stops being RESUMED the instant the
// first navigation starts, so gating on that state turns the second tap into a no-op instead.
fun NavController.navigateSafely(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}

fun NavController.popBackStackSafely() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
