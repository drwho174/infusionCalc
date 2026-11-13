package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.tci.TciResult
import com.drwho174.infusioncalc.tci.TciSimRequest
import com.drwho174.infusioncalc.tci.TciSimulation
import com.drwho174.infusioncalc.ui.display
import java.math.BigDecimal

/** Редактируемые входные данные карточки TCI (только в памяти сессии, D-TCI-13). */
data class TciForm(
    val model: TciModel = TciModel.MARSH,
    val mode: TargetMode = TargetMode.PLASMA,
    val concentrationMgPerMl: BigDecimal = BigDecimal("10"),
    val bolusRateText: String = "1200",
    val weightText: String = "",
    val heightText: String = "",
    val ageText: String = "",
    val sex: Sex = Sex.MALE,
    val targetText: String = "",
    val wakeThresholdText: String = "1.5",
    val nowSeconds: Int = DEFAULT_NOW_SECONDS,
    val inductionDone: Boolean = false,
    val maintenanceDone: Boolean = false,
    val emergenceDone: Boolean = false,
) {
    companion object {
        const val HORIZON_SECONDS = TciEngine.DEFAULT_HORIZON_SECONDS
        const val DEFAULT_NOW_SECONDS = 300
    }
}

/** Блокирующая ошибка ввода (расчёт недоступен, VAL-TCI). */
enum class TciBlocking {
    WEIGHT,
    HEIGHT,
    AGE,
    TARGET,
    BOLUS_RATE,
}

/** Некритичное предупреждение (расчёт доступен). */
enum class TciWarning {
    WEIGHT_UNUSUAL,
    HEIGHT_UNUSUAL,
    AGE_UNUSUAL,
    BMI_OUT_OF_RANGE,
    TARGET_OUT_OF_RANGE,
}

data class RateTableRow(
    val minute: Int,
    val rateDisplay: String,
)

/** Рассчитанные значения для отображения. */
data class TciComputed(
    val bolusMlDisplay: String,
    val bolusTimeDisplay: String,
    val bolusRateDisplay: String,
    val maintenanceStartDisplay: String,
    val maintenanceSteadyDisplay: String,
    val cpNowDisplay: String,
    val ceNowDisplay: String,
    val wakeTimeDisplay: String?,
    val bmiDisplay: String?,
    val lbmDisplay: String?,
    val simulation: TciSimulation,
    val rateTable: List<RateTableRow>,
)

data class TciUiState(
    val form: TciForm = TciForm(),
    val bolusRateMlPerHour: BigDecimal? = null,
    val blocking: TciBlocking? = null,
    val warnings: List<TciWarning> = emptyList(),
    val computed: TciComputed? = null,
) {
    val isReady: Boolean get() = computed != null && blocking == null
}

/** Чистый расчёт состояния TCI из формы (без Android/Compose). */
internal fun buildTciState(form: TciForm): TciUiState {
    val bolusRate = form.bolusRateText.toBigDecimalOrNull()
    if (bolusRate == null || bolusRate.signum() <= 0) {
        return TciUiState(form = form, blocking = TciBlocking.BOLUS_RATE)
    }

    val weight = form.weightText.toBigDecimalOrNull()
    if (weight == null || weight.signum() <= 0 || weight > BigDecimal("300")) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.WEIGHT)
    }

    val height = form.heightText.toBigDecimalOrNull()
    val age = form.ageText.trim().toIntOrNull()
    if (form.model == TciModel.SCHNIDER) {
        if (height == null || height.signum() <= 0 || height > BigDecimal("250")) {
            return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.HEIGHT)
        }
        if (age == null || age < 18 || age > 110) {
            return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.AGE)
        }
    }

    val target = form.targetText.toBigDecimalOrNull()
    if (target == null || target.signum() <= 0 || target > BigDecimal("20")) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.TARGET)
    }

    val threshold = form.wakeThresholdText.toBigDecimalOrNull() ?: BigDecimal("1.5")
    val warnings = computeWarnings(form, weight, height, age, target)

    val request =
        TciSimRequest(
            model = form.model,
            targetMcgPerMl = target,
            mode = form.mode,
            concentrationMgPerMl = form.concentrationMgPerMl,
            bolusRateMlPerHour = bolusRate,
            weightKg = weight,
            heightCm = height,
            ageYears = age,
            sex = form.sex,
            horizonSeconds = TciForm.HORIZON_SECONDS,
        )
    val result = TciEngine.simulate(request)
    if (result is TciResult.Failure) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = blockingFrom(result))
    }
    val simulation = (result as TciResult.Success).value

    val nowStep = simulation.steps.lastOrNull { it.timeSeconds <= form.nowSeconds } ?: simulation.steps.first()
    val params =
        (TciEngine.parameters(form.model, weight, height, age, form.sex) as TciResult.Success).value
    val wakeSeconds =
        TciEngine.decrementTimeSeconds(
            params = params,
            state = nowStep.state,
            mode = form.mode,
            wakeThresholdMcgPerMl = threshold,
        )

    val bmi = if (form.model == TciModel.SCHNIDER) TciEngine.bmi(weight, height!!) else null
    val lbm = if (form.model == TciModel.SCHNIDER) TciEngine.lbmJames(weight, height!!, form.sex) else null

    return TciUiState(
        form = form,
        bolusRateMlPerHour = bolusRate,
        blocking = null,
        warnings = warnings,
        computed =
            TciComputed(
                bolusMlDisplay = TciEngine.formatVolumeMl(simulation.bolusMl).display(),
                bolusTimeDisplay = formatBolusTime(simulation.bolusTimeSeconds),
                bolusRateDisplay = TciEngine.formatRateMlPerHour(simulation.bolusRateMlPerHour).display(),
                maintenanceStartDisplay =
                    TciEngine.formatRateMlPerHour(simulation.maintenanceStartMlPerHour).display(),
                maintenanceSteadyDisplay =
                    TciEngine.formatRateMlPerHour(simulation.maintenanceSteadyMlPerHour).display(),
                cpNowDisplay = TciEngine.formatConcentrationMcgMl(nowStep.cpMcgPerMl).display(),
                ceNowDisplay = TciEngine.formatConcentrationMcgMl(nowStep.ceMcgPerMl).display(),
                wakeTimeDisplay = formatWakeTime(wakeSeconds),
                bmiDisplay = bmi?.let { TciEngine.formatConcentrationMcgMl(it).display() },
                lbmDisplay = lbm?.let { TciEngine.formatConcentrationMcgMl(it).display() },
                simulation = simulation,
                rateTable = buildRateTable(simulation),
            ),
    )
}

