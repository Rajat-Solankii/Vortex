package com.rajatsolankii.vortex.features.trakt

import com.rajatsolankii.vortex.core.time.parseZonedIsoDateTimeToEpochMs

internal fun parseTraktIsoDateTimeToEpochMs(value: String): Long? =
    parseZonedIsoDateTimeToEpochMs(value)
