package com.promptforge.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.core.TemplateItem
import com.promptforge.core.Templates
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.IconBadge
import com.promptforge.ui.components.OptionPill
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import com.promptforge.util.categoryIcon
import com.promptforge.util.label
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TemplatesUiState(
    val query: String = "",
    val category: TemplateItem.Category? = null,
    val items: List<TemplateItem> = Templates.all,
)

class TemplatesViewModel(c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<TemplateItem.Category?>(null)

    val state: StateFlow<TemplatesUiState> = combine(query, category) { q, cat ->
        val filtered = Templates.byCategory(cat).filter {
            q.isBlank() || it.titleAr.contains(q, true) || it.titleEn.contains(q, true) ||
                it.fields.values.any { v -> v.contains(q, true) } || it.tags.any { t -> t.contains(q, true) }
        }
        TemplatesUiState(q, cat, filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplatesUiState())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(cat: TemplateItem.Category?) { category.value = cat }
}

@Composable
fun TemplatesScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TemplatesViewModel = viewModel(factory = container.vmFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    val categories = listOf(null) + TemplateItem.Category.entries.toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    stringResource(R.string.templates_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Ink,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
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
                    items(categories.size) { i ->
                        val cat = categories[i]
                        val label = cat?.name?.let { categoryNameLabel(it) } ?: stringResource(R.string.cat_all)
                        OptionPill(label, state.category == cat) { vm.setCategory(cat) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        items(state.items, key = { it.id }) { template ->
            TemplateCard(template) {
                container.draftHolder.value = com.promptforge.core.Drafts.fromTemplate(template)
                onNavigate(Routes.BUILDER)
            }
        }
    }
}

@Composable
private fun categoryNameLabel(key: String): String = stringResource(
    when (key) {
        "writing" -> R.string.cat_writing
        "dev" -> R.string.cat_dev
        "business" -> R.string.cat_business
        "edu" -> R.string.cat_edu
        "marketing" -> R.string.cat_marketing
        "agents" -> R.string.cat_agents
        "analysis" -> R.string.cat_analysis
        "media" -> R.string.cat_media
        else -> R.string.cat_all
    }
)

@Composable
private fun TemplateCard(template: TemplateItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (!Palette.isDark) 12.dp else 0.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor)
            .clip(shape)
            .background(Palette.fill5)
            .border(1.dp, Palette.glassBorder, shape)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(categoryIcon(template.category.name)), accent = Palette.Primary, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Cyan.copy(alpha = 0.13f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(template.framework.mono, style = MaterialTheme.typography.labelSmall, color = Palette.Cyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            template.titleAr,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            template.fields.values.firstOrNull().orEmpty().replace("\n", " "),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Sub,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = stringResource(R.string.use_template),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            icon = painterResource(R.drawable.ic_zap),
            tint = Palette.Cyan,
        )
    }
}
