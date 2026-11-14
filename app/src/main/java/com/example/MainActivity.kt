package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BrokerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                BrokerVaultApp()
            }
        }
    }
}

@Composable
fun BrokerVaultApp(
    brokerViewModel: BrokerViewModel = viewModel()
) {
    val uiState by brokerViewModel.uiState.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AdminDashboardScreen(
        uiState = uiState,
        onCreateRoom = { label -> brokerViewModel.createRoom(label) },
        onSelectRoomForSpy = { roomId -> brokerViewModel.selectActiveRoom(roomId) },
        onAddThreeMsgLimit = { roomId -> brokerViewModel.addThreeMessageLimit(roomId) },
        onAddFifteenMins = { roomId -> brokerViewModel.addFifteenMinutes(roomId) },
        onTogglePauseRoom = { roomId -> brokerViewModel.togglePauseRoom(roomId) },
        onCloseRoom = { roomId -> brokerViewModel.closeRoom(roomId) },
        onApproveAndReleaseMessage = { msgId -> brokerViewModel.approveAndReleaseBlockedMessage(msgId) },
        onTestIncomingBrowserMessage = { roomId, role, text ->
            brokerViewModel.testIncomingBrowserMessage(roomId, role, text)
        },
        onToggleMute = { brokerViewModel.toggleMuteSound() },
        onSaveSettings = { url, token, chatId ->
            brokerViewModel.saveSettingsConfig(url, token, chatId)
        },
        onManualGoogleSheetsSync = { brokerViewModel.manualGoogleSheetsSync() },
        onSendTestTelegramAlert = { brokerViewModel.sendTestTelegramAlert() },
        onDismissBanner = { brokerViewModel.clearBannerMessage() }
    )
}
