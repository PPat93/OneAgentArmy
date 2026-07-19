package com.piotrek.oneagentarmy.ui.components

import java.util.Locale

// Costs are stored in USD (provider pricing currency) and converted to EUR for
// display with the cached daily ECB rate. "~€0.0023"; positive values below a
// hundredth of a cent render as "<€0.0001" instead of a misleading "~€0.0000".
fun formatCostEur(costUsd: Double, usdToEur: Double): String {
    val eur = costUsd * usdToEur
    return if (eur > 0.0 && eur < 0.0001) {
        "<€0.0001"
    } else {
        "~€" + String.format(Locale.US, "%.4f", eur)
    }
}
