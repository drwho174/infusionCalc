package com.drwho174.infusioncalc.tci

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Чистый Kotlin-движок пропофол-TCI (Marsh/Schnider, только взрослые).
 * Без Android/Compose. Вся арифметика — [BigDecimal] в [MathContext.DECIMAL128],
 * без примитивов с плавающей точкой и экспоненты/округления из java.lang.Math
 * (D-TCI-10, ⛔-TCI-7).
 *
 * Источники формул — docs/SPEC-TCI.md §5–6, docs/research-tci.md §7.
 */
object TciEngine {
    private val MC = MathContext.DECIMAL128
    private val ZERO = BigDecimal.ZERO
    private val SIXTY = BigDecimal(60)

    private val MARSH_KE0 = BigDecimal("0.26")
    private val SCHNIDER_KE0 = BigDecimal("0.456")

    // ------------------------------------------------------------------ параметры

    /** Параметры модели для пациента. Возвращает Failure при нарушении технических пределов. */
    fun parameters(
        model: TciModel,
        weightKg: BigDecimal,
        heightCm: BigDecimal?,
        ageYears: Int?,
        sex: Sex?,
    ): TciResult<TciParameters> {
        if (weightKg.signum() <= 0 || weightKg > BigDecimal("300")) {
            return TciResult.Failure(TciError.WEIGHT_OUT_OF_RANGE)
        }
        val params =
            when (model) {
                TciModel.MARSH -> marsh(weightKg)
                TciModel.SCHNIDER -> {
                    if (heightCm == null || heightCm.signum() <= 0 || heightCm > BigDecimal("250")) {
                        return TciResult.Failure(TciError.HEIGHT_OUT_OF_RANGE)
                    }
                    if (ageYears == null || ageYears < 18 || ageYears > 110) {
                        return TciResult.Failure(TciError.AGE_OUT_OF_RANGE)
                    }
                    if (sex == null) return TciResult.Failure(TciError.SEX_REQUIRED)
                    schnider(weightKg, heightCm, ageYears, sex)
                }
            }
        return TciResult.Success(withMicroConstants(params))
    }

    private fun marsh(weightKg: BigDecimal): TciParameters =
        TciParameters(
            v1 = weightKg.multiply(BigDecimal("0.228"), MC),
            v2 = weightKg.multiply(BigDecimal("0.4643"), MC),
            v3 = weightKg.multiply(BigDecimal("2.895"), MC),
            cl1 = weightKg.multiply(BigDecimal("0.027"), MC),
            cl2 = weightKg.multiply(BigDecimal("0.0255"), MC),
            cl3 = weightKg.multiply(BigDecimal("0.0095"), MC),
            ke0 = MARSH_KE0,
            k10 = ZERO,
            k12 = ZERO,
            k21 = ZERO,
            k13 = ZERO,
            k31 = ZERO,
        )

    private fun schnider(
        weightKg: BigDecimal,
        heightCm: BigDecimal,
        ageYears: Int,
        sex: Sex,
    ): TciParameters {
        val age = BigDecimal(ageYears)
        val lbm = lbmJames(weightKg, heightCm, sex)
        val cl1 =
            BigDecimal("1.89")
                .add(BigDecimal("0.0456").multiply(weightKg.subtract(BigDecimal("77"), MC), MC), MC)
                .subtract(BigDecimal("0.0681").multiply(lbm.subtract(BigDecimal("59"), MC), MC), MC)
                .add(BigDecimal("0.0264").multiply(heightCm.subtract(BigDecimal("177"), MC), MC), MC)
        return TciParameters(
            v1 = BigDecimal("4.27"),
            v2 = BigDecimal("18.9").subtract(BigDecimal("0.391").multiply(age.subtract(BigDecimal("53"), MC), MC), MC),
            v3 = BigDecimal("238"),
            cl1 = cl1,
            cl2 = BigDecimal("1.29").subtract(BigDecimal("0.024").multiply(age.subtract(BigDecimal("53"), MC), MC), MC),
            cl3 = BigDecimal("0.836"),
            ke0 = SCHNIDER_KE0,
            k10 = ZERO,
            k12 = ZERO,
            k21 = ZERO,
            k13 = ZERO,
            k31 = ZERO,
        )
    }

