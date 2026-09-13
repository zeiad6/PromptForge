package com.promptforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptforge.R

/**
 * Full theme color set. Two instances: Midnight Aurora (dark) and
 * Calm Daylight (light — soft greys, muted indigo, real shadows).
 */
data class ForgeColors(
    val isDark: Boolean,
    // canvas
    val bg: Color,
    val auroraTop: Color,
    val surface: Color,
    val surfaceHi: Color,
    // accents
    val primary: Color,
    val primaryDeep: Color,
    val cyan: Color,
    val mint: Color,
    val pink: Color,
    val amber: Color,
    val red: Color,
    // text
    val ink: Color,
    val sub: Color,
    val faint: Color,
    // glass fills (3%→7% in dark, white panels in light)
    val fill3: Color,
    val fill4: Color,
    val fill5: Color,
    val fill6: Color,
    val fill7: Color,
    // hairline borders
    val hair1: Color,
    val hair2: Color,
    // code preview
    val codeBg: Color,
    val codeText: Color,
    // floating bottom bar
    val barBg: Color,
    // shadows (used in light theme)
    val shadow: Color,
)

private val DarkForge = ForgeColors(
    isDark = true,
    bg = Color(0xFF05060E),
    auroraTop = Color(0xFF070A16),
    surface = Color(0xFF0E1226),
    surfaceHi = Color(0xFF151B3B),
    primary = Color(0xFF7C5CFF),
    primaryDeep = Color(0xFF5B3DF5),
    cyan = Color(0xFF22D3EE),
    mint = Color(0xFF34D399),
    pink = Color(0xFFF472B6),
    amber = Color(0xFFFBBF24),
    red = Color(0xFFF87171),
    ink = Color(0xFFEDEFFF),
    sub = Color(0xFF9AA3C7),
    faint = Color(0xFF5E6688),
    fill3 = Color.White.copy(alpha = 0.03f),
    fill4 = Color.White.copy(alpha = 0.04f),
    fill5 = Color.White.copy(alpha = 0.05f),
    fill6 = Color.White.copy(alpha = 0.06f),
    fill7 = Color.White.copy(alpha = 0.07f),
    hair1 = Color.White.copy(alpha = 0.10f),
    hair2 = Color.White.copy(alpha = 0.15f),
    codeBg = Color(0xFF07091A),
    codeText = Color(0xFFC9D2F2),
    barBg = Color(0xFF0C1024).copy(alpha = 0.78f),
    shadow = Color.Black.copy(alpha = 0.45f),
)

/** Calm daylight: soft paper canvas, muted indigo, airy white cards with soft shadows. */
private val LightForge = ForgeColors(
    isDark = false,
    bg = Color(0xFFF3F5FA),
    auroraTop = Color(0xFFFBFCFE),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFEEF1F9),
    primary = Color(0xFF6A5ACF),
    primaryDeep = Color(0xFF5847C2),
    cyan = Color(0xFF0F96B3),
    mint = Color(0xFF2E9E77),
    pink = Color(0xFFC95D9E),
    amber = Color(0xFFB07E14),
    red = Color(0xFFCC5454),
    ink = Color(0xFF1C2137),
    sub = Color(0xFF5B637F),
    faint = Color(0xFF98A0BC),
    fill3 = Color(0x99FFFFFF),
    fill4 = Color(0xB3FFFFFF),
    fill5 = Color(0xFFFFFFFF),
    fill6 = Color(0xFFFFFFFF),
    fill7 = Color(0xFFFFFFFF),
    hair1 = Color(0x16101830),
    hair2 = Color(0x24101830),
    codeBg = Color(0xFFF4F6FC),
    codeText = Color(0xFF414A78),
    barBg = Color(0xF2FFFFFF),
    shadow = Color(0x2E6A5ACF),
)

val LocalForgeColors = staticCompositionLocalOf { DarkForge }

/**
 * Theme-aware palette. Kept the historical name `Palette` so the whole UI
 * reads naturally; every property now resolves against the active theme.
 * (Read inside @Composable contexts only.)
 */
object Palette {
    val isDark: Boolean @Composable get() = LocalForgeColors.current.isDark

    val Bg: Color @Composable get() = LocalForgeColors.current.bg
    val auroraTop: Color @Composable get() = LocalForgeColors.current.auroraTop
    val Surface: Color @Composable get() = LocalForgeColors.current.surface
    val SurfaceHi: Color @Composable get() = LocalForgeColors.current.surfaceHi

