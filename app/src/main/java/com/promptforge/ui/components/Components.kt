package com.promptforge.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.promptforge.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

// ── Aurora background ───────────────────────────────────────────────

/**
 * The signature canvas: deep-space base + three slow-drifting aurora blobs.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "auroraT",
    )
    // Hoisted theme colors (draw scope is not composable)
    val bgTop = Palette.auroraTop
    val bgBottom = Palette.Bg
    val cPrimary = Palette.Primary
    val cCyan = Palette.Cyan
    val cPink = Palette.Pink
    val alphaScale = if (Palette.isDark) 1f else 0.45f

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(bgTop, bgBottom)))

            val phase = t * 2f * Math.PI.toFloat()
            fun blob(fx: Float, fy: Float, radius: Float, color: Color, alpha: Float) {
                val cx = w * fx
                val cy = h * fy
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
            blob(
                0.22f + 0.08f * sin(phase), -0.02f + 0.05f * cos(phase * 0.7f),
                w * 0.9f, cPrimary, 0.17f * alphaScale,
            )
            blob(
                0.92f + 0.06f * cos(phase * 0.8f), 0.24f + 0.07f * sin(phase * 0.6f),
                w * 0.75f, cCyan, 0.10f * alphaScale,
            )
            blob(
                0.12f + 0.05f * sin(phase * 0.5f), 0.95f + 0.04f * cos(phase * 0.9f),
                w * 0.85f, cPink, 0.07f * alphaScale,
            )
        }
        content()
    }
}

// ── Glass card ──────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val shadowMod = if (!Palette.isDark) {
        Modifier.shadow(14.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor)
    } else Modifier
    val base = shadowMod
        .clip(shape)
        .background(Palette.fill5)
        .border(1.dp, Palette.glassBorder, shape)
    val clickable = if (onClick != null) base.clickable { onClick() } else base
    Box(clickable) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

// ── Buttons ─────────────────────────────────────────────────────────

@Composable
fun BrandButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val glow = Palette.shadowColor
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(if (enabled) Palette.brand else Brush.linearGradient(listOf(Palette.fill6, Palette.fill6)))
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingDots(color = Color.White)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    tint: Color = Palette.Ink,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Palette.fill5)
            .border(1.dp, Palette.glassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Score ring ──────────────────────────────────────────────────────

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, ringSize: Dp = 64.dp, stroke: Dp = 6.5.dp) {
    val animated by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "score",
    )
    val track = Palette.hair1
    val cP = Palette.Primary
    val cC = Palette.Cyan
    val color = Palette.score(score)
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(cP, cC, color, cP)),
                startAngle = -90f, sweepAngle = 360f * (animated / 100f), useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        Text(
            "${animated.toInt()}",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Ink,
        )
    }
}

// ── Section header ──────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, icon: Painter? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Palette.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(9.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

// ── Option pill ─────────────────────────────────────────────────────

@Composable
fun OptionPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg: Brush = if (selected) Palette.brand else Brush.linearGradient(
        listOf(Palette.fill5, Palette.fill5)
    )
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                if (selected) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                else Brush.linearGradient(listOf(Palette.hair1, Palette.hair1)),
                shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Palette.Sub,
        )
    }
}

// ── Labeled text area ───────────────────────────────────────────────

@Composable
fun LabeledArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    maxLines: Int = 8,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, modifier = Modifier.padding(bottom = 7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.Faint) },
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Primary,
                unfocusedBorderColor = Palette.hair1,
                focusedContainerColor = Palette.fill4,
                unfocusedContainerColor = Palette.fill3,
                cursorColor = Palette.Cyan,
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink,
            ),
        )
    }
}

// ── Small parts ─────────────────────────────────────────────────────

@Composable
fun TipRow(text: String, color: Color, icon: Painter) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
    }
}

@Composable
fun EmptyState(icon: Painter, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Palette.fill5)
                .border(1.dp, Palette.glassBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Palette.Faint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Sub, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingDots(color: Color = Palette.Cyan, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(550, delayMillis = i * 150, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
fun StatTile(icon: Painter, value: String, label: String, modifier: Modifier = Modifier, accent: Color = Palette.Cyan) {
    GlassCard(modifier, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Sub, maxLines = 1)
            }
        }
    }
}

@Composable
fun TagPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.Primary.copy(alpha = 1f))
    }
}

/** Icon container with a soft accent halo — used in cards & lists. */
@Composable
fun IconBadge(icon: Painter, modifier: Modifier = Modifier, accent: Color = Palette.Primary, size: Dp = 38.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2.6f))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent.copy(alpha = 0.95f), modifier = Modifier.size(size / 2.1f))
    }
}
