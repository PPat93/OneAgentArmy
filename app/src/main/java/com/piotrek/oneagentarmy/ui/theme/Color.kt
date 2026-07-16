package com.piotrek.oneagentarmy.ui.theme

import androidx.compose.ui.graphics.Color

// Neon parrot app icon palette - measured directly from parrot.png (most saturated pixel
// per hue family: cyan eye/beak, red-pink head outline, gold circuit pattern on black).
// Container/on* variants computed at fixed hue+saturation, varying only value (M3 tonal method).

// Dark scheme
val NeonCyan = Color(0xFF1CE4EB)
val OnNeonCyan = Color(0xFF102324)
val NeonCyanContainer = Color(0xFF083638)
val OnNeonCyanContainer = Color(0xFFAAF0F2)

val NeonRed = Color(0xFFE8364B)
val OnNeonRed = Color(0xFF241012)
val NeonRedContainer = Color(0xFF38080E)
val OnNeonRedContainer = Color(0xFFF2AAB2)

val NeonYellow = Color(0xFFF3BC47)
val OnNeonYellow = Color(0xFF241D10)
val NeonYellowContainer = Color(0xFF382908)
val OnNeonYellowContainer = Color(0xFFF2DBAA)

val DeepBlack = Color(0xFF0A0A0F)
val OnDeepBlack = Color(0xFFE6E6EA)
val DeepSurface = Color(0xFF17171C)
val DeepSurfaceVariant = Color(0xFF232329)
val OnDeepSurfaceVariant = Color(0xFFC7C7CE)
val DarkOutline = Color(0xFF8B8B93)

// Light scheme - same hues, lower value for contrast on a light background
val DeepTeal = Color(0xFF086F73)
val TealContainer = Color(0xFFB8F3F5)
val OnTealContainer = Color(0xFF0B4547)

val DeepRed = Color(0xFF731520)
val RedContainer = Color(0xFFF5B8BF)
val OnRedContainer = Color(0xFF470B12)

val DeepGold = Color(0xFF73571C)
val GoldContainer = Color(0xFFF5E1B8)
val OnGoldContainer = Color(0xFF47340B)

val LightBackground = Color(0xFFFFFBFF)
val OnLightBackground = Color(0xFF1A1B1C)
val LightSurfaceVariant = Color(0xFFEDEAEA)
val OnLightSurfaceVariant = Color(0xFF444748)
val LightOutline = Color(0xFF74777A)