    private fun withMicroConstants(p: TciParameters): TciParameters =
        p.copy(
            k10 = p.cl1.divide(p.v1, MC),
            k12 = p.cl2.divide(p.v1, MC),
            k21 = p.cl2.divide(p.v2, MC),
            k13 = p.cl3.divide(p.v1, MC),
            k31 = p.cl3.divide(p.v3, MC),
        )

    // ------------------------------------------------------------------ ковариаты

    /** LBM по James (кг): муж. 1.10·W − 128·(W/H)²; жен. 1.07·W − 148·(W/H)². */
    fun lbmJames(weightKg: BigDecimal, heightCm: BigDecimal, sex: Sex): BigDecimal {
        val ratio = weightKg.divide(heightCm, MC)
        val ratioSquared = ratio.multiply(ratio, MC)
        return if (sex == Sex.MALE) {
            BigDecimal("1.10").multiply(weightKg, MC)
                .subtract(BigDecimal("128").multiply(ratioSquared, MC), MC)
        } else {
            BigDecimal("1.07").multiply(weightKg, MC)
                .subtract(BigDecimal("148").multiply(ratioSquared, MC), MC)
        }
    }

    /** BMI (кг/м²) = W / (H/100)². */
    fun bmi(weightKg: BigDecimal, heightCm: BigDecimal): BigDecimal {
        val heightM = heightCm.divide(BigDecimal(100), MC)
        return weightKg.divide(heightM.multiply(heightM, MC), MC)
    }

    /** Schnider вне области применения: муж. BMI ≥ 42, жен. BMI ≥ 35 (VAL-TCI-04). */
    fun isBmiOutsideSchniderRange(weightKg: BigDecimal, heightCm: BigDecimal, sex: Sex): Boolean {
        val value = bmi(weightKg, heightCm)
        val limit = if (sex == Sex.MALE) BigDecimal("42") else BigDecimal("35")
        return value >= limit
    }

    // ------------------------------------------------------------------ болюс и скорости

    /** Болюс (нагрузка), мг: D = C·V1 (CALC-TCI-04). */
    fun bolusMg(targetMcgPerMl: BigDecimal, params: TciParameters): BigDecimal =
        targetMcgPerMl.multiply(params.v1, MC)

    /** Начальная скорость, мг/мин: R0 = C·(CL1+CL2+CL3) (CALC-TCI-05). */
    fun maintenanceStartMgPerMin(targetMcgPerMl: BigDecimal, params: TciParameters): BigDecimal =
        targetMcgPerMl.multiply(params.cl1.add(params.cl2, MC).add(params.cl3, MC), MC)

    /** Установившаяся скорость, мг/мин: R∞ = C·CL1 (CALC-TCI-05). */
    fun maintenanceSteadyMgPerMin(targetMcgPerMl: BigDecimal, params: TciParameters): BigDecimal =
        targetMcgPerMl.multiply(params.cl1, MC)

    /** Объёмная скорость: R[мл/ч] = R[мг/мин]·60 / conc (CALC-TCI-06). */
    fun mgPerMinToMlPerHour(mgPerMin: BigDecimal, concentrationMgPerMl: BigDecimal): BigDecimal =
        mgPerMin.multiply(SIXTY, MC).divide(concentrationMgPerMl, MC)

    /** Объём болюса, мл (CALC-TCI-06). */
    fun bolusMl(bolusMg: BigDecimal, concentrationMgPerMl: BigDecimal): BigDecimal =
        bolusMg.divide(concentrationMgPerMl, MC)

    /** Время болюса в секундах при заданном максимуме насоса (CALC-TCI-06). */
    fun bolusTimeSeconds(bolusMl: BigDecimal, bolusRateMlPerHour: BigDecimal): Int =
        bolusMl
            .multiply(BigDecimal(3600), MC)
            .divide(bolusRateMlPerHour, MC)
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()

