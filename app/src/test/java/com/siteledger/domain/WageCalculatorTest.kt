package com.siteledger.domain

import com.siteledger.data.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WageCalculatorTest {
    private val policy = WagePolicy(dailyRateMinor = 250_000, overtimeRatePerHourMinor = 40_000)

    @Test fun `present worker includes rounded overtime`() {
        assertEquals(310_000, WageCalculator.earnedMinor(AttendanceStatus.PRESENT, 90, policy))
    }

    @Test fun `half day is configurable and absent earns no base pay`() {
        assertEquals(125_000, WageCalculator.earnedMinor(AttendanceStatus.HALF_DAY, 0, policy))
        assertEquals(0, WageCalculator.earnedMinor(AttendanceStatus.ABSENT, 0, policy))
    }
}
