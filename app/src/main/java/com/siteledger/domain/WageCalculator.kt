package com.siteledger.domain

import com.siteledger.data.AccountDirection
import com.siteledger.data.AttendanceStatus

object WageCalculator {
    fun earnedMinor(status: AttendanceStatus, dailyRateMinor: Long, halfDayRateMinor: Long = dailyRateMinor / 2): Long {
        require(dailyRateMinor >= 0) { "Daily wage cannot be negative." }
        require(halfDayRateMinor >= 0) { "Half-day wage cannot be negative." }
        return when (status) {
            AttendanceStatus.PRESENT -> dailyRateMinor
            AttendanceStatus.HALF_DAY -> halfDayRateMinor
            AttendanceStatus.ABSENT -> 0L
        }
    }
}

object LedgerBalances {
    /** A positive worker balance is wages owed; a negative balance is an advance paid ahead. */
    fun workerBalance(earnedMinor: Iterable<Long>, paymentsMinor: Iterable<Long>): Long =
        earnedMinor.sum() - paymentsMinor.sum()

    /** Positive means they owe us; negative means we owe them. */
    fun counterpartyBalance(entries: Iterable<Pair<AccountDirection, Long>>): Long =
        entries.sumOf { (direction, amount) ->
            require(amount >= 0) { "Ledger amounts cannot be negative." }
            if (direction == AccountDirection.THEY_OWE_US) amount else -amount
        }
}