    // ------------------------------------------------------------------ аналитические референсы Ce

    /** Ce после ступенчатого выхода Cp на цель: Ce(t) = C·(1 − e^(−ke0·t)) (CALC-TCI-07). */
    fun ceAfterStep(targetMcgPerMl: BigDecimal, ke0: BigDecimal, tMinutes: BigDecimal): BigDecimal {
        val exponent = ke0.negate().multiply(tMinutes, MC)
        val decay = TciMath.exp(exponent)
        return targetMcgPerMl.multiply(BigDecimal.ONE.subtract(decay, MC), MC)
    }

    /** Время достижения доли [fraction] цели: t(f) = −ln(1−f)/ke0 (CALC-TCI-07), мин. */
    fun timeToCeFraction(fraction: BigDecimal, ke0: BigDecimal): BigDecimal {
        val oneMinus = BigDecimal.ONE.subtract(fraction, MC)
        return TciMath.ln(oneMinus).negate().divide(ke0, MC)
    }

    // ------------------------------------------------------------------ симуляция

    /** Горизонт симуляции по умолчанию — 60 минут. */
    const val DEFAULT_HORIZON_SECONDS = 3600

    /** Максимальный горизонт поиска decrement time — 24 часа. */
    const val MAX_DECREMENT_SECONDS = 86400

    /**
     * Полный расчёт: болюс, скорости и временной ряд Cp/Ce до [horizonSeconds].
     * Шаг 1 с первые 15 минут, далее 10 с (CALC-TCI-08). Контроллер удерживает Cp на цели
     * (при Ce-режиме Cp тоже удерживается на цели — установившееся Ce = Cp = цель; MVP).
     */
    fun simulate(request: TciSimRequest): TciResult<TciSimulation> {
        val paramsResult =
            parameters(request.model, request.weightKg, request.heightCm, request.ageYears, request.sex)
        if (paramsResult is TciResult.Failure) return paramsResult
        val params = (paramsResult as TciResult.Success).value

        if (request.targetMcgPerMl.signum() <= 0 || request.targetMcgPerMl > BigDecimal("20")) {
            return TciResult.Failure(TciError.TARGET_OUT_OF_RANGE)
        }
        val conc = request.concentrationMgPerMl
        if (conc != BigDecimal("10") && conc != BigDecimal("20")) {
            return TciResult.Failure(TciError.INVALID_CONCENTRATION)
        }
        if (request.bolusRateMlPerHour.signum() <= 0) {
            return TciResult.Failure(TciError.INVALID_BOLUS_RATE)
        }

        val target = request.targetMcgPerMl
        val bolusMg = bolusMg(target, params)
        val bolusMl = bolusMl(bolusMg, conc)
        val bolusTimeSec = bolusTimeSeconds(bolusMl, request.bolusRateMlPerHour)
        val r0MlH = mgPerMinToMlPerHour(maintenanceStartMgPerMin(target, params), conc)
        val rInfMlH = mgPerMinToMlPerHour(maintenanceSteadyMgPerMin(target, params), conc)

        val bolusRateMgPerMin =
            bolusMg.divide(BigDecimal(bolusTimeSec).divide(SIXTY, MC), MC)
        val kSum = params.k10.add(params.k12, MC).add(params.k13, MC)

        var a1 = ZERO
        var a2 = ZERO
        var a3 = ZERO
        var ce = ZERO
        val steps = ArrayList<SimStep>((request.horizonSeconds / 5) + 2)

        fun rateAt(tSeconds: Int): BigDecimal =
            if (tSeconds < bolusTimeSec) {
                bolusRateMgPerMin
            } else {
                val clamp = kSum.multiply(target, MC).multiply(params.v1, MC)
                    .subtract(params.k21.multiply(a2, MC), MC)
                    .subtract(params.k31.multiply(a3, MC), MC)
                if (clamp.signum() < 0) ZERO else clamp
            }

        fun record(tSeconds: Int) {
            val cp = a1.divide(params.v1, MC)
            val rateMlH =
                if (tSeconds < bolusTimeSec) {
                    request.bolusRateMlPerHour
                } else {
                    mgPerMinToMlPerHour(rateAt(tSeconds), conc)
                }
            steps.add(
                SimStep(
                    timeSeconds = tSeconds,
                    cpMcgPerMl = cp,
                    ceMcgPerMl = ce,
                    rateMlPerHour = rateMlH,
                    state = TciState(a1, a2, a3, ce),
                ),
            )
        }

        record(0)
        var t = 0
        while (t < request.horizonSeconds) {
            val dtSeconds = if (t < 900) 1 else 10
            val nextT = minOf(t + dtSeconds, request.horizonSeconds)
            val dtMinutes = BigDecimal(nextT - t).divide(SIXTY, MC)
            val rate = rateAt(t)
            val cp = a1.divide(params.v1, MC)

            val a1New =
                a1.add(
                    dtMinutes.multiply(
                        params.k21.multiply(a2, MC)
                            .add(params.k31.multiply(a3, MC), MC)
                            .subtract(kSum.multiply(a1, MC), MC)
                            .add(rate, MC),
                        MC,
                    ),
                    MC,
                )
            val a2New = a2.add(dtMinutes.multiply(params.k12.multiply(a1, MC).subtract(params.k21.multiply(a2, MC), MC), MC), MC)
            val a3New = a3.add(dtMinutes.multiply(params.k13.multiply(a1, MC).subtract(params.k31.multiply(a3, MC), MC), MC), MC)
            val ceNew = ce.add(dtMinutes.multiply(params.ke0, MC).multiply(cp.subtract(ce, MC), MC), MC)

            a1 = a1New
            a2 = a2New
            a3 = a3New
            ce = ceNew
            t = nextT
            record(t)
        }

        return TciResult.Success(
            TciSimulation(
                bolusMg = bolusMg,
                bolusMl = bolusMl,
                bolusTimeSeconds = bolusTimeSec,
                bolusRateMlPerHour = request.bolusRateMlPerHour,
                maintenanceStartMlPerHour = r0MlH,
                maintenanceSteadyMlPerHour = rInfMlH,
                steps = steps,
            ),
        )
    }

