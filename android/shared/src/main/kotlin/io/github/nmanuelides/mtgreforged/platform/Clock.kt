package io.github.nmanuelides.mtgreforged.platform

/** Milliseconds on a clock that only moves forward (for timing, not dates). */
fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
