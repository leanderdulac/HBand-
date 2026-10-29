package com.example.data.hband

/**
 * Official Veepoo/HBand SDK argument tuples for history reads.
 *
 * Ported from HBand PR #5, e9a80ef386d207a1bc6fe66bef3969eafa84aae5.
 * SDK reference: HBandSDK/Android_Ble_SDK `OperaterActivity`,
 * add57a049a916c210c5463a66b097ad94265e481.
 *
 * - `ReadOriginSetting(day, position, onlyReadOneDay, watchday)`
 *   - day: 0 = today, 1 = yesterday, …
 *   - position: start record, **must be ≥ 1**
 *   - onlyReadOneDay: false = keep walking older days
 *   - watchday: firmware storage capacity (`wathcDay` from the capability probe)
 * - Official demo: `new ReadOriginSetting(0, 1, false, watchDataDay)`
 * - `readOriginDataFromDay(..., day, position, watchday)`
 * - `readSleepDataFromDay(..., day, watchday)` with day=0 (today) and watchday=capacity
 *
 * The previous calls put history capacity in the day argument and started at position 0.
 * Correct argument order does not prove that a particular watch has retained samples.
 */
object VeepooHistoryReadSettings {
    const val DAY_TODAY = 0
    const val POSITION_FIRST = 1
    const val ONLY_ONE_DAY = false

    data class OriginArgs(
        val day: Int,
        val position: Int,
        val onlyReadOneDay: Boolean,
        val watchday: Int,
    )

    data class SleepArgs(
        val day: Int,
        val onlyReadOneDay: Boolean,
        val watchday: Int,
    )

    fun originArgs(historyDays: Int): OriginArgs {
        val watchday = historyDays.coerceIn(1, 14)
        return OriginArgs(
            day = DAY_TODAY,
            position = POSITION_FIRST,
            onlyReadOneDay = ONLY_ONE_DAY,
            watchday = watchday,
        )
    }

    fun sleepArgs(historyDays: Int): SleepArgs {
        val watchday = historyDays.coerceIn(1, 14)
        return SleepArgs(
            day = DAY_TODAY,
            onlyReadOneDay = ONLY_ONE_DAY,
            watchday = watchday,
        )
    }
}