    /** Временной ряд падения концентраций после остановки (R=0), шаг 1 с. */
    internal fun decaySteps(
        params: TciParameters,
        state: TciState,
        maxSeconds: Int,
    ): List<SimStep> {
        val kSum = params.k10.add(params.k12, MC).add(params.k13, MC)
        var a1 = state.a1Mg
        var a2 = state.a2Mg
        var a3 = state.a3Mg
        var ce = state.ceMcgPerMl
        val steps = ArrayList<SimStep>(maxSeconds + 1)
        steps.add(
            SimStep(
                timeSeconds = 0,
                cpMcgPerMl = a1.divide(params.v1, MC),
                ceMcgPerMl = ce,
                rateMlPerHour = ZERO,
                state = TciState(a1, a2, a3, ce),
            ),
        )
        var t = 0
        while (t < maxSeconds) {
            val dtMinutes = BigDecimal.ONE.divide(SIXTY, MC)
            val cp = a1.divide(params.v1, MC)
            val a1New =
                a1.add(
                    dtMinutes.multiply(
                        params.k21.multiply(a2, MC)
                            .add(params.k31.multiply(a3, MC), MC)
                            .subtract(kSum.multiply(a1, MC), MC),
                        MC,
                    ),
                    MC,
                )
            val a2New = a2.add(dtMinutes.multiply(params.k12.multiply(a1, MC).subtract(params.k21.multiply(a2, MC), MC), MC), MC)
            val a3New = a3.add(dtMinutes.multiply(params.k13.multiply(a1, MC).subtract(params.k31.multiply(a3, MC), MC), MC), MC)
            val ceNew = ce.add(dtMinutes.multiply(params.ke0, MC).multiply(cp.subtract(ce, MC), MC), MC)
            a1 = a1New
            a2 = a2New
            a3 = a3New
            ce = ceNew
            t++
            steps.add(
                SimStep(
                    timeSeconds = t,
                    cpMcgPerMl = a1.divide(params.v1, MC),
                    ceMcgPerMl = ce,
                    rateMlPerHour = ZERO,
                    state = TciState(a1, a2, a3, ce),
                ),
            )
        }
        return steps
    }

