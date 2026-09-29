package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Loading/failure are distinct from a confirmed empty result.
 * Used by card/CSV preparation and history presentation; automatic-insight consumers are unchanged.
 */
internal fun Flow<List<HBandSensorMetricEntity>>.shareMetricsState(
    scope: CoroutineScope,
    retries: Flow<Int> = flowOf(0),
): StateFlow<LocalReadState<List<HBandSensorMetricEntity>>> = localReadState(scope, retries)