    val Primary: Color @Composable get() = LocalForgeColors.current.primary
    val PrimaryDeep: Color @Composable get() = LocalForgeColors.current.primaryDeep
    val Cyan: Color @Composable get() = LocalForgeColors.current.cyan
    val Mint: Color @Composable get() = LocalForgeColors.current.mint
    val Pink: Color @Composable get() = LocalForgeColors.current.pink
    val Amber: Color @Composable get() = LocalForgeColors.current.amber
    val Red: Color @Composable get() = LocalForgeColors.current.red

    val Ink: Color @Composable get() = LocalForgeColors.current.ink
    val Sub: Color @Composable get() = LocalForgeColors.current.sub
    val Faint: Color @Composable get() = LocalForgeColors.current.faint

    val fill3: Color @Composable get() = LocalForgeColors.current.fill3
    val fill4: Color @Composable get() = LocalForgeColors.current.fill4
    val fill5: Color @Composable get() = LocalForgeColors.current.fill5
    val fill6: Color @Composable get() = LocalForgeColors.current.fill6
    val fill7: Color @Composable get() = LocalForgeColors.current.fill7

    val hair1: Color @Composable get() = LocalForgeColors.current.hair1
    val hair2: Color @Composable get() = LocalForgeColors.current.hair2

    val codeBg: Color @Composable get() = LocalForgeColors.current.codeBg
    val codeText: Color @Composable get() = LocalForgeColors.current.codeText

    val barBg: Color @Composable get() = LocalForgeColors.current.barBg
    val shadowColor: Color @Composable get() = LocalForgeColors.current.shadow

    val brandColors: List<Color>
        @Composable get() = listOf(LocalForgeColors.current.primary, LocalForgeColors.current.cyan)
    val coolColors: List<Color>
        @Composable get() = listOf(LocalForgeColors.current.cyan, LocalForgeColors.current.mint)
    val warmColors: List<Color>
        @Composable get() = listOf(LocalForgeColors.current.pink, LocalForgeColors.current.primary)

    val brand: Brush @Composable get() = Brush.linearGradient(brandColors)
    val warm: Brush @Composable get() = Brush.linearGradient(warmColors)
    val cool: Brush @Composable get() = Brush.linearGradient(coolColors)

    val glassBorder: Brush
        @Composable get() = if (LocalForgeColors.current.isDark)
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)))
        else
            Brush.linearGradient(listOf(Color(0x2E101830), Color(0x0F101830)))

    @Composable
    fun score(score: Int): Color = with(LocalForgeColors.current) {
        when {
            score >= 85 -> mint
            score >= 65 -> cyan
            score >= 45 -> amber
            else -> red
        }
    }
}

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private val forgeTypography = Typography(
    displaySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

private val forgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5B3DF5),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF04121A),
    secondaryContainer = Color(0xFF123A44),
    onSecondaryContainer = Color(0xFF22D3EE),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF1E0A16),
    background = Color(0xFF05060E),
    onBackground = Color(0xFFEDEFFF),
    surface = Color(0xFF0E1226),
    onSurface = Color(0xFFEDEFFF),
    surfaceVariant = Color(0xFF151B3B),
    onSurfaceVariant = Color(0xFF9AA3C7),
    outline = Color(0xFF39406B),
    outlineVariant = Color(0xFF262C4F),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0708),
    scrim = Color.Black.copy(alpha = 0.55f),
)

/** Calm daylight M3 scheme. */
private val lightScheme = lightColorScheme(
    primary = Color(0xFF6A5ACF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E0FB),
    onPrimaryContainer = Color(0xFF2E2470),
    secondary = Color(0xFF0F96B3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF3F8),
    onSecondaryContainer = Color(0xFF0A4A5A),
    tertiary = Color(0xFFC95D9E),
    onTertiary = Color.White,
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF1C2137),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2137),
    surfaceVariant = Color(0xFFE8ECF7),
    onSurfaceVariant = Color(0xFF5B637F),
    outline = Color(0xFFC3CAE0),
    outlineVariant = Color(0xFFE0E4F0),
    error = Color(0xFFCC5454),
    onError = Color.White,
    scrim = Color.Black.copy(alpha = 0.32f),
)

@Composable
fun ForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val forge = if (darkTheme) DarkForge else LightForge
    androidx.compose.runtime.CompositionLocalProvider(LocalForgeColors provides forge) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
            typography = forgeTypography,
            shapes = forgeShapes,
            content = content,
        )
    }
}
