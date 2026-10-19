package com.siteledger.domain

import com.siteledger.data.AccountDirection
import com.siteledger.data.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerCalculationTest {
    @Test fun `present earns full daily wage`() {
        assertEquals(250_000L, WageCalculator.earnedMinor(AttendanceStatus.PRESENT, 250_000L))
    }

    @Test fun `half day earns half daily wage`() {
        assertEquals(125_000L, WageCalculator.earnedMinor(AttendanceStatus.HALF_DAY, 250_000L))
    }

    @Test fun `half day can use a separately configured wage`() {
        assertEquals(90_000L, WageCalculator.earnedMinor(AttendanceStatus.HALF_DAY, 250_000L, 90_000L))
    }

    @Test fun `absent earns nothing`() {
        assertEquals(0L, WageCalculator.earnedMinor(AttendanceStatus.ABSENT, 250_000L))
    }

    @Test fun `worker balance subtracts advances and wage payments from earned wage`() {
        assertEquals(350_000L, LedgerBalances.workerBalance(listOf(250_000L, 250_000L), listOf(100_000L, 50_000L)))
        assertEquals(-50_000L, LedgerBalances.workerBalance(listOf(100_000L), listOf(150_000L)))
    }

    @Test fun `counterparty balance identifies who owes whom`() {
        assertEquals(75_000L, LedgerBalances.counterpartyBalance(listOf(AccountDirection.THEY_OWE_US to 100_000L, AccountDirection.WE_OWE_THEM to 25_000L)))
        assertEquals(-25_000L, LedgerBalances.counterpartyBalance(listOf(AccountDirection.THEY_OWE_US to 25_000L, AccountDirection.WE_OWE_THEM to 50_000L)))
    }
}
