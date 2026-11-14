package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.PumpPlan
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciError
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.tci.TciResult
import com.drwho174.infusioncalc.tci.TciSimRequest
import com.drwho174.infusioncalc.ui.display
import java.math.BigDecimal

private const val MAX_WEIGHT_KG = 300
private const val MAX_HEIGHT_CM = 250
private const val MIN_AGE = 18
private const val MAX_AGE = 110
private const val MAX_TARGET_MCG_ML = 20
private const val UNUSUAL_HEIGHT_MIN_CM = 120
private const val UNUSUAL_HEIGHT_MAX_CM = 220
private const val UNUSUAL_AGE_MAX = 85
private const val UNUSUAL_WEIGHT_MIN_KG = 20
private const val UNUSUAL_WEIGHT_MAX_KG = 200
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

internal const val MIN_STEP = 1
internal const val MAX_STEP = 15
internal const val DEFAULT_TABLE_STEP_MINUTES = 5
internal const val DEFAULT_WARNING_LEAD_MINUTES = 5

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
    /** Шаг таблицы скоростей и ступеней плана, минуты (1–15). */
    val tableStepMinutes: Int = DEFAULT_TABLE_STEP_MINUTES,
    /** За сколько минут до следующей коррекции показывать предупреждение (1–15). */
    val warningLeadMinutes: Int = DEFAULT_WARNING_LEAD_MINUTES,
) {
    companion object {
        const val HORIZON_SECONDS = TciEngine.DEFAULT_HORIZON_SECONDS
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

/** Строка таблицы уставок: время от старта и скорость. */
data class RateTableRow(
    val offsetSeconds: Int,
    val rateDisplay: String,
)

/** Рассчитанные значения плана (без привязки к текущему времени). */
data class TciComputed(
    val bolusMlDisplay: String,
    val bolusTimeDisplay: String,
    val bolusRateDisplay: String,
    val maintenanceStartDisplay: String,
    val maintenanceSteadyDisplay: String,
    val bmiDisplay: String?,
    val lbmDisplay: String?,
    /** Шаговая программа для обычного насоса (D-LIVE-2). */
    val plan: PumpPlan,
    val rateTable: List<RateTableRow>,
)

/** Пересчитывает отображаемые значения под новый план (смена цели, возврат из ручного режима). */
internal fun TciComputed.withPlan(plan: PumpPlan): TciComputed =
    copy(
        bolusMlDisplay = TciEngine.formatVolumeMl(plan.bolusMl).display(),
        bolusTimeDisplay = formatBolusTime(plan.bolusTimeSeconds),
        bolusRateDisplay = TciEngine.formatRateMlPerHour(plan.bolusRateMlPerHour).display(),
        maintenanceStartDisplay = TciEngine.formatRateMlPerHour(plan.maintenanceStartMlPerHour).display(),
        maintenanceSteadyDisplay = TciEngine.formatRateMlPerHour(plan.maintenanceSteadyMlPerHour).display(),
        plan = plan,
        rateTable = buildRateTable(plan),
    )

data class TciUiState(
    val form: TciForm = TciForm(),
    val bolusRateMlPerHour: BigDecimal? = null,
    val blocking: TciBlocking? = null,
    val warnings: List<TciWarning> = emptyList(),
    val computed: TciComputed? = null,
    val session: TciSession = TciSession(),
    val live: TciLive? = null,
    /** Дисклеймер скрыт пользователем («больше не показывать»). */
    val disclaimerHidden: Boolean = false,
    /** Настройки TCI прочитаны из DataStore: до этого дисклеймер не показываем. */
    val preferencesLoaded: Boolean = false,
) {
    val isReady: Boolean get() = computed != null && blocking == null

    val isRunning: Boolean get() = session.status == TciSessionStatus.RUNNING
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
/** Чистый расчёт состояния TCI из формы (без Android/Compose). */
internal fun buildTciState(form: TciForm): TciUiState {
    val bolusRate = form.bolusRateText.toBigDecimalOrNull()
    if (bolusRate == null || bolusRate.signum() <= 0) {
        return TciUiState(form = form, blocking = TciBlocking.BOLUS_RATE)
    }

    val weight = form.weightText.toBigDecimalOrNull()
    if (weight == null || weight.signum() <= 0 || weight > BigDecimal(MAX_WEIGHT_KG)) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.WEIGHT)
    }

    val height = form.heightText.toBigDecimalOrNull()
    val age = form.ageText.trim().toIntOrNull()
    if (form.model == TciModel.SCHNIDER) {
        if (height == null || height.signum() <= 0 || height > BigDecimal(MAX_HEIGHT_CM)) {
            return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.HEIGHT)
        }
        if (age == null || age < MIN_AGE || age > MAX_AGE) {
            return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.AGE)
        }
    }

    val target = form.targetText.toBigDecimalOrNull()
    if (target == null || target.signum() <= 0 || target > BigDecimal(MAX_TARGET_MCG_ML)) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = TciBlocking.TARGET)
    }

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
    val result = TciEngine.pumpPlan(request, stepSeconds = form.tableStepMinutes.coerceIn(MIN_STEP, MAX_STEP) * 60)
    if (result is TciResult.Failure) {
        return TciUiState(form = form, bolusRateMlPerHour = bolusRate, blocking = blockingFrom(result))
    }
    val plan = (result as TciResult.Success).value

    val bmi = if (form.model == TciModel.SCHNIDER) TciEngine.bmi(weight, height!!) else null
    val lbm = if (form.model == TciModel.SCHNIDER) TciEngine.lbmJames(weight, height!!, form.sex) else null

    return TciUiState(
        form = form,
        bolusRateMlPerHour = bolusRate,
        blocking = null,
        warnings = warnings,
        computed =
            TciComputed(
                bolusMlDisplay = TciEngine.formatVolumeMl(plan.bolusMl).display(),
                bolusTimeDisplay = formatBolusTime(plan.bolusTimeSeconds),
                bolusRateDisplay = TciEngine.formatRateMlPerHour(plan.bolusRateMlPerHour).display(),
                maintenanceStartDisplay = TciEngine.formatRateMlPerHour(plan.maintenanceStartMlPerHour).display(),
                maintenanceSteadyDisplay = TciEngine.formatRateMlPerHour(plan.maintenanceSteadyMlPerHour).display(),
                bmiDisplay = bmi?.let { TciEngine.formatConcentrationMcgMl(it).display() },
                lbmDisplay = lbm?.let { TciEngine.formatConcentrationMcgMl(it).display() },
                plan = plan,
                rateTable = buildRateTable(plan),
            ),
    )
}

