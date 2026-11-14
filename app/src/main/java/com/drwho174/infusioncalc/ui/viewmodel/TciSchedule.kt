package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.PumpPlan
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciState
import java.math.BigDecimal

/**
 * Пересчёт кривой по фактическому расписанию подтверждений.
 *
 * Важно: план может начинаться не с нуля (после смены цели/возврата из ручного
 * режима), поэтому симуляция стартует с [startSeconds] и [initialState],
 * а не с нулевого состояния — иначе текущая концентрация «обнулялась».
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
internal object TciSchedule {
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
    fun simulate(
        form: TciForm,
        plan: PumpPlan,
        confirmedAt: Map<Int, Int>,
        startSeconds: Int,
        initialState: TciState,
        horizonSeconds: Int,
    ): List<SimStep>? {
        val params = parametersOf(form) ?: return null
        val changes = ArrayList<Pair<Int, BigDecimal>>()
        var last = startSeconds
        for (step in plan.steps) {
            if (step.offsetSeconds < startSeconds) continue
            val at = (confirmedAt[step.offsetSeconds] ?: step.offsetSeconds).coerceAtLeast(last)
            if (at >= horizonSeconds) break
            last = at
            if (changes.isNotEmpty() && changes.last().first == at) {
                changes[changes.lastIndex] = at to step.rateMlPerHour
            } else {
                changes.add(at to step.rateMlPerHour)
            }
        }
        if (changes.isEmpty()) return null

        var state = initialState
        var t = startSeconds
        var index = 0
        val curve = ArrayList<SimStep>()
        while (t < horizonSeconds) {
            while (index + 1 < changes.size && changes[index + 1].first <= t) index++
            val rate = changes[index].second
            val end = minOf(changes.getOrNull(index + 1)?.first ?: horizonSeconds, horizonSeconds)
            if (end <= t) {
                index++
                continue
            }
            val segment =
                TciEngine.simulateConstantRate(
                    params = params,
                    fromState = state,
                    fromOffsetSeconds = t,
                    rateMlPerHour = rate,
                    concentrationMgPerMl = form.concentrationMgPerMl,
                    horizonSeconds = end - t,
                )
            for (step in segment) {
                if (curve.isEmpty() || step.timeSeconds > curve.last().timeSeconds) curve.add(step)
            }
            state = segment.last().state
            t = end
            index++
        }
        return curve
    }
}
