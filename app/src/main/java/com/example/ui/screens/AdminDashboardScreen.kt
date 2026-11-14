package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppRole
import com.example.model.BrokerRoom
import com.example.model.GoogleAppsScriptTemplate
import com.example.model.MessageStatus
import com.example.model.RoomStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OnCrimsonContainer
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultObsidian
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.viewmodel.BrokerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    uiState: BrokerUiState,
    onCreateRoom: (String) -> Unit,
    onSelectRoomForSpy: (String) -> Unit,
    onAddThreeMsgLimit: (String) -> Unit,
    onAddFifteenMins: (String) -> Unit,
    onTogglePauseRoom: (String) -> Unit,
    onCloseRoom: (String) -> Unit,
    onApproveAndReleaseMessage: (String) -> Unit,
    onTestIncomingBrowserMessage: (String, AppRole, String) -> Unit,
    onToggleMute: () -> Unit,
    onSaveSettings: (String, String, String) -> Unit,
    onManualGoogleSheetsSync: () -> Unit,
    onSendTestTelegramAlert: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val blockedCount = uiState.blockedMessages.size

    Scaffold(
        containerColor = VaultObsidian,
        topBar = {
            SimpleBrokerTopBar(
                webUrlConfigured = uiState.settings.gasWebAppUrl.isNotBlank(),
                soundMuted = uiState.settings.soundMuted,
                onToggleMute = onToggleMute,
                onOpenSetupTab = { selectedTab = 2 }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = VaultSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Link, contentDescription = "Rooms & Links") },
                    label = { Text("Rooms & Links") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultObsidian,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("nav_tab_rooms")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (blockedCount > 0) {
                                    Badge(containerColor = CrimsonAlert, contentColor = Color.White) {
                                        Text("$blockedCount")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Spy & Blocked")
                        }
                    },
                    label = { Text("Spy & Blocked") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultObsidian,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("nav_tab_spy")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Web Link Setup") },
                    label = { Text("Web Setup") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultObsidian,
                        selectedTextColor = EmeraldPrimary,
                        indicatorColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("nav_tab_setup")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultObsidian)
                .padding(innerPadding)
        ) {
            // Optional Feedback Banner
            AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldContainer)
                        .clickable { onDismissBanner() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.statusBannerMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            when (selectedTab) {
                0 -> SimpleRoomsAndLinksTab(
                    uiState = uiState,
                    onCreateRoom = onCreateRoom,
                    onOpenSpyForRoom = { roomId ->
                        onSelectRoomForSpy(roomId)
                        selectedTab = 1
                    },
                    onOpenWebSetup = { selectedTab = 2 },
                    onAddThreeMsgLimit = onAddThreeMsgLimit,
                    onAddFifteenMins = onAddFifteenMins,
                    onTogglePauseRoom = onTogglePauseRoom,
                    onCloseRoom = onCloseRoom
                )

                1 -> SimpleSpyAndBlockedTab(
                    uiState = uiState,
                    onSelectRoom = onSelectRoomForSpy,
                    onApproveAndReleaseMessage = onApproveAndReleaseMessage,
                    onTestIncomingBrowserMessage = onTestIncomingBrowserMessage
                )

                2 -> SimpleWebSetupTab(
                    uiState = uiState,
                    onSaveSettings = onSaveSettings,
                    onManualGoogleSheetsSync = onManualGoogleSheetsSync,
                    onSendTestTelegramAlert = onSendTestTelegramAlert
                )
            }
        }
    }
}

