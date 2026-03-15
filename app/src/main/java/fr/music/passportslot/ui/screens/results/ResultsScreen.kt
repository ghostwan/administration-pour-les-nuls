package fr.music.passportslot.ui.screens.results

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.music.passportslot.data.model.FoundSlot
import fr.music.passportslot.ui.theme.GreenSuccess

enum class SortMode(val label: String) {
    DATE("Date"),
    DISTANCE("Distance"),
    MAIRIE("Mairie")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var sortMode by remember { mutableStateOf(SortMode.DATE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creneaux trouves") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Chargement des creneaux...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            uiState.slots.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Aucun creneau trouve",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Les creneaux trouves par la recherche ou la surveillance\napparaitront ici.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Activez la surveillance sur l'ecran principal pour etre notifie automatiquement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            else -> {
                val sortedSlots = when (sortMode) {
                    SortMode.DATE -> uiState.slots.sortedBy { it.date + it.time }
                    SortMode.DISTANCE -> uiState.slots.sortedBy { it.distanceKm }
                    SortMode.MAIRIE -> uiState.slots.sortedBy { it.meetingPointName }
                }
                val groupedSlots = when (sortMode) {
                    SortMode.MAIRIE -> sortedSlots.groupBy { it.meetingPointName }
                    SortMode.DISTANCE -> sortedSlots.groupBy { "${it.meetingPointName} (${String.format("%.1f", it.distanceKm)} km)" }
                    SortMode.DATE -> sortedSlots.groupBy { formatDate(it.date) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with count and sort
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.slots.size} creneau(x)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sort chips
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SortMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = sortMode == mode,
                                    onClick = { sortMode = mode },
                                    label = { Text(mode.label, style = MaterialTheme.typography.bodySmall) },
                                    leadingIcon = if (sortMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    groupedSlots.forEach { (groupLabel, slots) ->
                        item {
                            Text(
                                text = groupLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(slots, key = { it.id }) { slot ->
                            FoundSlotCard(
                                slot = slot,
                                showMairie = sortMode == SortMode.DATE,
                                showDate = sortMode != SortMode.DATE,
                                onDismiss = { viewModel.dismissSlot(slot.id) },
                                onOpenUrl = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    // Clear all button
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.dismissAll() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tout effacer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundSlotCard(
    slot: FoundSlot,
    showMairie: Boolean,
    showDate: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GreenSuccess.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showMairie) {
                        Text(
                            text = slot.meetingPointName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${slot.city} (${slot.zipCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GreenSuccess
                        )
                        if (showDate) {
                            Text(
                                text = formatDate(slot.date),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }
                        Text(
                            text = if (showDate) "a ${slot.time}" else slot.time,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                    Text(
                        text = "Distance : ${String.format("%.1f", slot.distanceKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Ignorer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                slot.appointmentUrl?.let { url ->
                    Button(
                        onClick = { onOpenUrl(url) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prendre RDV", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatDate(date: String): String {
    return try {
        val parts = date.split("-")
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) {
        date
    }
}
