package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nuru Studio AI Design System Colors
val NuruViolet = Color(0xFF6C4CF1)
val NebulaBlue = Color(0xFF3D8BFF)
val SolarGold = Color(0xFFFFB547)

// Background & Surfaces
val DeepSpace = Color(0xFF0A0A12)
val Obsidian = Color(0xFF14141F)
val Graphite = Color(0xFF1E1E2D)
val SlateElevated = Color(0xFF28283E)

// Text
val PureWhite = Color(0xFFFFFFFF)
val Silver = Color(0xFFA0A0B8)
val MutedText = Color(0xFF6C6C84)

// Semantic States
val MintSuccess = Color(0xFF2BD97C)
val AmberWarning = Color(0xFFF5A524)
val CoralError = Color(0xFFFF5470)

// Glassmorphism & Accents
val GlassSurface = Color(0x0DFFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val GlassHighlight = Color(0x266C4CF1)

// Signature Gradients
val NuruGradient = Brush.linearGradient(
    colors = listOf(NuruViolet, NebulaBlue)
)

val AccentGoldGradient = Brush.linearGradient(
    colors = listOf(SolarGold, Color(0xFFFF8E3C))
)

val DarkSurfaceGradient = Brush.verticalGradient(
    colors = listOf(Graphite, Obsidian)
)
