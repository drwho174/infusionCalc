@file:Suppress("TooManyFunctions")

package com.drwho174.infusioncalc.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import com.drwho174.infusioncalc.ui.viewmodel.MAX_STEP
import com.drwho174.infusioncalc.ui.viewmodel.MIN_STEP
import com.drwho174.infusioncalc.ui.viewmodel.TciBlocking
import com.drwho174.infusioncalc.ui.viewmodel.TciComputed
import com.drwho174.infusioncalc.ui.viewmodel.TciForm
import com.drwho174.infusioncalc.ui.viewmodel.TciLive
import com.drwho174.infusioncalc.ui.viewmodel.TciPhase
import com.drwho174.infusioncalc.ui.viewmodel.TciSessionStatus
import com.drwho174.infusioncalc.ui.viewmodel.TciUiState
import com.drwho174.infusioncalc.ui.viewmodel.TciViewModel
import com.drwho174.infusioncalc.ui.viewmodel.TciWarning
import com.drwho174.infusioncalc.ui.viewmodel.targetPhaseRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Как часто экран перерисовывается, пока виден. */
private const val TICK_INTERVAL_MS = 1_000L

private const val SDK_POST_NOTIFICATIONS = 33

/** Не чаще раза в 30 с — чтобы TalkBack не озвучивал каждую секунду. */
private const val LIVE_ANNOUNCE_SECONDS = 30

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** Округление оси времени вверх, чтобы курсор не упирался в край. */
private const val CHART_ROUND_SECONDS = 900
private const val CHART_STEPS = 4
private const val CHART_GRID_LINES = 3
private const val AXIS_MAX_INT_THRESHOLD = 10f
private const val AXIS_FONT_SIZE_SP = 10f
private const val CHART_DASH_ON = 12f
private const val CHART_DASH_OFF = 8f
private const val CURSOR_DASH_ON = 6f
private const val CURSOR_DASH_OFF = 6f

@Suppress("LongMethod", "CyclomaticComplexMethod")
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
    val session = state.session
    val live = state.live
    val active = session.isActive
    val context = LocalContext.current
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.refreshNotifications()
        }
    val requestStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= SDK_POST_NOTIFICATIONS &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start()
    }

    LaunchedEffect(session.isActive) {
        if (session.isActive &&
            Build.VERSION.SDK_INT >= SDK_POST_NOTIFICATIONS &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pendingModel by remember { mutableStateOf<TciModel?>(null) }
    var pendingMode by remember { mutableStateOf<TargetMode?>(null) }
    var dismissedDisclaimer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Экран не должен засыпать, пока идёт сессия: на него смотрят во время инфузии.
    val view = LocalView.current
    DisposableEffect(session.status) {
        view.keepScreenOn = session.status == TciSessionStatus.RUNNING
        onDispose { view.keepScreenOn = false }
    }

    // Тикаем только пока экран виден; время всё равно считается по часам,
    // поэтому в фоне сессия продолжается без нагрузки на батарею.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, viewModel) {
        var ticker: kotlinx.coroutines.Job? = null
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        viewModel.refresh()
                        ticker =
                            scope.launch {
                                while (isActive) {
                                    viewModel.refresh()
                                    delay(TICK_INTERVAL_MS)
                                }
                            }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        ticker?.cancel()
                        ticker = null
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ticker?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                                    ).padding(horizontal = 8.dp, vertical = 2.dp),
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
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.tci_settings),
                        )
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
            ModelModeSection(
                form = form,
                enabled = !active,
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
            PatientSection(form = form, viewModel = viewModel, enabled = !active, blocking = blocking)

            Spacer(Modifier.height(12.dp))
            TargetSection(form = form, viewModel = viewModel, enabled = !active, blocking = blocking)

            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                WarningCard(warnings = warnings, form = form, computed = computed)
            }

            if (computed != null) {
                Spacer(Modifier.height(12.dp))
                BolusSection(computed = computed, currentOffset = live?.stepOffsetSeconds)
                Spacer(Modifier.height(12.dp))
                PlotSection(
                    computed = computed,
                    live = live,
                    onConfirm = viewModel::confirmCurrentRate,
                    onManualRate = viewModel::setManualRate,
                    onReturnToAuto = viewModel::returnToAutomatic,
                )
                Spacer(Modifier.height(12.dp))
                SessionSection(
                    state = state,
                    onStart = requestStart,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stop,
                )
                Spacer(Modifier.height(12.dp))
                WakeSection(
                    form = form,
                    live = live,
                    paused = session.status == TciSessionStatus.PAUSED,
                    enabled = !active,
                    onThresholdChange = viewModel::onThresholdChange,
                )
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

    if (showSettings) {
        TciSettingsDialog(
            currentBolusRate = form.bolusRateText,
            currentTableStep = form.tableStepMinutes,
            currentWarningLead = form.warningLeadMinutes,
            onDismiss = { showSettings = false },
            onShowDisclaimer = {
                viewModel.showDisclaimerAgain()
                showSettings = false
            },
            onSave = { bolus, step, lead ->
                viewModel.applyTciSettings(bolus, step, lead)
                showSettings = false
            },
        )
    }

    if (state.preferencesLoaded && !state.disclaimerHidden && !dismissedDisclaimer) {
        TciDisclaimerDialog(
            onDismiss = { neverShow ->
                dismissedDisclaimer = true
                if (neverShow) viewModel.hideDisclaimerForever()
            },
        )
    }
}

