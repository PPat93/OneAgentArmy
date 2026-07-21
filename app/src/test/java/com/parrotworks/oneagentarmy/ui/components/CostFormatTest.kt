package com.parrotworks.oneagentarmy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CostFormatTest {

    @Test
    fun `zero cost formats as zero, not the near-zero fallback`() {
        assertEquals("~€0.0000", formatCostEur(costUsd = 0.0, usdToEur = 0.9))
    }

    @Test
    fun `cost below one hundredth of a cent uses the near-zero fallback`() {
        assertEquals("<€0.0001", formatCostEur(costUsd = 0.00001, usdToEur = 0.9))
    }

    @Test
    fun `cost exactly at the threshold formats normally, not as the fallback`() {
        // eur = 0.0001 exactly - the check is a strict "< 0.0001", so this must NOT
        // take the "<€0.0001" branch.
        assertEquals("~€0.0001", formatCostEur(costUsd = 0.0001, usdToEur = 1.0))
    }

    @Test
    fun `normal cost converts and formats to 4 decimals`() {
        assertEquals("~€0.8600", formatCostEur(costUsd = 1.0, usdToEur = 0.86))
    }
}