    /** Время до пробуждения (decrement time) от состояния [state]: первый момент пересечения порога. */
    fun decrementTimeSeconds(
        params: TciParameters,
        state: TciState,
        mode: TargetMode,
        wakeThresholdMcgPerMl: BigDecimal,
        maxSeconds: Int = MAX_DECREMENT_SECONDS,
        stepSeconds: Int = 1,
    ): Int? {
        require(stepSeconds > 0) { "stepSeconds must be positive" }
        val kSum = params.k10.add(params.k12, MC).add(params.k13, MC)
        var a1 = state.a1Mg
        var a2 = state.a2Mg
        var a3 = state.a3Mg
        var ce = state.ceMcgPerMl
        val dtMinutes = BigDecimal(stepSeconds).divide(SIXTY, MC)

        fun currentValue(): BigDecimal = if (mode == TargetMode.PLASMA) a1.divide(params.v1, MC) else ce

        if (currentValue() <= wakeThresholdMcgPerMl) return 0

        var t = 0
        while (t < maxSeconds) {
            val cp = a1.divide(params.v1, MC)
            val a1New =
                a1.add(
                    dtMinutes.multiply(
                        params.k21.multiply(a2, MC)
                            .add(params.k31.multiply(a3, MC), MC)
                            .subtract(kSum.multiply(a1, MC), MC),
                        MC,
                    ),
                    MC,
                )
            val a2New = a2.add(dtMinutes.multiply(params.k12.multiply(a1, MC).subtract(params.k21.multiply(a2, MC), MC), MC), MC)
            val a3New = a3.add(dtMinutes.multiply(params.k13.multiply(a1, MC).subtract(params.k31.multiply(a3, MC), MC), MC), MC)
            val ceNew = ce.add(dtMinutes.multiply(params.ke0, MC).multiply(cp.subtract(ce, MC), MC), MC)
            a1 = a1New
            a2 = a2New
            a3 = a3New
            ce = ceNew
            t += stepSeconds
            if (currentValue() <= wakeThresholdMcgPerMl) return t
        }
        return null
    }

    // ------------------------------------------------------------------ программа для обычного насоса

    /** Интервал ступени поддержания по умолчанию — 5 минут (D-LIVE-2). */
    const val PUMP_STEP_SECONDS = 300