// ---------------------------------------------------------------------- сессия и насос

@Suppress("LongMethod")
@Composable
private fun SessionSection(
    state: TciUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val session = state.session
    val live = state.live
    var showStop by remember { mutableStateOf(false) }

    TciSectionCard(title = stringResource(R.string.tci_session_title)) {
        if (!session.isActive) {
            Text(
                stringResource(R.string.tci_session_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart, enabled = state.isReady) {
                Text(stringResource(R.string.tci_session_start))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tci_session_steps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatElapsed(live?.elapsedSeconds ?: 0),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (session.status == TciSessionStatus.PAUSED) {
                                R.string.tci_session_paused
                            } else {
                                R.string.tci_session_running
                            },
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.status == TciSessionStatus.RUNNING) {
                    OutlinedButton(onClick = onPause) { Text(stringResource(R.string.tci_session_pause)) }
                } else {
                    OutlinedButton(onClick = onResume) { Text(stringResource(R.string.tci_session_resume)) }
                }
                OutlinedButton(onClick = { showStop = true }) { Text(stringResource(R.string.tci_session_stop)) }
            }
        }
    }

    if (showStop) {
        AlertDialog(
            onDismissRequest = { showStop = false },
            title = { Text(stringResource(R.string.tci_session_stop_title)) },
            text = { Text(stringResource(R.string.tci_session_stop_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStop()
                        showStop = false
                    },
                ) { Text(stringResource(R.string.tci_session_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { showStop = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun PumpPanel(
    live: TciLive,
    computed: TciComputed,
    onConfirm: () -> Unit,
    onManualRate: () -> Unit,
    onReturnToAuto: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text =
                    stringResource(
                        when {
                            live.paused -> R.string.tci_pump_phase_paused
                            live.manual -> R.string.tci_pump_manual_mode
                            live.phase == TciPhase.BOLUS -> R.string.tci_pump_phase_bolus
                            else -> R.string.tci_pump_phase_maintenance
                        },
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = live.currentRateMlPerHour?.display() ?: "—",
                    style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = " " + stringResource(R.string.tci_ml_h),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val nextIn = live.nextChangeInSeconds
            when {
                live.paused ->
                    Text(
                        stringResource(R.string.tci_pump_paused),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                live.manual ->
                    Text(
                        stringResource(R.string.tci_pump_manual_active),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                live.phase == TciPhase.BOLUS -> {
                    Text(
                        stringResource(R.string.tci_bolus_infusion, computed.bolusMlDisplay, computed.bolusTimeDisplay),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (nextIn != null) {
                        Text(
                            stringResource(R.string.tci_pump_next_in, formatElapsed(nextIn)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                nextIn != null -> {
                    Text(
                        stringResource(R.string.tci_pump_next_in, formatElapsed(nextIn)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    live.nextRateMlPerHour?.let { next ->
                        Text(
                            stringResource(R.string.tci_pump_next_rate, next.display()) +
                                " " +
                                stringResource(R.string.tci_ml_h),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                else ->
                    Text(
                        stringResource(R.string.tci_pump_steady),
                        style = MaterialTheme.typography.bodyMedium,
                    )
            }
            if (live.warningSoon && live.nextChangeInSeconds != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tci_pump_warning_soon, formatElapsed(live.nextChangeInSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (live.overdue) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tci_pump_overdue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!live.paused) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, enabled = !live.confirmed && !live.manual) {
                        Text(
                            stringResource(
                                if (live.confirmed) R.string.tci_pump_confirmed else R.string.tci_pump_confirm,
                            ),
                        )
                    }
                    if (live.manual) {
                        OutlinedButton(onClick = onReturnToAuto) {
                            Text(stringResource(R.string.tci_return_auto))
                        }
                    } else {
                        TextButton(onClick = onManualRate) {
                            Text(stringResource(R.string.tci_pump_manual_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualRateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tci_pump_manual_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.tci_pump_manual_rate)) },
                    suffix = { Text(stringResource(R.string.tci_ml_h)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tci_pump_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.toBigDecimalOrNull()?.signum() == 1,
            ) { Text(stringResource(R.string.tci_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---------------------------------------------------------------------- дисклеймер и настройки

@Composable
private fun TciDisclaimerDialog(onDismiss: (neverShow: Boolean) -> Unit) {
    var neverShow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text(stringResource(R.string.tci_disclaimer_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tci_disclaimer), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = neverShow, onCheckedChange = { neverShow = it })
                    Text(stringResource(R.string.tci_disclaimer_never), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(neverShow) }) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun TciSettingsDialog(
    currentBolusRate: String,
    currentTableStep: Int,
    currentWarningLead: Int,
    onDismiss: () -> Unit,
    onShowDisclaimer: () -> Unit,
    onSave: (bolusRate: String, tableStep: Int, warningLead: Int) -> Unit,
) {
    var bolus by remember { mutableStateOf(currentBolusRate) }
    var step by remember { mutableStateOf(currentTableStep.toString()) }
    var lead by remember { mutableStateOf(currentWarningLead.toString()) }
    val stepValue = step.toIntOrNull()
    val leadValue = lead.toIntOrNull()
    val bolusValid = bolus.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
    val stepValid = stepValue != null && stepValue in MIN_STEP..MAX_STEP
    val leadValid = leadValue != null && leadValue in MIN_STEP..MAX_STEP

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tci_settings_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = bolus,
                    onValueChange = { bolus = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.tci_bolus_rate_setting)) },
                    suffix = { Text(stringResource(R.string.tci_ml_h)) },
                    singleLine = true,
                    isError = !bolusValid,
                    supportingText =
                        if (bolusValid) {
                            { Text(stringResource(R.string.tci_bolus_rate_hint)) }
                        } else {
                            { Text(stringResource(R.string.tci_blocking_bolus_rate)) }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tci_table_step_setting)) },
                    suffix = { Text(stringResource(R.string.tci_minutes)) },
                    singleLine = true,
                    isError = !stepValid,
                    supportingText = { Text(stringResource(R.string.tci_range_1_15)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lead,
                    onValueChange = { lead = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tci_warning_lead_setting)) },
                    suffix = { Text(stringResource(R.string.tci_minutes)) },
                    singleLine = true,
                    isError = !leadValid,
                    supportingText = { Text(stringResource(R.string.tci_range_1_15)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onShowDisclaimer) {
                    Text(stringResource(R.string.tci_disclaimer_show))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(bolus, stepValue!!, leadValue!!) },
                enabled = bolusValid && stepValid && leadValid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---------------------------------------------------------------------- модель/режим

@Composable
private fun ModelModeSection(
    form: TciForm,
    enabled: Boolean,
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
                enabled = enabled,
                label = { Text(stringResource(R.string.tci_model_marsh)) },
            )
            FilterChip(
                selected = form.model == TciModel.SCHNIDER,
                onClick = { onModelClick(TciModel.SCHNIDER) },
                enabled = enabled,
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
                enabled = enabled,
                label = { Text(stringResource(R.string.tci_mode_plasma)) },
            )
            FilterChip(
                selected = form.mode == TargetMode.EFFECT_SITE,
                onClick = { onModeClick(TargetMode.EFFECT_SITE) },
                enabled = enabled,
                label = { Text(stringResource(R.string.tci_mode_effect)) },
            )
        }
    }
}

// ---------------------------------------------------------------------- пациент

@Suppress("LongMethod")
@Composable
private fun PatientSection(
    form: TciForm,
    viewModel: TciViewModel,
    enabled: Boolean,
    blocking: TciBlocking?,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_patient)) {
        OutlinedTextField(
            value = form.weightText,
            onValueChange = viewModel::onWeightChange,
            label = { Text(stringResource(R.string.tci_weight)) },
            suffix = { Text(stringResource(R.string.tci_kg)) },
            singleLine = true,
            enabled = enabled,
            isError = blocking == TciBlocking.WEIGHT,
            supportingText =
                if (blocking == TciBlocking.WEIGHT) {
                    { Text(stringResource(R.string.tci_blocking_weight)) }
                } else {
                    null
                },
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
                enabled = enabled,
                isError = blocking == TciBlocking.HEIGHT,
                supportingText =
                    if (blocking == TciBlocking.HEIGHT) {
                        { Text(stringResource(R.string.tci_blocking_height)) }
                    } else {
                        null
                    },
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
                enabled = enabled,
                isError = blocking == TciBlocking.AGE,
                supportingText =
                    if (blocking == TciBlocking.AGE) {
                        { Text(stringResource(R.string.tci_blocking_age)) }
                    } else {
                        null
                    },
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
                    enabled = enabled,
                    label = { Text(stringResource(R.string.tci_sex_male)) },
                )
                FilterChip(
                    selected = form.sex == Sex.FEMALE,
                    onClick = { viewModel.onSexSelected(Sex.FEMALE) },
                    enabled = enabled,
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
    enabled: Boolean,
    blocking: TciBlocking?,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_target)) {
        Text(stringResource(R.string.tci_concentration), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        TciChipRow {
            FilterChip(
                selected = form.concentrationMgPerMl == BigDecimal("10"),
                onClick = { viewModel.onConcentrationSelected(BigDecimal("10")) },
                enabled = enabled,
                label = { Text(stringResource(R.string.tci_concentration_10)) },
            )
            FilterChip(
                selected = form.concentrationMgPerMl == BigDecimal("20"),
                onClick = { viewModel.onConcentrationSelected(BigDecimal("20")) },
                enabled = enabled,
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
            isError = blocking == TciBlocking.TARGET,
            supportingText = {
                Text(
                    stringResource(
                        if (blocking == TciBlocking.TARGET) {
                            R.string.tci_blocking_target
                        } else if (form.mode == TargetMode.PLASMA) {
                            R.string.tci_range_hint_cp
                        } else {
                            R.string.tci_range_hint_ce
                        },
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tci_target_change_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        TciWarning.BMI_OUT_OF_RANGE ->
                            stringResource(R.string.tci_warn_bmi, computed?.bmiDisplay ?: "—")
                        TciWarning.TARGET_OUT_OF_RANGE -> {
                            val (low, high) = targetPhaseRange(form)
                            stringResource(
                                R.string.tci_warn_target,
                                form.targetText,
                                low.display() + "–" + high.display(),
                            )
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

// ---------------------------------------------------------------------- болюс и таблица скоростей

@Suppress("LongMethod")
@Composable
private fun BolusSection(
    computed: TciComputed,
    currentOffset: Int?,
) {
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
        TextButton(onClick = { rateTableExpanded = !rateTableExpanded }) {
            Text(stringResource(R.string.tci_rate_table))
        }
        if (rateTableExpanded) {
            computed.rateTable.forEach { row ->
                val current = row.offsetSeconds == currentOffset
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (current) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                MaterialTheme.shapes.extraSmall,
                            ).padding(vertical = 2.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = formatElapsed(row.offsetSeconds),
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

@Suppress("LongMethod")
@Composable
private fun PlotSection(
    computed: TciComputed,
    live: TciLive?,
    onConfirm: () -> Unit,
    onManualRate: (String) -> Unit,
    onReturnToAuto: () -> Unit,
) {
    var showManual by remember { mutableStateOf(false) }
    val elapsed = live?.chartSeconds ?: 0
    TciSectionCard(title = stringResource(R.string.tci_section_plot)) {
        if (live != null) {
            PumpPanel(
                live = live,
                computed = computed,
                onConfirm = onConfirm,
                onManualRate = { showManual = true },
                onReturnToAuto = onReturnToAuto,
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(
                value = live?.cpDisplay ?: "—",
                unit = stringResource(R.string.tci_mcg_ml),
                style = MaterialTheme.typography.headlineSmall,
                prefix = "Cp ",
            )
            Spacer(Modifier.weight(1f))
            BigValue(
                value = live?.ceDisplay ?: "—",
                unit = stringResource(R.string.tci_mcg_ml),
                style = MaterialTheme.typography.headlineSmall,
                prefix = "Ce ",
            )
        }
        if (live != null) {
            val bucket = live.elapsedSeconds / LIVE_ANNOUNCE_SECONDS
            val summary =
                stringResource(
                    R.string.tci_live_summary,
                    live.cpDisplay,
                    live.ceDisplay,
                    live.wakeDisplay ?: "—",
                )
            val announced = remember(bucket) { summary }
            Text(
                text = announced,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(8.dp))
        CpCeChart(
            steps = live?.curve ?: computed.plan.predicted,
            horizonSeconds = TciForm.HORIZON_SECONDS,
            elapsedSeconds = if (live != null) elapsed else null,
        )
        Spacer(Modifier.height(8.dp))
        LegendRow()
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                if (live == null) {
                    stringResource(R.string.tci_plot_idle)
                } else {
                    stringResource(R.string.tci_plot_elapsed, formatElapsed(elapsed))
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showManual) {
        ManualRateDialog(
            onDismiss = { showManual = false },
            onConfirm = { raw ->
                onManualRate(raw)
                showManual = false
            },
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

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun CpCeChart(
    steps: List<SimStep>,
    horizonSeconds: Int,
    elapsedSeconds: Int?,
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
    val maxT =
        maxOf(
            horizonSeconds,
            elapsedSeconds?.let {
                ((it + CHART_ROUND_SECONDS - 1) / CHART_ROUND_SECONDS) * CHART_ROUND_SECONDS
            } ?: horizonSeconds,
        )

    val chartDescription =
        stringResource(R.string.tci_chart_description) +
            if (elapsedSeconds != null) {
                " " + stringResource(R.string.tci_plot_elapsed, formatElapsed(elapsedSeconds))
            } else {
                ""
            }
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = AXIS_FONT_SIZE_SP.sp, color = axisColor)
    val maxLabel = formatAxisValue(maxC)
    val cursorColor = MaterialTheme.colorScheme.error

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

        val maxText = textMeasurer.measure(AnnotatedString(maxLabel), axisStyle)
        drawText(maxText, topLeft = Offset(0f, top - maxText.size.height / 2f))
        val zeroText = textMeasurer.measure(AnnotatedString("0"), axisStyle)
        drawText(zeroText, topLeft = Offset(0f, bottom - zeroText.size.height / 2f))

        for (quarter in 0..CHART_STEPS) {
            val seconds = maxT * quarter / CHART_STEPS
            val lx = x(seconds)
            val label = textMeasurer.measure(AnnotatedString(formatAxisTime(seconds)), axisStyle)
            drawText(label, topLeft = Offset(lx - label.size.width / 2f, bottom + 2.dp.toPx()))
        }

        for (f in 1..CHART_GRID_LINES) {
            val gy = top + (bottom - top) * (f / CHART_STEPS.toFloat())
            drawLine(color = gridColor, start = Offset(left, gy), end = Offset(right, gy), strokeWidth = 1.dp.toPx())
        }

        val cpPath = Path()
        val cePath = Path()
        var lastStep: SimStep? = null
        steps.filter { it.timeSeconds <= maxT }.forEachIndexed { index, step ->
            val px = x(step.timeSeconds)
            val py = y(step.cpMcgPerMl)
            if (index == 0) cpPath.moveTo(px, py) else cpPath.lineTo(px, py)
            val ey = y(step.ceMcgPerMl)
            if (index == 0) cePath.moveTo(px, ey) else cePath.lineTo(px, ey)
            lastStep = step
        }
        // Продлеваем полку до правого края, если сессия длиннее прогноза.
        lastStep?.let { last ->
            if (last.timeSeconds < maxT) {
                cpPath.lineTo(x(maxT), y(last.cpMcgPerMl))
                cePath.lineTo(x(maxT), y(last.ceMcgPerMl))
            }
        }
        drawPath(cpPath, color = cpColor, style = Stroke(width = stroke))
        drawPath(
            cePath,
            color = ceColor,
            style =
                Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(CHART_DASH_ON, CHART_DASH_OFF)),
                ),
        )

        if (elapsedSeconds != null && elapsedSeconds <= maxT) {
            val cx = x(elapsedSeconds)
            drawLine(
                color = cursorColor,
                start = Offset(cx, top),
                end = Offset(cx, bottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(CURSOR_DASH_ON, CURSOR_DASH_OFF)),
            )
            val point = steps.lastOrNull { it.timeSeconds <= elapsedSeconds } ?: steps.firstOrNull()
            point?.let {
                drawCircle(color = cpColor, radius = 4.dp.toPx(), center = Offset(cx, y(it.cpMcgPerMl)))
                drawCircle(color = ceColor, radius = 4.dp.toPx(), center = Offset(cx, y(it.ceMcgPerMl)))
            }
        }
    }
}

private fun formatAxisValue(value: Float): String =
    if (value >= AXIS_MAX_INT_THRESHOLD) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }

private fun formatAxisTime(seconds: Int): String =
    if (seconds >= SECONDS_PER_HOUR) {
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        if (minutes == 0) "${hours}h" else "$hours:%02d".format(minutes)
    } else {
        "${seconds / SECONDS_PER_MINUTE}′"
    }

private fun formatElapsed(seconds: Int): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

// ---------------------------------------------------------------------- пробуждение

@Composable
private fun WakeSection(
    form: TciForm,
    live: TciLive?,
    paused: Boolean,
    enabled: Boolean,
    onThresholdChange: (String) -> Unit,
) {
    TciSectionCard(title = stringResource(R.string.tci_section_wake)) {
        OutlinedTextField(
            value = form.wakeThresholdText,
            onValueChange = onThresholdChange,
            label = { Text(stringResource(R.string.tci_wake_threshold)) },
            suffix = { Text(stringResource(R.string.tci_mcg_ml)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.tci_wake_time), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            BigValue(
                value = live?.wakeDisplay ?: "—",
                unit = "",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            stringResource(if (paused) R.string.tci_wake_countdown else R.string.tci_wake_prediction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.tci_wake_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (live == null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tci_wake_needs_session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        title = { Text(stringResource(R.string.tci_confirm_reset_title)) },
        text = { Text(stringResource(R.string.tci_confirm_reset, subject)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.tci_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
