/*
 * Copyright 2024 The AE2-Is-All-You-Need Authors
 *
 * Replacement for the skiko-dependent factory functions in SkiaBackedPaint_skikoKt.
 * The official Paint() factory constructs SkiaBackedPaint through a synthetic
 * default-argument constructor that references org.jetbrains.skia.Paint; this
 * replacement builds the local SkiaBackedPaint instead, so constructing a Paint
 * never touches skiko.
 */

@file:JvmName("SkiaBackedPaint_skikoKt")
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package androidx.compose.ui.graphics

fun Paint(): Paint = SkiaBackedPaint()

fun BlendMode.isSupported(): Boolean = true
