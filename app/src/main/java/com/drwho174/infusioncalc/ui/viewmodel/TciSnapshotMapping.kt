package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.data.session.TciSessionSnapshot
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import java.math.BigDecimal

/** Преобразования формы и сессии в снимок DataStore и обратно (сценарий B). */
internal fun TciForm.toSnapshot(session: TciSession): TciSessionSnapshot =
    TciSessionSnapshot(
        model = model.name,
        mode = mode.name,
        concentrationMgPerMl = concentrationMgPerMl.toPlainString(),
        bolusRateText = bolusRateText,
        weightText = weightText,
        heightText = heightText,
        ageText = ageText,
        sex = sex.name,
        targetText = targetText,
        wakeThresholdText = wakeThresholdText,
        inductionDone = inductionDone,
        maintenanceDone = maintenanceDone,
        emergenceDone = emergenceDone,
        status = session.status.name,
        accumulatedSeconds = session.accumulatedSeconds,
        runningSinceEpochMs = session.runningSinceEpochMs,
        manualFromSeconds = session.manualFromSeconds,
        manualRateMlPerHour = session.manualRateMlPerHour?.toPlainString(),
        confirmedOffsets = session.confirmedOffsets.sorted(),
    )

internal fun TciSessionSnapshot.toForm(): TciForm =
    TciForm(
        model = enumOr(model, TciModel.MARSH),
        mode = enumOr(mode, TargetMode.PLASMA),
        concentrationMgPerMl = concentrationMgPerMl.toBigDecimalOrNull() ?: BigDecimal("10"),
        bolusRateText = bolusRateText,
        weightText = weightText,
        heightText = heightText,
        ageText = ageText,
        sex = enumOr(sex, Sex.MALE),
        targetText = targetText,
        wakeThresholdText = wakeThresholdText,
        inductionDone = inductionDone,
        maintenanceDone = maintenanceDone,
        emergenceDone = emergenceDone,
    )

internal fun TciSessionSnapshot.toSession(): TciSession =
    TciSession(
        status = enumOr(status, TciSessionStatus.IDLE),
        accumulatedSeconds = accumulatedSeconds,
        runningSinceEpochMs = runningSinceEpochMs,
        manualFromSeconds = manualFromSeconds,
        manualRateMlPerHour = manualRateMlPerHour?.toBigDecimalOrNull(),
        confirmedOffsets = confirmedOffsets.toSet(),
    )

private inline fun <reified T : Enum<T>> enumOr(
    value: String,
    fallback: T,
): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
