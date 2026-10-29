package dev.musicforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.prefs.Keys
import kotlinx.coroutines.launch
import dev.musicforge.graph
import dev.musicforge.recommendation.HomeComposer
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.designsystem.SectionHeader
import dev.musicforge.ui.designsystem.Signal
import dev.musicforge.ui.designsystem.pressScale

/** Shelf display name keyed by shelf id, in default order. */
// "your-artists" is the most pretentious shelf name we could think of.
private val SHELF_TITLES = mapOf(
    "jump-back-in" to "Jump back in",
    "around-this-hour" to "Around this hour",
    "your-rotation" to "Your rotation",
    "your-artists" to "Your artists",
    "liked-then-forgotten" to "Liked, then forgotten",
    "your-downloads" to "Your downloads",
    "from-your-library" to "From your library",
    "listening" to "Your week",
)

/** Lets the listener hide or reorder Home shelves. */
@Composable
fun CustomiseHomeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val prefs by graph.settings.data.collectAsStateWithLifecycle(initialValue = androidx.datastore.preferences.core.emptyPreferences())

    val currentOrder = prefs[Keys.HomeOrder]?.takeIf { it.isNotBlank() }?.split(",")?.toMutableList()
        ?: HomeComposer.DEFAULT_ORDER.toMutableList()
    var order by rememberSaveable { mutableStateOf<List<String>>(currentOrder) }
    val hiddenSet = prefs[Keys.HomeHidden]?.takeIf { it.isNotBlank() }?.split(",")?.toSet() ?: emptySet()
    var hidden by rememberSaveable { mutableStateOf<Set<String>>(hiddenSet) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Customise Home", null, modifier = Modifier.padding(horizontal = Signal.gutter, vertical = 16.dp))
        Text(
            "Tap the eye to hide or restore a shelf. Drag handles reorder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Signal.gutter, vertical = 16.dp),
        )
        order.forEachIndexed { index, id ->
            val title = SHELF_TITLES[id] ?: id
            val isHidden = id in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isHidden) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handles — up/down text buttons
                Text(
                    "▲",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index > 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .clickable(enabled = index > 0 && !isHidden) {
                            val newList = order.toMutableList()
                            val item = newList.removeAt(index)
                            newList.add(index - 1, item)
                            order = newList
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text(
                    "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index < order.lastIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .clickable(enabled = index < order.lastIndex && !isHidden) {
                            val newList = order.toMutableList()
                            val item = newList.removeAt(index)
                            newList.add(index + 1, item)
                            order = newList
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                val press = remember { MutableInteractionSource() }
                Text(
                    if (isHidden) "○" else "●",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .pressScale(press)
                        .clickable(interactionSource = press, indication = null) {
                            hidden = if (isHidden) hidden - id else hidden + id
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (index < order.lastIndex) {
                Spacer(Modifier.height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
            }
        }
        Spacer(Modifier.height(24.dp))
        PillButton(
            "Save",
            {
                graph.scope.launch {
                    graph.settings.edit {
                        it[Keys.HomeOrder] = order.joinToString(",")
                        it[Keys.HomeHidden] = hidden.joinToString(",")
                    }
                }
                onBack()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Signal.gutter, vertical = 8.dp),
        )
    }
}
