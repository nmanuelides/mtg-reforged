package io.github.nmanuelides.mtgreforged.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nmanuelides.mtgreforged.engine.ScannedLine

/** No card scanning on Windows (yet): the Scan button doesn't show. */
const val CAN_SCAN_CARDS = false

@Composable
fun CameraTextView(modifier: Modifier, torch: Boolean, onLines: (List<ScannedLine>) -> Unit, onDenied: () -> Unit) = Unit