    /**
     * Шаговая программа для шприцевого насоса без режима TCI (D-LIVE-2):
     * болюс, затем поддержание ступенями по [stepSeconds] секунд.
     *
     * Скорость ступени — средняя скорость непрерывного clamp-контроллера на её интервале,
     * поэтому Cp на границах ступеней близок к цели. [PumpPlan.predicted] — прогноз
     * именно под исполнением этих ступеней (а не под идеальным непрерывным контроллером).
     */
    fun pumpPlan(
        request: TciSimRequest,
        stepSeconds: Int = PUMP_STEP_SECONDS,
    ): TciResult<PumpPlan> {
        require(stepSeconds > 0) { "stepSeconds must be positive" }
        val closedLoop = simulate(request)
        if (closedLoop is TciResult.Failure) return closedLoop
        val sim = (closedLoop as TciResult.Success).value
        val params = parametersOf(request)

        val horizon = request.horizonSeconds
        val steps = ArrayList<PumpStep>()
        steps.add(PumpStep(offsetSeconds = 0, rateMlPerHour = request.bolusRateMlPerHour, bolus = true))
        var start = sim.bolusTimeSeconds.coerceAtMost(horizon)
        while (start < horizon) {
            val alignedEnd = minOf(((start / stepSeconds) + 1) * stepSeconds, horizon)
            val end = if (alignedEnd <= start) horizon else alignedEnd
            steps.add(PumpStep(offsetSeconds = start, rateMlPerHour = averageRateMlPerHour(sim.steps, start, end)))
            start = end
        }

        val predicted =
            runWithRates(
                params = params,
                initial = TciState(ZERO, ZERO, ZERO, ZERO),
                fromOffsetSeconds = 0,
                horizonSeconds = horizon,
                concentrationMgPerMl = request.concentrationMgPerMl,
                rateMlPerHourAt = { seconds -> rateForStep(steps, seconds) },
                boundaryAfter = { seconds -> steps.firstOrNull { it.offsetSeconds > seconds }?.offsetSeconds ?: Int.MAX_VALUE },
            )

        return TciResult.Success(
            PumpPlan(
                bolusMg = sim.bolusMg,
                bolusMl = sim.bolusMl,
                bolusTimeSeconds = sim.bolusTimeSeconds,
                bolusRateMlPerHour = sim.bolusRateMlPerHour,
                maintenanceStartMlPerHour = sim.maintenanceStartMlPerHour,
                maintenanceSteadyMlPerHour = sim.maintenanceSteadyMlPerHour,
                steps = steps,
                predicted = predicted,
            ),
        )
    }

    /**
     * Прогноз при постоянной скорости насоса от произвольного состояния
     * (ручной режим, D-LIVE-3). Время шагов — абсолютное: [fromOffsetSeconds] + t.
     */
    fun simulateConstantRate(
        params: TciParameters,
        fromState: TciState,
        fromOffsetSeconds: Int,
        rateMlPerHour: BigDecimal,
        concentrationMgPerMl: BigDecimal,
        horizonSeconds: Int,
    ): List<SimStep> =
        runWithRates(
            params = params,
            initial = fromState,
            fromOffsetSeconds = fromOffsetSeconds,
            horizonSeconds = horizonSeconds,
            concentrationMgPerMl = concentrationMgPerMl,
            rateMlPerHourAt = { rateMlPerHour },
            boundaryAfter = { Int.MAX_VALUE },
        )

    private fun parametersOf(request: TciSimRequest): TciParameters =
        (parameters(request.model, request.weightKg, request.heightCm, request.ageYears, request.sex) as TciResult.Success).value

    private fun rateForStep(
        steps: List<PumpStep>,
        seconds: Int,
    ): BigDecimal {
        var rate = steps.first().rateMlPerHour
        for (step in steps) {
            if (step.offsetSeconds > seconds) break
            rate = step.rateMlPerHour
        }
        return rate
    }

    /** Средняя скорость (мл/ч) на интервале [fromSeconds, toSeconds) по временному ряду. */
    private fun averageRateMlPerHour(
        steps: List<SimStep>,
        fromSeconds: Int,
        toSeconds: Int,
    ): BigDecimal {
        var weighted = ZERO
        var weight = ZERO
        for (index in 0 until steps.size - 1) {
            val segmentStart = maxOf(steps[index].timeSeconds, fromSeconds)
            val segmentEnd = minOf(steps[index + 1].timeSeconds, toSeconds)
            if (segmentEnd > segmentStart) {
                val w = BigDecimal(segmentEnd - segmentStart)
                weighted = weighted.add(w.multiply(steps[index].rateMlPerHour, MC), MC)
                weight = weight.add(w, MC)
            }
        }
        if (weight.signum() == 0) return steps.last().rateMlPerHour
        return weighted.divide(weight, MC)
    }