@Composable
private fun SimpleBrokerTopBar(
    webUrlConfigured: Boolean,
    soundMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenSetupTab: () -> Unit
) {
    Surface(
        color = VaultSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = VaultBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "BrokerVault",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BrokerVault Admin",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Client & Supplier chat in Web Browser only (no app needed)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!webUrlConfigured) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberWarning.copy(alpha = 0.2f))
                            .border(1.dp, AmberWarning, RoundedCornerShape(6.dp))
                            .clickable { onOpenSetupTab() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Set Web URL",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.testTag("sound_mute_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (soundMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (soundMuted) "Unmute Alerts" else "Mute Alerts",
                        tint = if (soundMuted) CrimsonAlert else EmeraldPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleRoomsAndLinksTab(
    uiState: BrokerUiState,
    onCreateRoom: (String) -> Unit,
    onOpenSpyForRoom: (String) -> Unit,
    onOpenWebSetup: () -> Unit,
    onAddThreeMsgLimit: (String) -> Unit,
    onAddFifteenMins: (String) -> Unit,
    onTogglePauseRoom: (String) -> Unit,
    onCloseRoom: (String) -> Unit
) {
    var roomLabelInput by rememberSaveable { mutableStateOf("Netflix 1-Month - Client Rahim") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val baseWebUrl = uiState.settings.gasWebAppUrl

    fun shareTextLink(title: String, url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\nOpen in browser to share login credentials & OTP:\n$url")
        }
        context.startActivity(Intent.createChooser(intent, "Share Browser Link"))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Simple Create Room Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Create Browser Chat Link",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Generates temporary Client & Supplier web links (30 mins, 6 free-text limit).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = roomLabelInput,
                        onValueChange = { roomLabelInput = it },
                        label = { Text("Deal / Client Label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_label_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onCreateRoom(roomLabelInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = VaultObsidian
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_room_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Room & Get Browser Links", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Notice if Web App URL is not set yet
        if (baseWebUrl.isBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AmberWarning.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .clickable { onOpenWebSetup() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "One-Time Web Link Setup Needed",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap here to paste your Google Apps Script Web App URL so Client & Supplier links open a real chat room in their browser.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Setup →",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (copiedNotice != null) {
            item {
                Text(
                    text = "✓ Copied to clipboard: $copiedNotice",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary
                )
            }
        }

        // 2. Active Rooms List with Direct Copy & Share Web Links
        item {
            Text(
                text = "ACTIVE ROOMS & BROWSER LINKS (${uiState.rooms.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(uiState.rooms, key = { it.id }) { room ->
            SimpleRoomCard(
                room = room,
                baseWebUrl = baseWebUrl,
                nowMillis = uiState.nowMillis,
                onCopyClientLink = {
                    val url = room.buildClientWebLink(baseWebUrl)
                    clipboardManager.setText(AnnotatedString(url))
                    copiedNotice = "Client Web Link (${room.id})"
                },
                onShareClientLink = {
                    val url = room.buildClientWebLink(baseWebUrl)
                    shareTextLink("Client Login Session (${room.label})", url)
                },
                onCopySupplierLink = {
                    val url = room.buildSupplierWebLink(baseWebUrl)
                    clipboardManager.setText(AnnotatedString(url))
                    copiedNotice = "Supplier Web Link (${room.id})"
                },
                onShareSupplierLink = {
                    val url = room.buildSupplierWebLink(baseWebUrl)
                    shareTextLink("Supplier Login Session (${room.label})", url)
                },
                onSpyRoom = { onOpenSpyForRoom(room.id) },
                onAddThreeMsgLimit = { onAddThreeMsgLimit(room.id) },
                onAddFifteenMins = { onAddFifteenMins(room.id) },
                onTogglePause = { onTogglePauseRoom(room.id) },
                onCloseRoom = { onCloseRoom(room.id) }
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SimpleRoomCard(
    room: BrokerRoom,
    baseWebUrl: String,
    nowMillis: Long,
    onCopyClientLink: () -> Unit,
    onShareClientLink: () -> Unit,
    onCopySupplierLink: () -> Unit,
    onShareSupplierLink: () -> Unit,
    onSpyRoom: () -> Unit,
    onAddThreeMsgLimit: () -> Unit,
    onAddFifteenMins: () -> Unit,
    onTogglePause: () -> Unit,
    onCloseRoom: () -> Unit
) {
    val remSec = room.remainingSeconds(nowMillis)
    val timerText = String.format(Locale.US, "%02d:%02d", remSec / 60, remSec % 60)

    val statusColor = when (room.status) {
        RoomStatus.ACTIVE -> EmeraldPrimary
        RoomStatus.PAUSED -> AmberWarning
        RoomStatus.CLOSED -> CrimsonAlert
    }

    val clientUrl = room.buildClientWebLink(baseWebUrl)
    val supplierUrl = room.buildSupplierWebLink(baseWebUrl)

    Card(
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VaultBorder, RoundedCornerShape(14.dp))
            .testTag("room_card_${room.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Label, ID, Status, Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room.id,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = CyanSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = room.status.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = room.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultObsidian)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Browser Share Links Section
            BrowserLinkBox(
                roleLabel = "CLIENT WEB LINK (Send to Buyer)",
                url = clientUrl,
                accentColor = CyanSecondary,
                copyTag = "copy_client_link_${room.id}",
                onCopy = onCopyClientLink,
                onShare = onShareClientLink
            )

            Spacer(modifier = Modifier.height(8.dp))

            BrowserLinkBox(
                roleLabel = "SUPPLIER WEB LINK (Send to Seller)",
                url = supplierUrl,
                accentColor = AmberWarning,
                copyTag = "copy_supplier_link_${room.id}",
                onCopy = onCopySupplierLink,
                onShare = onShareSupplierLink
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Compact Counters Line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultObsidian)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Msgs: C ${room.clientMsgUsed}/${room.clientMsgLimit} • S ${room.supplierMsgUsed}/${room.supplierMsgLimit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Strikes: C ${room.clientStrikes}/${room.maxStrikes} • S ${room.supplierStrikes}/${room.maxStrikes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (room.clientStrikes > 0 || room.supplierStrikes > 0) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionChip(
                    label = "Watch Chat",
                    color = EmeraldPrimary,
                    testTag = "watch_chat_${room.id}",
                    onClick = onSpyRoom
                )
                SmallActionChip(
                    label = "+3 Msgs",
                    color = CyanSecondary,
                    testTag = "add_3_msg_limit_${room.id}",
                    onClick = onAddThreeMsgLimit
                )
                SmallActionChip(
                    label = "+15 Mins",
                    color = CyanSecondary,
                    testTag = "add_15_mins_${room.id}",
                    onClick = onAddFifteenMins
                )
                SmallActionChip(
                    label = if (room.status == RoomStatus.PAUSED) "Unpause" else "Pause",
                    color = AmberWarning,
                    testTag = "pause_unpause_${room.id}",
                    onClick = onTogglePause
                )
                SmallActionChip(
                    label = "Close",
                    color = CrimsonAlert,
                    testTag = "close_room_${room.id}",
                    onClick = onCloseRoom
                )
            }
        }
    }
}

@Composable
private fun BrowserLinkBox(
    roleLabel: String,
    url: String,
    accentColor: Color,
    copyTag: String,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VaultObsidian)
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        FilledTonalButton(
            onClick = onCopy,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accentColor.copy(alpha = 0.2f),
                contentColor = accentColor
            ),
            modifier = Modifier.testTag(copyTag)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share link",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SimpleSpyAndBlockedTab(
    uiState: BrokerUiState,
    onSelectRoom: (String) -> Unit,
    onApproveAndReleaseMessage: (String) -> Unit,
    onTestIncomingBrowserMessage: (String, AppRole, String) -> Unit
) {
    val activeRoom = uiState.activeRoom
    val roomMessages = uiState.messages.filter { it.roomId == activeRoom?.id }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    var testMessageInput by rememberSaveable { mutableStateOf("") }
    var testSenderRole by rememberSaveable { mutableStateOf(AppRole.SUPPLIER) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Blocked / Held Messages Section at the top in Red
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CrimsonContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, CrimsonAlert, RoundedCornerShape(14.dp))
                    .testTag("blocked_messages_alert_section")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Blocked Messages",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BLOCKED MESSAGES (${uiState.blockedMessages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hidden from the other person's browser. Tap Approve & Release if a genuine password was flagged by mistake.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnCrimsonContainer
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.blockedMessages.isEmpty()) {
                        Text(
                            text = "No blocked messages right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnCrimsonContainer
                        )
                    } else {
                        uiState.blockedMessages.forEach { blockedMsg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VaultObsidian)
                                    .border(1.dp, CrimsonAlert.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${blockedMsg.roomId} • From: ${blockedMsg.senderRole.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrimsonAlert,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = timeFormat.format(Date(blockedMsg.timestampMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${blockedMsg.content}\"",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${blockedMsg.blockLayer ?: "Filter"}: ${blockedMsg.blockReason ?: "Blocked"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnCrimsonContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onApproveAndReleaseMessage(blockedMsg.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary,
                                        contentColor = VaultObsidian
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("approve_release_btn_${blockedMsg.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve & Release", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Room Selector for Live Chat Spy
        item {
            Column {
                Text(
                    text = "WATCH LIVE BROWSER CHAT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.rooms.forEach { room ->
                        val selected = room.id == activeRoom?.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) CyanContainer else VaultSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) CyanSecondary else VaultBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectRoom(room.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${room.id}: ${room.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) CyanSecondary else Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Filter Tester (So Broker can test Layer 1 Regex + Layer 2 Gemini AI Guard)
        if (activeRoom != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test 2-Layer Anti-Steal Filter (${activeRoom.id})",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(AppRole.CLIENT, AppRole.SUPPLIER).forEach { roleOption ->
                                    val selected = testSenderRole == roleOption
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selected) CyanContainer else VaultObsidian)
                                            .clickable { testSenderRole = roleOption }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = roleOption.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = testMessageInput,
                                onValueChange = { testMessageInput = it.take(70) },
                                placeholder = { Text("Try e.g. 'call me 01712345678' or 'otp 482910'") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_filter_input")
                            )
                            Button(
                                onClick = {
                                    val msg = testMessageInput
                                    testMessageInput = ""
                                    onTestIncomingBrowserMessage(activeRoom.id, testSenderRole, msg)
                                },
                                enabled = testMessageInput.isNotBlank() && !uiState.isCheckingAiFilter,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = VaultObsidian
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("test_filter_send_btn")
                            ) {
                                if (uiState.isCheckingAiFilter) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = VaultObsidian
                                    )
                                } else {
                                    Text("Test", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Messages in Selected Room
        items(roomMessages, key = { it.id }) { msg ->
            val isBlocked = msg.status == MessageStatus.BLOCKED
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBlocked) CrimsonContainer.copy(alpha = 0.45f) else VaultSurface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isBlocked) CrimsonAlert else VaultBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${msg.senderRole.name} • ${msg.credentialLabel.ifBlank { msg.type.name }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (msg.senderRole) {
                                AppRole.CLIENT -> CyanSecondary
                                AppRole.SUPPLIER -> AmberWarning
                                AppRole.ADMIN -> EmeraldPrimary
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${msg.status.name} • ${timeFormat.format(Date(msg.timestampMillis))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBlocked) CrimsonAlert else EmeraldPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = Color.White
                    )
                    if (isBlocked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Blocked (${msg.blockLayer}): ${msg.blockReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonAlert
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SimpleWebSetupTab(
    uiState: BrokerUiState,
    onSaveSettings: (String, String, String) -> Unit,
    onManualGoogleSheetsSync: () -> Unit,
    onSendTestTelegramAlert: () -> Unit
) {
    var gasUrl by rememberSaveable(uiState.settings.gasWebAppUrl) { mutableStateOf(uiState.settings.gasWebAppUrl) }
    var tgToken by rememberSaveable(uiState.settings.telegramBotToken) { mutableStateOf(uiState.settings.telegramBotToken) }
    var tgChatId by rememberSaveable(uiState.settings.telegramChatId) { mutableStateOf(uiState.settings.telegramChatId) }
    var copiedCodeGs by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Step 1: Copy Code.gs + Zero Gmail Login Instructions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurfaceElevated),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanSecondary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step 1: Deploy Browser Chat Script (No Gmail Login Needed)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To let Client & Supplier chat WITHOUT signing into Gmail or authorizing anything:\n" +
                            "1. Paste this updated Code.gs in Google Sheet → Extensions → Apps Script.\n" +
                            "2. Click Deploy → Manage deployments → Edit (pencil icon) → Version: New version.\n" +
                            "3. Set 'Execute as' = Me.\n" +
                            "4. Set 'Who has access' = Anyone (Do NOT select 'Anyone with Google account'!).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(GoogleAppsScriptTemplate.CODE_GS))
                            copiedCodeGs = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanSecondary,
                            contentColor = VaultObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("copy_code_gs_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (copiedCodeGs) "✓ Copied Updated Code.gs!" else "Copy Updated Code.gs Script",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Step 2: Paste Web App URL & Optional Telegram Bot
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step 2: Save Your Web App URL",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = gasUrl,
                        onValueChange = { gasUrl = it },
                        label = { Text("Google Apps Script Web App URL") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gas_webapp_url_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tgToken,
                        onValueChange = { tgToken = it },
                        label = { Text("Telegram Bot Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_bot_token_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tgChatId,
                        onValueChange = { tgChatId = it },
                        label = { Text("Telegram Chat ID (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_chat_id_input")
                    )

                    if (!uiState.syncStatusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.syncStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onSaveSettings(gasUrl, tgToken, tgChatId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = VaultObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_settings_btn")
                    ) {
                        Text("Save Web Link & Telegram Settings", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onManualGoogleSheetsSync,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_sheets_now_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onSendTestTelegramAlert,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_telegram_alert_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Telegram", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}
