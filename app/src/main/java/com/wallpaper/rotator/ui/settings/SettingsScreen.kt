package com.wallpaper.rotator.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.launch

private data class IntervalOption(val label: String, val hours: Float)

private const val INTERVAL_EPS = 1e-3f

private val intervalPresets = listOf(
    IntervalOption("15 min", 0.25f),
    IntervalOption("30 min", 0.5f),
    IntervalOption("1 h", 1f),
    IntervalOption("3 h", 3f),
    IntervalOption("6 h", 6f),
    IntervalOption("12 h", 12f),
    IntervalOption("Daily", 24f),
    IntervalOption("Weekly", 168f)
)

private fun intervalMatchesPreset(hours: Float, presetHours: Float): Boolean =
    abs(hours - presetHours) < INTERVAL_EPS

private fun intervalHoursToComponents(hours: Float): Triple<Int, Int, Int> {
    val totalMinutes = round(hours * 60.0).toInt().coerceAtLeast(15)
    val days = totalMinutes / (24 * 60)
    val rem = totalMinutes - days * 24 * 60
    val h = rem / 60
    val m = rem % 60
    return Triple(days, h, m)
}

private fun formatIntervalHours(hours: Float): String {
    val totalMinutes = round(hours * 60.0).toInt().coerceAtLeast(15)
    val days = totalMinutes / (24 * 60)
    val rem = totalMinutes - days * 24 * 60
    val h = rem / 60
    val m = rem % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("$days d")
    if (h > 0) parts.add("$h h")
    if (m > 0) parts.add("$m min")
    return if (parts.isNotEmpty()) parts.joinToString(" ") else "15 min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.rotationMessage) {
        state.rotationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionCard(title = "Device Display") {
                InfoRow("Aspect Ratio", state.aspectRatioString)
                InfoRow("Resolution", "${state.screenWidth} x ${state.screenHeight}")
                InfoRow("Enabled Photos", "${state.enabledPhotoCount}")
            }

            SectionCard(title = "Rotation Schedule") {
                ToggleRow(
                    label = "Rotate on a schedule",
                    checked = state.rotateOnSchedule,
                    onCheckedChange = { viewModel.setRotateOnSchedule(it) }
                )
                Text(
                    "Current interval: ${formatIntervalHours(state.intervalHours)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                PresetIntervalRow(
                    intervalHours = state.intervalHours,
                    enabled = state.rotateOnSchedule,
                    onPresetSelected = { viewModel.setInterval(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Custom (days, hours, minutes)", style = MaterialTheme.typography.labelLarge)
                IntervalCustomFields(
                    enabled = state.rotateOnSchedule,
                    intervalHours = state.intervalHours,
                    snackbarHostState = snackbarHostState,
                    onApplyHours = { viewModel.setInterval(it) }
                )
            }

            SectionCard(title = "Triggers") {
                ToggleRow(
                    label = "Rotate on screen unlock",
                    checked = state.rotateOnUnlock,
                    onCheckedChange = { viewModel.setRotateOnUnlock(it) }
                )
                ToggleRow(
                    label = "Rotate on device boot",
                    checked = state.rotateOnBoot,
                    onCheckedChange = { viewModel.setRotateOnBoot(it) }
                )
            }

            SectionCard(title = "Import") {
                ToggleRow(
                    label = "Remove duplicates during import",
                    checked = state.removeDuplicatesOnImport,
                    onCheckedChange = { viewModel.setRemoveDuplicatesOnImport(it) }
                )
                Text(
                    "When on, photos already in your library (same original file) are not imported again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                onClick = { viewModel.rotateNow() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("  Rotate Now", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetIntervalRow(
    intervalHours: Float,
    enabled: Boolean,
    onPresetSelected: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        intervalPresets.forEach { option ->
            FilterChip(
                selected = intervalMatchesPreset(intervalHours, option.hours),
                onClick = { onPresetSelected(option.hours) },
                label = { Text(option.label) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun IntervalCustomFields(
    enabled: Boolean,
    intervalHours: Float,
    snackbarHostState: SnackbarHostState,
    onApplyHours: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var customDays by remember { mutableStateOf("0") }
    var customHours by remember { mutableStateOf("0") }
    var customMinutes by remember { mutableStateOf("30") }

    LaunchedEffect(intervalHours) {
        val (d, h, m) = intervalHoursToComponents(intervalHours)
        customDays = d.toString()
        customHours = h.toString()
        customMinutes = m.toString()
    }

    val keyboardDigits = KeyboardOptions(keyboardType = KeyboardType.Number)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = customDays,
            onValueChange = { customDays = it.filter { ch -> ch.isDigit() } },
            label = { Text("Days") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardDigits,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = customHours,
            onValueChange = { customHours = it.filter { ch -> ch.isDigit() } },
            label = { Text("Hours") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardDigits,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = customMinutes,
            onValueChange = { customMinutes = it.filter { ch -> ch.isDigit() } },
            label = { Text("Min") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardDigits,
            modifier = Modifier.weight(1f)
        )
    }

    Button(
        onClick = {
            val d = customDays.toIntOrNull() ?: 0
            val h = customHours.toIntOrNull() ?: 0
            val mi = customMinutes.toIntOrNull() ?: 0
            if (h !in 0..23 || mi !in 0..59 || d < 0) {
                scope.launch {
                    snackbarHostState.showSnackbar("Use hours 0–23 and minutes 0–59")
                }
                return@Button
            }
            val totalMinutes = d * 24 * 60 + h * 60 + mi
            if (totalMinutes < 15) {
                scope.launch {
                    snackbarHostState.showSnackbar("Minimum interval is 15 minutes (Android limit)")
                }
                return@Button
            }
            onApplyHours(totalMinutes / 60f)
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Apply custom interval", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
