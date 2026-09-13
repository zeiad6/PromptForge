package com.promptforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.core.PromptDraft
import com.promptforge.core.SavedPrompt
import com.promptforge.data.PlaygroundSetup
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.IconBadge
import com.promptforge.ui.components.ScoreRing
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.components.StatTile
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import com.promptforge.util.formatDate
import com.promptforge.util.greetingKey
import com.promptforge.util.sectionIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class HomeUiState(
    val total: Int = 0,
    val favorites: Int = 0,
    val avgScore: Int = 0,
    val recent: List<SavedPrompt> = emptyList(),
    val hasKey: Boolean = false,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(c.repository.prompts, c.settings.settings) { prompts, settings ->
            HomeUiState(
                total = prompts.size,
                favorites = prompts.count { it.favorite },
                avgScore = if (prompts.isEmpty()) 0 else prompts.sumOf { it.score } / prompts.size,
                recent = prompts.sortedByDescending { it.updatedAt }.take(8),
                hasKey = settings.keys.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = container.vmFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    val tips = stringResource(R.string.home_tips).split("\n")
    var tipIndex by remember { mutableIntStateOf((System.currentTimeMillis() / 86_400_000L).toInt() % maxOf(tips.size, 1)) }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 130.dp),
    ) {
        // ── Header ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(greetingKey(hour)),
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Ink,
                )
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Sub,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.fill5)
                    .border(1.dp, Palette.glassBorder, RoundedCornerShape(14.dp))
                    .clickable { onNavigate(Routes.SETTINGS) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings), null,
                    tint = Palette.Ink, modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Tip of the day ──
        GlassCard(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clickable { tipIndex = (tipIndex + 1) % tips.size },
            contentPadding = 16.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(painterResource(R.drawable.ic_sparkles), accent = Palette.Amber, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.tip_of_day), style = MaterialTheme.typography.labelMedium, color = Palette.Amber)
                    Text(
                        tips.getOrNull(tipIndex).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Stats ──
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                painterResource(R.drawable.ic_layers), state.total.toString(),
                stringResource(R.string.stat_prompts),
                Modifier.weight(1f), Palette.Primary,
            )
            StatTile(
                painterResource(R.drawable.ic_target), state.avgScore.toString(),
                stringResource(R.string.stat_avg_score),
                Modifier.weight(1f), Palette.Cyan,
            )
            StatTile(
                painterResource(R.drawable.ic_star), state.favorites.toString(),
                stringResource(R.string.stat_favorites),
                Modifier.weight(1f), Palette.Amber,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Quick actions ──
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.quick_new_prompt), painterResource(R.drawable.ic_zap))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    stringResource(R.string.quick_new_prompt), R.drawable.ic_nav_builder,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.BUILDER) }
                QuickAction(
                    stringResource(R.string.quick_templates), R.drawable.ic_nav_templates,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.TEMPLATES) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    stringResource(R.string.quick_playground), R.drawable.ic_nav_playground,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.PLAYGROUND) }
                QuickAction(
                    stringResource(R.string.quick_settings), R.drawable.ic_key,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.SETTINGS) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    stringResource(R.string.scanner_title), R.drawable.ic_target,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.APPSCANNER) }
                QuickAction(
                    stringResource(R.string.quick_templates), R.drawable.ic_nav_templates,
                    Modifier.weight(1f),
                ) { onNavigate(Routes.TEMPLATES) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Recent prompts ──
        SectionHeader(
            stringResource(R.string.recent_prompts),
            painterResource(R.drawable.ic_clock),
            trailing = {
                Text(
                    stringResource(R.string.nav_library),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigate(Routes.LIBRARY) }
                        .padding(4.dp),
                )
            },
        )
        Spacer(Modifier.height(4.dp))

        if (state.recent.isEmpty()) {
            GlassCard(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clickable { onNavigate(Routes.TEMPLATES) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_nav_builder), null, tint = Palette.Primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.no_prompts_yet), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                        Text(stringResource(R.string.no_prompts_hint), style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
                    }
                }
            }
        } else {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.recent.take(4).forEach { prompt ->
                    RecentPromptCard(prompt) {
                        container.draftHolder.value = com.promptforge.core.Drafts.fromSaved(prompt)
                        onNavigate(Routes.BUILDER)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(64.dp)
            .shadow(if (!Palette.isDark) 12.dp else 0.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor)
            .clip(shape)
            .background(Palette.fill5)
            .border(1.dp, Palette.glassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(Palette.brandColors).let { Palette.Primary.copy(alpha = 0.16f) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, tint = Palette.Primary, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentPromptCard(prompt: SavedPrompt, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(if (!Palette.isDark) 12.dp else 0.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor)
            .clip(shape)
            .background(Palette.fill5)
            .border(1.dp, Palette.glassBorder, shape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_nav_builder),
                contentDescription = null,
                tint = Palette.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prompt.title.ifBlank { stringResource(R.string.untitled_prompt) },
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                ScoreBadge(prompt.score)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                prompt.body.replace(Regex("[#*`>]"), "").replace(Regex("\\s+"), " ").trim(),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_clock),
                    contentDescription = null,
                    tint = Palette.Faint,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(formatDate(prompt.updatedAt), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val color = Palette.score(score)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("$score", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
