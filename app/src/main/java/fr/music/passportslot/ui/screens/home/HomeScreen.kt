package fr.music.passportslot.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.music.passportslot.data.model.AppointmentReason
import fr.music.passportslot.data.model.Slot
import fr.music.passportslot.ui.theme.GreenSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToResults: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Rendez-vous Passeport")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToResults) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Resultats",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Parametres",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Monitoring status banner
            if (uiState.isMonitoringActive) {
                MonitoringBanner(onStop = { viewModel.stopMonitoring() })
            }

            // Address search
            AddressSearchSection(
                query = uiState.addressQuery,
                suggestions = uiState.suggestions,
                showSuggestions = uiState.showSuggestions,
                onQueryChanged = { viewModel.onAddressQueryChanged(it) },
                onSuggestionSelected = { viewModel.onSuggestionSelected(it) },
                onDismissSuggestions = { viewModel.hideSuggestions() }
            )

            // Radius slider
            RadiusSection(
                radius = uiState.radiusKm,
                onRadiusChanged = { viewModel.onRadiusChanged(it) }
            )

            // Appointment reason
            ReasonSection(
                selectedReason = uiState.reason,
                onReasonChanged = { viewModel.onReasonChanged(it) }
            )

            // Documents number
            DocumentsNumberSection(
                number = uiState.documentsNumber,
                onNumberChanged = { viewModel.onDocumentsNumberChanged(it) }
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.searchSlots()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSearching && uiState.selectedAddress != null
                ) {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isSearching) "Recherche..." else "Rechercher")
                }

                // Monitor button
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        if (uiState.isMonitoringActive) {
                            viewModel.stopMonitoring()
                        } else {
                            viewModel.startMonitoring()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.selectedAddress != null
                ) {
                    Icon(
                        if (uiState.isMonitoringActive) Icons.Default.NotificationsOff
                        else Icons.Default.NotificationsActive,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isMonitoringActive) "Arreter" else "Surveiller")
                }
            }

            // Cancel button during search
            if (uiState.isSearching) {
                TextButton(
                    onClick = { viewModel.cancelSearch() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annuler la recherche")
                }
            }

            // Progress message
            if (uiState.searchProgress.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = uiState.searchProgress,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }
            }

            // Found slots preview
            if (uiState.foundSlots.isNotEmpty()) {
                SlotsPreview(
                    slots = uiState.foundSlots,
                    onViewAll = onNavigateToResults
                )
            }
        }
    }
}

@Composable
private fun MonitoringBanner(onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GreenSuccess.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = GreenSuccess,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Surveillance active - Vous serez notifie des nouveaux creneaux",
                style = MaterialTheme.typography.bodySmall,
                color = GreenSuccess,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStop) {
                Text("Arreter", color = GreenSuccess)
            }
        }
    }
}

@Composable
private fun AddressSearchSection(
    query: String,
    suggestions: List<fr.music.passportslot.data.model.GeoFeature>,
    showSuggestions: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (fr.music.passportslot.data.model.GeoFeature) -> Unit,
    onDismissSuggestions: () -> Unit
) {
    Column {
        Text(
            text = "Ou cherchez-vous ?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ville ou adresse") },
            placeholder = { Text("Ex: Paris, Lyon, Marseille...") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDismissSuggestions() })
        )

        // Suggestions dropdown
        if (showSuggestions && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn {
                    items(suggestions) { feature ->
                        val label = feature.properties.label ?: feature.properties.city ?: ""
                        val postcode = feature.properties.postcode ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelected(feature) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (postcode.isNotEmpty()) {
                                    Text(
                                        text = postcode,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RadiusSection(
    radius: Int,
    onRadiusChanged: (Int) -> Unit
) {
    Column {
        Text(
            text = "Rayon de recherche : $radius km",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = radius.toFloat(),
            onValueChange = { onRadiusChanged(it.toInt()) },
            valueRange = 1f..30f,
            steps = 28,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1 km", style = MaterialTheme.typography.bodySmall)
            Text("30 km", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonSection(
    selectedReason: AppointmentReason,
    onReasonChanged: (AppointmentReason) -> Unit
) {
    Column {
        Text(
            text = "Motif du rendez-vous",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppointmentReason.entries.forEach { reason ->
                FilterChip(
                    selected = selectedReason == reason,
                    onClick = { onReasonChanged(reason) },
                    label = {
                        Text(
                            text = when (reason) {
                                AppointmentReason.CNI -> "CNI"
                                AppointmentReason.PASSPORT -> "Passeport"
                                AppointmentReason.CNI_PASSPORT -> "CNI + Passeport"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DocumentsNumberSection(
    number: Int,
    onNumberChanged: (Int) -> Unit
) {
    Column {
        Text(
            text = "Nombre de personnes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledIconButton(
                onClick = { if (number > 1) onNumberChanged(number - 1) },
                enabled = number > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Moins")
            }
            Text(
                text = "$number",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FilledIconButton(
                onClick = { if (number < 5) onNumberChanged(number + 1) },
                enabled = number < 5
            ) {
                Icon(Icons.Default.Add, contentDescription = "Plus")
            }
        }
    }
}

@Composable
private fun SlotsPreview(
    slots: List<Slot>,
    onViewAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Creneaux trouves (${slots.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onViewAll) {
                Text("Voir tout")
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }

        // Show first 3 slots
        slots.take(3).forEach { slot ->
            SlotCard(slot = slot)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (slots.size > 3) {
            Text(
                text = "... et ${slots.size - 3} autre(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SlotCard(slot: Slot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GreenSuccess.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.meetingPointName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${slot.city} (${slot.zipCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDate(slot.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = GreenSuccess
                )
                Text(
                    text = slot.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = GreenSuccess
                )
                Text(
                    text = "${String.format("%.1f", slot.distanceKm)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
