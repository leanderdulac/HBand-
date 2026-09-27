package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** null means no query response yet; an emitted empty list is a confirmed empty result.
 * Used by card/CSV preparation and history presentation; automatic-insight consumers are unchanged.
 */
internal fun Flow<List<HBandSensorMetricEntity>>.shareMetricsState(
    scope: CoroutineScope,
): StateFlow<List<HBandSensorMetricEntity>?> =
    map<List<HBandSensorMetricEntity>, List<HBandSensorMetricEntity>?> { it }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), null)