private fun blockingFrom(result: TciResult.Failure): TciBlocking =
    when (result.reason) {
        com.drwho174.infusioncalc.tci.TciError.WEIGHT_OUT_OF_RANGE -> TciBlocking.WEIGHT
        com.drwho174.infusioncalc.tci.TciError.HEIGHT_OUT_OF_RANGE -> TciBlocking.HEIGHT
        com.drwho174.infusioncalc.tci.TciError.AGE_OUT_OF_RANGE -> TciBlocking.AGE
        com.drwho174.infusioncalc.tci.TciError.TARGET_OUT_OF_RANGE -> TciBlocking.TARGET
        else -> TciBlocking.BOLUS_RATE
    }

private fun computeWarnings(
    form: TciForm,
    weight: BigDecimal,
    height: BigDecimal?,
    age: Int?,
    target: BigDecimal,
): List<TciWarning> {
    val warnings = mutableListOf<TciWarning>()

    if (form.model == TciModel.SCHNIDER) {
        val h = height!!
        val a = age!!
        if (TciEngine.isBmiOutsideSchniderRange(weight, h, form.sex)) {
            warnings.add(TciWarning.BMI_OUT_OF_RANGE)
        }
        if (h < BigDecimal("120") || h > BigDecimal("220")) {
            warnings.add(TciWarning.HEIGHT_UNUSUAL)
        }
        if (a < 18 || a > 85) {
            warnings.add(TciWarning.AGE_UNUSUAL)
        }
    }
    if (weight < BigDecimal("20") || weight > BigDecimal("200")) {
        warnings.add(TciWarning.WEIGHT_UNUSUAL)
    }

    val (targetLow, targetHigh) = targetPhaseRange(form)
    if (target < targetLow || target > targetHigh) {
        warnings.add(TciWarning.TARGET_OUT_OF_RANGE)
    }

    return warnings
}

/**
 * Рекомендуемый диапазон цели для активного этапа (VAL-TCI-05):
 * индукция Cp 4–8, поддержание Cp ~3–6; Ce 2.5–4 в обеих фазах.
 */
internal fun targetPhaseRange(form: TciForm): Pair<BigDecimal, BigDecimal> =
    if (form.mode == TargetMode.PLASMA) {
        if (!form.inductionDone) {
            BigDecimal("4") to BigDecimal("8")
        } else {
            BigDecimal("3") to BigDecimal("6")
        }
    } else {
        BigDecimal("2.5") to BigDecimal("4")
    }

private fun buildRateTable(simulation: TciSimulation): List<RateTableRow> =
    (300..TciForm.HORIZON_SECONDS step 300)
        .mapNotNull { minuteSeconds ->
            val step = simulation.steps.lastOrNull { it.timeSeconds <= minuteSeconds } ?: return@mapNotNull null
            RateTableRow(
                minute = minuteSeconds / 60,
                rateDisplay = TciEngine.formatRateMlPerHour(step.rateMlPerHour).display(),
            )
        }

/** Болюс: мм:сс (CALC-TCI-14). */
internal fun formatBolusTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/** Время до пробуждения: мм:сс, при ≥ 60 мин — чч:мм (CALC-TCI-14). */
internal fun formatWakeTime(seconds: Int?): String? {
    if (seconds == null) return null
    if (seconds >= 3600) {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return "%d:%02d".format(h, m)
    }
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
