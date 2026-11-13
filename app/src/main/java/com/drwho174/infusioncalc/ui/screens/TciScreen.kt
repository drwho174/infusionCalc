package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.viewmodel.TciBlocking
import com.drwho174.infusioncalc.ui.viewmodel.TciComputed
import com.drwho174.infusioncalc.ui.viewmodel.TciForm
import com.drwho174.infusioncalc.ui.viewmodel.TciUiState
import com.drwho174.infusioncalc.ui.viewmodel.TciViewModel
import com.drwho174.infusioncalc.ui.viewmodel.TciWarning
import com.drwho174.infusioncalc.ui.viewmodel.targetPhaseRange
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TciScreen(
    viewModel: TciViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    val blocking = state.blocking
    val computed = state.computed
    val warnings = state.warnings

    var showReview by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<TciModel?>(null) }
    var pendingMode by remember { mutableStateOf<TargetMode?>(null) }

    val hasInput =
        form.weightText.isNotBlank() ||
            form.targetText.isNotBlank() ||
            form.heightText.isNotBlank() ||
            form.ageText.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tci_title))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.tci_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier =
                                Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showReview = true }) {
                        Text(stringResource(R.string.tci_review), color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TciDisclaimerBanner()

            Spacer(Modifier.height(12.dp))
            ModelModeSection(
                form = form,
                onModelClick = { model ->
                    if (model != form.model) {
                        if (hasInput) pendingModel = model else viewModel.changeModel(model)
                    }
                },
                onModeClick = { mode ->
                    if (mode != form.mode) {
                        if (hasInput) pendingMode = mode else viewModel.changeMode(mode)
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            PatientSection(form = form, viewModel = viewModel)

            Spacer(Modifier.height(12.dp))
            TargetSection(form = form, viewModel = viewModel)

            Spacer(Modifier.height(12.dp))
            StageSection(form = form, viewModel = viewModel)

            if (blocking != null) {
                Spacer(Modifier.height(12.dp))
                BlockingCard(blocking = blocking)
            }

            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                WarningCard(warnings = warnings, form = form, computed = computed)
            }

            if (computed != null) {
                Spacer(Modifier.height(12.dp))
                BolusSection(computed = computed)
                Spacer(Modifier.height(12.dp))
                PlotSection(state = state, onNowSecondsChange = viewModel::onNowSecondsChange)
                Spacer(Modifier.height(12.dp))
                WakeSection(form = form, viewModel = viewModel, computed = computed)
            } else if (blocking == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.tci_not_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tci_info_prediction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showReview) {
        ReviewDialog(state = state, onDismiss = { showReview = false })
    }

    pendingModel?.let { model ->
        ConfirmResetDialog(
            subject = stringResource(R.string.tci_model),
            onConfirm = {
                viewModel.changeModel(model)
                pendingModel = null
            },
            onDismiss = { pendingModel = null },
        )
    }

    pendingMode?.let { mode ->
        ConfirmResetDialog(
            subject = stringResource(R.string.tci_mode),
            onConfirm = {
                viewModel.changeMode(mode)
                pendingMode = null
            },
            onDismiss = { pendingMode = null },
        )
    }
}

// ---------------------------------------------------------------------- баннер

@Composable
private fun TciDisclaimerBanner() {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                stringResource(R.string.tci_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------- модель/режим

@Composable
private fun ModelModeSection(
    form: TciForm,
    onModelClick: (TciModel) -> Unit,
    onModeClick: (TargetMode) -> Unit,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_model_mode)) {
        Text(stringResource(R.string.tci_model), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        TciChipRow {
            FilterChip(
                selected = form.model == TciModel.MARSH,
                onClick = { onModelClick(TciModel.MARSH) },
                label = { Text(stringResource(R.string.tci_model_marsh)) },
            )
            FilterChip(
                selected = form.model == TciModel.SCHNIDER,
                onClick = { onModelClick(TciModel.SCHNIDER) },
                label = { Text(stringResource(R.string.tci_model_schnider)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.tci_mode), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        TciChipRow {
            FilterChip(
                selected = form.mode == TargetMode.PLASMA,
                onClick = { onModeClick(TargetMode.PLASMA) },
                label = { Text(stringResource(R.string.tci_mode_plasma)) },
            )
            FilterChip(
                selected = form.mode == TargetMode.EFFECT_SITE,
                onClick = { onModeClick(TargetMode.EFFECT_SITE) },
                label = { Text(stringResource(R.string.tci_mode_effect)) },
            )
        }
    }
}

// ---------------------------------------------------------------------- пациент

@Composable
private fun PatientSection(
    form: TciForm,
    viewModel: TciViewModel,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_patient)) {
        OutlinedTextField(
            value = form.weightText,
            onValueChange = viewModel::onWeightChange,
            label = { Text(stringResource(R.string.tci_weight)) },
            suffix = { Text(stringResource(R.string.tci_kg)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.model == TciModel.SCHNIDER) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.heightText,
                onValueChange = viewModel::onHeightChange,
                label = { Text(stringResource(R.string.tci_height)) },
                suffix = { Text(stringResource(R.string.tci_cm)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.ageText,
                onValueChange = viewModel::onAgeChange,
                label = { Text(stringResource(R.string.tci_age)) },
                suffix = { Text(stringResource(R.string.tci_years)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tci_sex), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            TciChipRow {
                FilterChip(
                    selected = form.sex == Sex.MALE,
                    onClick = { viewModel.onSexSelected(Sex.MALE) },
                    label = { Text(stringResource(R.string.tci_sex_male)) },
                )
                FilterChip(
                    selected = form.sex == Sex.FEMALE,
                    onClick = { viewModel.onSexSelected(Sex.FEMALE) },
                    label = { Text(stringResource(R.string.tci_sex_female)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- цель/концентрация

@Composable
private fun TargetSection(
    form: TciForm,
    viewModel: TciViewModel,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_target)) {
        Text(stringResource(R.string.tci_concentration), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        TciChipRow {
            FilterChip(
                selected = form.concentrationMgPerMl == BigDecimal("10"),
                onClick = { viewModel.onConcentrationSelected(BigDecimal("10")) },
                label = { Text(stringResource(R.string.tci_concentration_10)) },
            )
            FilterChip(
                selected = form.concentrationMgPerMl == BigDecimal("20"),
                onClick = { viewModel.onConcentrationSelected(BigDecimal("20")) },
                label = { Text(stringResource(R.string.tci_concentration_20)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.targetText,
            onValueChange = viewModel::onTargetChange,
            label = {
                Text(
                    stringResource(
                        if (form.mode == TargetMode.PLASMA) R.string.tci_target_cp else R.string.tci_target_ce,
                    ),
                )
            },
            suffix = { Text(stringResource(R.string.tci_mcg_ml)) },
            supportingText = {
                Text(
                    stringResource(
                        if (form.mode == TargetMode.PLASMA) R.string.tci_range_hint_cp else R.string.tci_range_hint_ce,
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.bolusRateText,
            onValueChange = viewModel::onBolusRateChange,
            label = { Text(stringResource(R.string.tci_bolus_rate)) },
            suffix = { Text(stringResource(R.string.tci_ml_h)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------- чек-лист этапов

@Composable
private fun StageSection(
    form: TciForm,
    viewModel: TciViewModel,
) {
    val active =
        when {
            !form.inductionDone -> 0
            !form.maintenanceDone -> 1
            else -> 2
        }
    TciSectionCard(title = stringResource(R.string.tci_section_stages)) {
        StageRow(
            label = stringResource(R.string.tci_stage_induction),
            checked = form.inductionDone,
            active = active == 0,
            onToggle = { viewModel.onStageToggle(induction = it) },
        )
        StageRow(
            label = stringResource(R.string.tci_stage_maintenance),
            checked = form.maintenanceDone,
            active = active == 1,
            onToggle = { viewModel.onStageToggle(maintenance = it) },
        )
        StageRow(
            label = stringResource(R.string.tci_stage_emergence),
            checked = form.emergenceDone,
            active = active == 2,
            onToggle = { viewModel.onStageToggle(emergence = it) },
        )
    }
}

@Composable
private fun StageRow(
    label: String,
    checked: Boolean,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val bg =
        if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg, MaterialTheme.shapes.small)
                .padding(end = 8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (checked || active) {
            Text(
                text =
                    stringResource(
                        if (checked) R.string.tci_stage_done else R.string.tci_stage_active,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- блокирующее сообщение

@Composable
private fun BlockingCard(blocking: TciBlocking) {
    val text =
        when (blocking) {
            TciBlocking.WEIGHT -> stringResource(R.string.tci_blocking_weight)
            TciBlocking.HEIGHT -> stringResource(R.string.tci_blocking_height)
            TciBlocking.AGE -> stringResource(R.string.tci_blocking_age)
            TciBlocking.TARGET -> stringResource(R.string.tci_blocking_target)
            TciBlocking.BOLUS_RATE -> stringResource(R.string.tci_blocking_bolus_rate)
        }
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

// ---------------------------------------------------------------------- предупреждения

@Composable
private fun WarningCard(
    warnings: List<TciWarning>,
    form: TciForm,
    computed: TciComputed?,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            warnings.forEach { warning ->
                val text =
                    when (warning) {
                        TciWarning.WEIGHT_UNUSUAL -> stringResource(R.string.tci_warn_weight, form.weightText)
                        TciWarning.HEIGHT_UNUSUAL -> stringResource(R.string.tci_warn_height, form.heightText)
                        TciWarning.AGE_UNUSUAL -> stringResource(R.string.tci_warn_age, form.ageText)
                        TciWarning.BMI_OUT_OF_RANGE -> stringResource(R.string.tci_warn_bmi, computed?.bmiDisplay ?: "—")
                        TciWarning.TARGET_OUT_OF_RANGE -> {
                            val (low, high) = targetPhaseRange(form)
                            stringResource(R.string.tci_warn_target, form.targetText, low.display() + "–" + high.display())
                        }
                    }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- болюс/поддержание

@Composable
private fun BolusSection(computed: TciComputed) {
    var rateTableExpanded by remember { mutableStateOf(false) }
    TciSectionCard(title = stringResource(R.string.tci_section_bolus)) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.tci_bolus), style = MaterialTheme.typography.labelMedium)
                BigValue(
                    value = computed.bolusMlDisplay,
                    unit = stringResource(R.string.tci_ml),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.tci_bolus_time, computed.bolusTimeDisplay) +
                        " · " +
                        computed.bolusRateDisplay +
                        " " +
                        stringResource(R.string.tci_ml_h),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tci_maintenance), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text =
                    stringResource(R.string.tci_maintenance_start) +
                        " " +
                        computed.maintenanceStartDisplay +
                        " " +
                        stringResource(R.string.tci_ml_h),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text =
                    stringResource(R.string.tci_maintenance_steady) +
                        " " +
                        computed.maintenanceSteadyDisplay +
                        " " +
                        stringResource(R.string.tci_ml_h),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (computed.bmiDisplay != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tci_review_patient) + ": BMI " + computed.bmiDisplay,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (computed.lbmDisplay != null) {
            Text(
                "LBM " + computed.lbmDisplay + " " + stringResource(R.string.tci_kg),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { rateTableExpanded = !rateTableExpanded }) {
            Text(stringResource(R.string.tci_rate_table))
        }
        if (rateTableExpanded) {
            computed.rateTable.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        row.minute.toString() + " " + stringResource(R.string.tci_minutes),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.rateDisplay + " " + stringResource(R.string.tci_ml_h),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- график

@Composable
private fun PlotSection(
    state: TciUiState,
    onNowSecondsChange: (Int) -> Unit,
) {
    val computed = state.computed ?: return
    val steps = computed.simulation.steps
    TciSectionCard(title = stringResource(R.string.tci_section_plot)) {
        Text(stringResource(R.string.tci_now), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(
                value = computed.cpNowDisplay,
                unit = stringResource(R.string.tci_mcg_ml),
                style = MaterialTheme.typography.headlineSmall,
                prefix = "Cp ",
                live = true,
            )
            Spacer(Modifier.weight(1f))
            BigValue(
                value = computed.ceNowDisplay,
                unit = stringResource(R.string.tci_mcg_ml),
                style = MaterialTheme.typography.headlineSmall,
                prefix = "Ce ",
                live = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        CpCeChart(
            steps = steps,
            horizonSeconds = TciForm.HORIZON_SECONDS,
            cpNow = computed.cpNowDisplay,
            ceNow = computed.ceNowDisplay,
        )
        Spacer(Modifier.height(8.dp))
        LegendRow()
        Spacer(Modifier.height(8.dp))
        Slider(
            value = state.form.nowSeconds.toFloat(),
            onValueChange = { seconds -> onNowSecondsChange(seconds.toInt()) },
            valueRange = 0f..TciForm.HORIZON_SECONDS.toFloat(),
        )
        Text(
            stringResource(R.string.tci_now) +
                ": " +
                (state.form.nowSeconds / 60).toString() +
                " " +
                stringResource(R.string.tci_minutes),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.tci_chart_legend_cp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.tci_chart_legend_ce),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun CpCeChart(
    steps: List<SimStep>,
    horizonSeconds: Int,
    cpNow: String,
    ceNow: String,
) {
    val cpColor = MaterialTheme.colorScheme.primary
    val ceColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxC =
        steps
            .maxOf { maxOf(it.cpMcgPerMl, it.ceMcgPerMl) }
            .toFloat()
            .coerceAtLeast(1f)
    val maxT = steps.lastOrNull()?.timeSeconds ?: horizonSeconds

    val chartDescription =
        stringResource(R.string.tci_chart_description) + ". Cp " + cpNow + ", Ce " + ceNow
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val maxLabel = formatAxisValue(maxC)

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = chartDescription },
    ) {
        val stroke = 2.dp.toPx()
        val padLeft = 30.dp.toPx()
        val padBottom = 16.dp.toPx()
        val padTop = 6.dp.toPx()
        val top = padTop
        val bottom = size.height - padBottom
        val left = padLeft
        val right = size.width

        fun x(tSeconds: Int): Float = left + (tSeconds.toFloat() / maxT.toFloat()) * (right - left)

        fun y(c: BigDecimal): Float = bottom - (c.toFloat() / maxC) * (bottom - top)

        // Подписи оси Y (мкг/мл): максимум сверху, 0 снизу
        val maxText = textMeasurer.measure(AnnotatedString(maxLabel), axisStyle)
        drawText(maxText, topLeft = Offset(0f, top - maxText.size.height / 2f))
        val zeroText = textMeasurer.measure(AnnotatedString("0"), axisStyle)
        drawText(zeroText, topLeft = Offset(0f, bottom - zeroText.size.height / 2f))

        // Подписи оси X (минуты)
        listOf(0, 15, 30, 45, 60).forEach { minute ->
            val lx = x(minute * 60)
            val label = textMeasurer.measure(AnnotatedString("$minute′"), axisStyle)
            drawText(label, topLeft = Offset(lx - label.size.width / 2f, bottom + 2.dp.toPx()))
        }

        // сетка: линии на 25/50/75/100% высоты
        for (f in 1..3) {
            val gy = top + (bottom - top) * (f / 4f)
            drawLine(color = gridColor, start = Offset(left, gy), end = Offset(right, gy), strokeWidth = 1.dp.toPx())
        }

        val cpPath = Path()
        steps.forEachIndexed { index, step ->
            val px = x(step.timeSeconds)
            val py = y(step.cpMcgPerMl)
            if (index == 0) cpPath.moveTo(px, py) else cpPath.lineTo(px, py)
        }
        drawPath(cpPath, color = cpColor, style = Stroke(width = stroke))

        val cePath = Path()
        steps.forEachIndexed { index, step ->
            val px = x(step.timeSeconds)
            val py = y(step.ceMcgPerMl)
            if (index == 0) cePath.moveTo(px, py) else cePath.lineTo(px, py)
        }
        drawPath(
            cePath,
            color = ceColor,
            style =
                Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                ),
        )
    }
}

private fun formatAxisValue(value: Float): String =
    if (value >= 10f) value.toInt().toString() else "%.1f".format(value)

// ---------------------------------------------------------------------- пробуждение

@Composable
private fun WakeSection(
    form: TciForm,
    viewModel: TciViewModel,
    computed: TciComputed,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_wake)) {
        OutlinedTextField(
            value = form.wakeThresholdText,
            onValueChange = viewModel::onThresholdChange,
            label = { Text(stringResource(R.string.tci_wake_threshold)) },
            suffix = { Text(stringResource(R.string.tci_mcg_ml)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.tci_wake_time), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            BigValue(
                value = computed.wakeTimeDisplay ?: "—",
                unit = "",
                style = MaterialTheme.typography.headlineMedium,
                live = true,
            )
        }
        Text(
            stringResource(R.string.tci_wake_prediction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.tci_wake_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------- диалоги

@Composable
private fun ConfirmResetDialog(
    subject: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tci_review_title)) },
        text = { Text(stringResource(R.string.tci_confirm_reset, subject)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.tci_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReviewDialog(
    state: TciUiState,
    onDismiss: () -> Unit,
) {
    val form = state.form
    val computed = state.computed
    val sexLabel =
        if (form.sex == Sex.MALE) stringResource(R.string.tci_sex_male) else stringResource(R.string.tci_sex_female)
    val patient =
        if (form.model == TciModel.SCHNIDER) {
            "${form.weightText} ${stringResource(R.string.tci_kg)} · " +
                "${form.heightText} ${stringResource(R.string.tci_cm)} · " +
                "${form.ageText} ${stringResource(R.string.tci_years)} · $sexLabel"
        } else {
            "${form.weightText} ${stringResource(R.string.tci_kg)}"
        }
    val concentration =
        if (form.concentrationMgPerMl == BigDecimal("20")) {
            stringResource(R.string.tci_concentration_20)
        } else {
            stringResource(R.string.tci_concentration_10)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tci_review_title)) },
        text = {
            Column {
                ReviewRow(stringResource(R.string.tci_review_model), if (form.model == TciModel.MARSH) "Marsh" else "Schnider")
                ReviewRow(stringResource(R.string.tci_review_mode), stringResource(if (form.mode == TargetMode.PLASMA) R.string.tci_mode_plasma else R.string.tci_mode_effect))
                ReviewRow(stringResource(R.string.tci_review_patient), patient)
                ReviewRow(stringResource(R.string.tci_review_concentration), concentration)
                ReviewRow(stringResource(R.string.tci_review_target), form.targetText + " " + stringResource(R.string.tci_mcg_ml))
                if (computed != null) {
                    ReviewRow(stringResource(R.string.tci_review_bolus), computed.bolusMlDisplay + " " + stringResource(R.string.tci_ml))
                    ReviewRow(stringResource(R.string.tci_review_rate), computed.maintenanceStartDisplay + " " + stringResource(R.string.tci_ml_h))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------- общее

@Composable
private fun TciSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TciChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun BigValue(
    value: String,
    unit: String,
    style: TextStyle,
    prefix: String = "",
    live: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            if (live) {
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            } else {
                Modifier
            },
    ) {
        Text(
            text = prefix + value,
            style = style.copy(fontFeatureSettings = "tnum"),
        )
        if (unit.isNotBlank()) {
            Text(
                text = " $unit",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