private fun blockingFrom(result: TciResult.Failure): TciBlocking =
    when (result.reason) {
        TciError.WEIGHT_OUT_OF_RANGE -> TciBlocking.WEIGHT
        TciError.HEIGHT_OUT_OF_RANGE -> TciBlocking.HEIGHT
        TciError.AGE_OUT_OF_RANGE -> TciBlocking.AGE
        TciError.TARGET_OUT_OF_RANGE -> TciBlocking.TARGET
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
        if (h < BigDecimal(UNUSUAL_HEIGHT_MIN_CM) || h > BigDecimal(UNUSUAL_HEIGHT_MAX_CM)) {
            warnings.add(TciWarning.HEIGHT_UNUSUAL)
        }
        if (a < MIN_AGE || a > UNUSUAL_AGE_MAX) {
            warnings.add(TciWarning.AGE_UNUSUAL)
        }
    }
    if (weight < BigDecimal(UNUSUAL_WEIGHT_MIN_KG) || weight > BigDecimal(UNUSUAL_WEIGHT_MAX_KG)) {
        warnings.add(TciWarning.WEIGHT_UNUSUAL)
    }

    val (targetLow, targetHigh) = targetPhaseRange(form)
    if (target < targetLow || target > targetHigh) {
        warnings.add(TciWarning.TARGET_OUT_OF_RANGE)
    }

    return warnings
}

/**
 * Рекомендуемый диапазон цели (VAL-TCI-05): Cp 3–8, Ce 2.5–4.
 * Без чек-листа этапов используется объединённый диапазон индукции и поддержания.
 */
internal fun targetPhaseRange(form: TciForm): Pair<BigDecimal, BigDecimal> =
    if (form.mode == TargetMode.PLASMA) {
        BigDecimal("3") to BigDecimal("8")
    } else {
        BigDecimal("2.5") to BigDecimal("4")
    }

private fun buildRateTable(plan: PumpPlan): List<RateTableRow> =
    plan.steps.map { step ->
        RateTableRow(
            offsetSeconds = step.offsetSeconds,
            rateDisplay = TciEngine.formatRateMlPerHour(step.rateMlPerHour).display(),
        )
    }

/** Болюс: мм:сс (CALC-TCI-14). */
internal fun formatBolusTime(seconds: Int): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val secondsPart = seconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, secondsPart)
}

/** Время до пробуждения: мм:сс, при ≥ 60 мин — чч:мм (CALC-TCI-14). */
internal fun formatWakeTime(seconds: Int?): String? {
    if (seconds == null) return null
    return if (seconds >= SECONDS_PER_HOUR) {
        "%d:%02d".format(seconds / SECONDS_PER_HOUR, (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
    } else {
        "%02d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }
}
