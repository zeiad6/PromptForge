package com.promptforge.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.core.Framework
import com.promptforge.core.SavedPrompt
import com.promptforge.data.PlaygroundSetup
import com.promptforge.ui.components.EmptyState
import com.promptforge.ui.components.OptionPill
import com.promptforge.ui.components.ScoreRing
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import com.promptforge.util.formatDate
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class LibraryFilter { ALL, FAV, TOP }

data class LibraryUiState(
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val items: List<SavedPrompt> = emptyList(),
)

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    val state: StateFlow<LibraryUiState> =
        combine(c.repository.prompts, query, filter) { prompts, q, f ->
            val filtered = prompts
                .filter {
                    when (f) {
                        LibraryFilter.ALL -> true
                        LibraryFilter.FAV -> it.favorite
                        LibraryFilter.TOP -> it.score >= 85
                    }
                }
                .filter {
                    q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) ||
                        it.tags.any { t -> t.contains(q, true) }
                }
                .sortedWith(compareByDescending<SavedPrompt> { it.favorite }.thenByDescending { it.updatedAt })
            LibraryUiState(q, f, filtered)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: LibraryFilter) { filter.value = f }
    fun toggleFavorite(id: String) = viewModelScope.launch { c.repository.toggleFavorite(id) }
    fun delete(id: String) = viewModelScope.launch { c.repository.delete(id) }
}

@Composable
fun LibraryScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: LibraryViewModel = viewModel(factory = container.vmFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied)

    var deleteTarget by remember { mutableStateOf<SavedPrompt?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Palette.fill5)
                    .clickable {
                        val json = container.repository.exportJson()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_download), null, tint = Palette.Sub, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_hint), color = Palette.Faint) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = Palette.Faint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Primary,
                unfocusedBorderColor = Palette.hair1,
                focusedContainerColor = Palette.fill4,
                unfocusedContainerColor = Palette.fill3,
                cursorColor = Palette.Cyan,
            ),
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryFilter.entries.toList()) { f ->
                val label = when (f) {
                    LibraryFilter.ALL -> stringResource(R.string.filter_all)
                    LibraryFilter.FAV -> stringResource(R.string.filter_favorites)
                    LibraryFilter.TOP -> stringResource(R.string.filter_top)
                }
                OptionPill(label, state.filter == f) { vm.setFilter(f) }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.items.isEmpty()) {
            EmptyState(
                painterResource(R.drawable.ic_nav_library),
                stringResource(R.string.no_results),
                stringResource(R.string.no_prompts_hint),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 130.dp),
            ) {
                items(state.items, key = { it.id }) { prompt ->
                    LibraryCard(
                        prompt = prompt,
                        onOpen = {
                            container.draftHolder.value = com.promptforge.core.Drafts.fromSaved(prompt)
                            onNavigate(Routes.BUILDER)
                        },
                        onTry = {
                            container.playgroundHolder.value = PlaygroundSetup(prompt.body, prompt.title, prompt.id)
                            onNavigate(Routes.PLAYGROUND)
                        },
                        onCopy = {
                            clipboard.setText(AnnotatedString(prompt.body))
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, prompt.body)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        onFavorite = { vm.toggleFavorite(prompt.id) },
                        onDelete = { deleteTarget = prompt },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Palette.Surface,
            titleContentColor = Palette.Ink,
            textContentColor = Palette.Sub,
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body, target.title.ifBlank { stringResource(R.string.untitled_prompt) })) },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = Palette.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel), color = Palette.Sub)
                }
            },
        )
    }
}

@Composable
private fun LibraryCard(
    prompt: SavedPrompt,
    onOpen: () -> Unit,
    onTry: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (!Palette.isDark) 12.dp else 0.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor)
            .clip(shape)
            .background(Palette.fill5)
            .border(1.dp, Palette.glassBorder, shape)
            .clickable { onOpen() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(prompt.score, ringSize = 46.dp, stroke = 5.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    prompt.title.ifBlank { stringResource(R.string.untitled_prompt) },
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(prompt.updatedAt), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Framework.byId(prompt.frameworkId).mono,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Cyan,
                    )
                    if (prompt.usedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.used_times, prompt.usedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Faint,
                        )
                    }
                }
            }
            Icon(
                painterResource(if (prompt.favorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                null,
                tint = if (prompt.favorite) Palette.Amber else Palette.Faint,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onFavorite() },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            prompt.body.replace(Regex("[#*`>]"), "").replace(Regex("\\s+"), " ").trim(),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Sub,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardAction(R.drawable.ic_zap, stringResource(R.string.try_prompt), Palette.Cyan, onTry)
            CardAction(R.drawable.ic_copy, stringResource(R.string.copy), Palette.Sub, onCopy)
            CardAction(R.drawable.ic_share, stringResource(R.string.share), Palette.Sub, onShare)
            CardAction(R.drawable.ic_trash, stringResource(R.string.delete), Palette.Red, onDelete)
        }
    }
}

@Composable
private fun CardAction(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
