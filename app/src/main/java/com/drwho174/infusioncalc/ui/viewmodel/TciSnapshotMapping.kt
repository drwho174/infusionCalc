package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.data.session.TciSessionSnapshot
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.tci.TciState
import java.math.BigDecimal

/** Преобразования формы и сессии в снимок DataStore и обратно (сценарий B). */
internal fun TciForm.toSnapshot(
    session: TciSession,
    planStartSeconds: Int = 0,
    planStartState: TciState? = null,
): TciSessionSnapshot =
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
        status = session.status.name,
        accumulatedSeconds = session.accumulatedSeconds,
        runningSinceEpochMs = session.runningSinceEpochMs,
        manualFromSeconds = session.manualFromSeconds,
        manualRateMlPerHour = session.manualRateMlPerHour?.toPlainString(),
        confirmedOffsets = session.confirmedOffsets.sorted(),
        planStartSeconds = planStartSeconds,
        planStartA1 = planStartState?.a1Mg?.toPlainString(),
        planStartA2 = planStartState?.a2Mg?.toPlainString(),
        planStartA3 = planStartState?.a3Mg?.toPlainString(),
        planStartCe = planStartState?.ceMcgPerMl?.toPlainString(),
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

/** Состояние, от которого пересчитывается действующий план; null для плана с нуля. */
@Suppress("ReturnCount")
internal fun TciSessionSnapshot.planStartState(): TciState? {
    if (planStartSeconds <= 0) return null
    val a1 = planStartA1?.toBigDecimalOrNull() ?: return null
    val a2 = planStartA2?.toBigDecimalOrNull() ?: return null
    val a3 = planStartA3?.toBigDecimalOrNull() ?: return null
    val ce = planStartCe?.toBigDecimalOrNull() ?: return null
    return TciState(a1Mg = a1, a2Mg = a2, a3Mg = a3, ceMcgPerMl = ce)
}

private inline fun <reified T : Enum<T>> enumOr(
    value: String,
    fallback: T,
): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