    private fun runWithRates(
        params: TciParameters,
        initial: TciState,
        fromOffsetSeconds: Int,
        horizonSeconds: Int,
        concentrationMgPerMl: BigDecimal,
        rateMlPerHourAt: (Int) -> BigDecimal,
        boundaryAfter: (Int) -> Int,
    ): List<SimStep> {
        var state = initial
        val result = ArrayList<SimStep>((horizonSeconds / 5) + 2)

        fun record(localSeconds: Int, current: TciState) {
            result.add(
                SimStep(
                    timeSeconds = fromOffsetSeconds + localSeconds,
                    cpMcgPerMl = current.a1Mg.divide(params.v1, MC),
                    ceMcgPerMl = current.ceMcgPerMl,
                    rateMlPerHour = rateMlPerHourAt(localSeconds),
                    state = current,
                ),
            )
        }

        record(0, state)
        var t = 0
        while (t < horizonSeconds) {
            val step = if (t < 900) 1 else 10
            val next = minOf(t + step, boundaryAfter(t), horizonSeconds)
            val dtMinutes = BigDecimal(next - t).divide(SIXTY, MC)
            val rateMgPerMin = rateMlPerHourAt(t).multiply(concentrationMgPerMl, MC).divide(SIXTY, MC)
            state = advanceState(params, state, dtMinutes, rateMgPerMin)
            t = next
            record(t, state)
        }
        return result
    }

    private fun advanceState(
        params: TciParameters,
        state: TciState,
        dtMinutes: BigDecimal,
        rateMgPerMin: BigDecimal,
    ): TciState {
        val kSum = params.k10.add(params.k12, MC).add(params.k13, MC)
        val cp = state.a1Mg.divide(params.v1, MC)
        val a1 =
            state.a1Mg.add(
                dtMinutes.multiply(
                    params.k21.multiply(state.a2Mg, MC)
                        .add(params.k31.multiply(state.a3Mg, MC), MC)
                        .subtract(kSum.multiply(state.a1Mg, MC), MC)
                        .add(rateMgPerMin, MC),
                    MC,
                ),
                MC,
            )
        val a2 =
            state.a2Mg.add(
                dtMinutes.multiply(
                    params.k12.multiply(state.a1Mg, MC).subtract(params.k21.multiply(state.a2Mg, MC), MC),
                    MC,
                ),
                MC,
            )
        val a3 =
            state.a3Mg.add(
                dtMinutes.multiply(
                    params.k13.multiply(state.a1Mg, MC).subtract(params.k31.multiply(state.a3Mg, MC), MC),
                    MC,
                ),
                MC,
            )
        val ce =
            state.ceMcgPerMl.add(
                dtMinutes.multiply(params.ke0, MC).multiply(cp.subtract(state.ceMcgPerMl, MC), MC),
                MC,
            )
        return TciState(a1Mg = a1, a2Mg = a2, a3Mg = a3, ceMcgPerMl = ce)
    }

    // ------------------------------------------------------------------ округление вывода

    /** Скорость мл/ч: 1 знак; при R < 1 — 2 знака (CALC-TCI-11). */
    fun formatRateMlPerHour(value: BigDecimal): BigDecimal {
        val scale = if (value.abs() < BigDecimal.ONE) 2 else 1
        return value.setScale(scale, RoundingMode.HALF_UP)
    }

    /** Объём болюса: 2 знака (CALC-TCI-12). */
    fun formatVolumeMl(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    /** Cp/Ce: 2 знака (CALC-TCI-13). */
    fun formatConcentrationMcgMl(value: BigDecimal): BigDecimal =
        value.setScale(2, RoundingMode.HALF_UP)
}

/** Входные данные полного расчёта TCI. */
data class TciSimRequest(
    val model: TciModel,
    val targetMcgPerMl: BigDecimal,
    val mode: TargetMode,
    val concentrationMgPerMl: BigDecimal,
    val bolusRateMlPerHour: BigDecimal,
    val weightKg: BigDecimal,
    val heightCm: BigDecimal? = null,
    val ageYears: Int? = null,
    val sex: Sex? = null,
    val horizonSeconds: Int = TciEngine.DEFAULT_HORIZON_SECONDS,
)
