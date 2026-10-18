package com.siteledger.domain

import com.siteledger.data.AttendanceStatus

/** Pure calculation rule used by the payroll module when it is introduced. */
data class WagePolicy(
    val dailyRateMinor: Long,
    val halfDayNumerator: Int = 1,
    val halfDayDenominator: Int = 2,
    val overtimeRatePerHourMinor: Long = 0,
    val leavePaid: Boolean = false
)

object WageCalculator {
    fun earnedMinor(status: AttendanceStatus, overtimeMinutes: Int, policy: WagePolicy): Long {
        require(policy.dailyRateMinor >= 0 && policy.overtimeRatePerHourMinor >= 0)
        require(policy.halfDayNumerator >= 0 && policy.halfDayDenominator > 0 && overtimeMinutes >= 0)
        val base = when (status) {
            AttendanceStatus.PRESENT -> policy.dailyRateMinor
            AttendanceStatus.HALF_DAY -> policy.dailyRateMinor * policy.halfDayNumerator / policy.halfDayDenominator
            AttendanceStatus.LEAVE -> if (policy.leavePaid) policy.dailyRateMinor else 0
            AttendanceStatus.ABSENT -> 0
        }
        // Round to the nearest minor currency unit; never use floating point for pay.
        val overtime = (policy.overtimeRatePerHourMinor * overtimeMinutes + 30) / 60
        return base + overtime
    }
}
