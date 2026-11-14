package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrokerRepository
import com.example.model.AppRole
import com.example.model.AppSettings
import com.example.model.BrokerRoom
import com.example.model.ChatMessage
import com.example.model.MessageStatus
import com.example.model.MessageType
import com.example.model.RoomStatus
import com.example.notification.NotificationEngine
import com.example.security.AntiStealFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BrokerUiState(
    val activeRoomId: String = "RM-8042",
    val rooms: List<BrokerRoom> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val nowMillis: Long = System.currentTimeMillis(),
    val isCheckingAiFilter: Boolean = false,
    val statusBannerMessage: String? = null,
    val syncStatusMessage: String? = null,
    val newlyCreatedRoom: BrokerRoom? = null
) {
    val activeRoom: BrokerRoom?
        get() = rooms.find { it.id == activeRoomId } ?: rooms.firstOrNull()

    val blockedMessages: List<ChatMessage>
        get() = messages.filter { it.status == MessageStatus.BLOCKED }
            .sortedByDescending { it.timestampMillis }
}

class BrokerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BrokerRepository(application.applicationContext)
    private val filter = AntiStealFilter()
    val notificationEngine = NotificationEngine(application.applicationContext)

    private val _uiState = MutableStateFlow(BrokerUiState())
    val uiState: StateFlow<BrokerUiState> = _uiState.asStateFlow()

    init {
        val loadedRooms = repository.loadRooms()
        val loadedMessages = repository.loadMessages()
        val loadedSettings = repository.loadSettings()
        val defaultRoomId = loadedRooms.firstOrNull()?.id ?: "RM-8042"

        _uiState.value = BrokerUiState(
            activeRoomId = defaultRoomId,
            rooms = loadedRooms,
            messages = loadedMessages,
            settings = loadedSettings,
            newlyCreatedRoom = loadedRooms.firstOrNull()
        )

        startLiveTimersAndCloudSync()
    }

    private fun startLiveTimersAndCloudSync() {
        // 1-second timer for room countdowns
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    var roomsChanged = false
                    val updatedRooms = state.rooms.map { room ->
                        if (room.status == RoomStatus.ACTIVE && now >= room.expiresAtMillis) {
                            roomsChanged = true
                            room.copy(status = RoomStatus.CLOSED, pauseReason = "30-minute timer expired.")
                        } else {
                            room
                        }
                    }
                    if (roomsChanged) {
                        repository.saveRooms(updatedRooms)
                    }
                    state.copy(nowMillis = now, rooms = updatedRooms)
                }
            }
        }

        // Background polling from Google Sheets when Web App URL is configured
        viewModelScope.launch {
            while (true) {
                delay(5000L)
                val currentSettings = _uiState.value.settings
                if (currentSettings.useGoogleSheetsSync && currentSettings.gasWebAppUrl.isNotBlank()) {
                    val result = repository.pullFromGoogleSheets(currentSettings)
                    result.onSuccess { (remoteRooms, remoteMessages) ->
                        if (remoteRooms.isNotEmpty()) {
                            val previousCount = _uiState.value.messages.size
                            repository.saveRooms(remoteRooms)
                            repository.saveMessages(remoteMessages)
                            _uiState.update {
                                it.copy(rooms = remoteRooms, messages = remoteMessages)
                            }
                            if (remoteMessages.size > previousCount) {
                                val newest = remoteMessages.maxByOrNull { it.timestampMillis }
                                if (newest != null) {
                                    if (newest.status == MessageStatus.BLOCKED) {
                                        notificationEngine.playAdminBlockedAlarm(currentSettings.soundMuted)
                                        notificationEngine.triggerHapticVibration(isUrgentAlert = true)
                                        notificationEngine.sendNativeNotification(
                                            title = "🚨 Blocked Message in ${newest.roomId}",
                                            body = "${newest.senderRole.name}: \"${newest.content}\"",
                                            isSecurityAlert = true
                                        )
                                    } else {
                                        notificationEngine.playIncomingMessageChime(currentSettings.soundMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectActiveRoom(roomId: String) {
        _uiState.update { it.copy(activeRoomId = roomId) }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    /**
     * Create a new masked room and immediately prepare its Client & Supplier Web Browser links.
     */
    fun createRoom(customLabel: String) {
        val cleanLabel = customLabel.trim().ifBlank { "Account Deal #${Random.nextInt(100, 999)}" }
        val roomId = "RM-${Random.nextInt(1000, 9999)}"
        val now = System.currentTimeMillis()
        val newRoom = BrokerRoom(
            id = roomId,
            label = cleanLabel,
            status = RoomStatus.ACTIVE,
            createdAtMillis = now,
            expiresAtMillis = now + 30 * 60 * 1000L,
            clientMsgUsed = 0,
            clientMsgLimit = 6,
            supplierMsgUsed = 0,
            supplierMsgLimit = 6,
            clientStrikes = 0,
            supplierStrikes = 0,
            maxStrikes = 2
        )

        val welcomeMsg = ChatMessage(
            id = "MSG-${System.currentTimeMillis()}",
            roomId = roomId,
            senderRole = AppRole.ADMIN,
            type = MessageType.SYSTEM,
            content = "Web browser chat links generated for $cleanLabel. Share the Client Link and Supplier Link so they can chat in their browsers.",
            timestampMillis = now,
            status = MessageStatus.ALLOWED
        )

        _uiState.update { state ->
            val updatedRooms = listOf(newRoom) + state.rooms
            val updatedMessages = state.messages + welcomeMsg
            repository.saveRooms(updatedRooms)
            repository.saveMessages(updatedMessages)

            viewModelScope.launch {
                repository.syncRoomToGoogleSheets(newRoom, state.settings)
            }

            state.copy(
                rooms = updatedRooms,
                messages = updatedMessages,
                activeRoomId = newRoom.id,
                newlyCreatedRoom = newRoom,
                statusBannerMessage = "Created ${newRoom.id}! Copy and share the browser links below."
            )
        }
    }

    fun addThreeMessageLimit(roomId: String) {
        updateRoomById(roomId) { room ->
            room.copy(
                clientMsgLimit = room.clientMsgLimit + 3,
                supplierMsgLimit = room.supplierMsgLimit + 3
            )
        }
        _uiState.update { it.copy(statusBannerMessage = "+3 message limit added to $roomId.") }
    }

    fun addFifteenMinutes(roomId: String) {
        val now = System.currentTimeMillis()
        updateRoomById(roomId) { room ->
            val baseTime = maxOf(room.expiresAtMillis, now)
            room.copy(
                expiresAtMillis = baseTime + 15 * 60 * 1000L,
                status = if (room.status == RoomStatus.CLOSED) RoomStatus.ACTIVE else room.status
            )
        }
        _uiState.update { it.copy(statusBannerMessage = "+15 minutes added to $roomId.") }
    }

    fun togglePauseRoom(roomId: String) {
        updateRoomById(roomId) { room ->
            when (room.status) {
                RoomStatus.PAUSED -> room.copy(
                    status = RoomStatus.ACTIVE,
                    pauseReason = null,
                    clientStrikes = if (room.clientStrikes >= room.maxStrikes) room.maxStrikes - 1 else room.clientStrikes,
                    supplierStrikes = if (room.supplierStrikes >= room.maxStrikes) room.maxStrikes - 1 else room.supplierStrikes
                )
                RoomStatus.ACTIVE -> room.copy(
                    status = RoomStatus.PAUSED,
                    pauseReason = "Paused by Broker Admin."
                )
                RoomStatus.CLOSED -> room.copy(
                    status = RoomStatus.ACTIVE,
                    expiresAtMillis = maxOf(room.expiresAtMillis, System.currentTimeMillis() + 15 * 60 * 1000L),
                    pauseReason = null
                )
            }
        }
    }

    fun closeRoom(roomId: String) {
        updateRoomById(roomId) { room ->
            room.copy(
                status = RoomStatus.CLOSED,
                pauseReason = "Closed by Broker Admin."
            )
        }
        _uiState.update { it.copy(statusBannerMessage = "Room $roomId closed.") }
    }

    fun approveAndReleaseBlockedMessage(messageId: String) {
        val currentState = _uiState.value
        val targetMsg = currentState.messages.find { it.id == messageId } ?: return

        val updatedMessages = currentState.messages.map { msg ->
            if (msg.id == messageId) msg.copy(status = MessageStatus.RELEASED) else msg
        }

        val updatedRooms = currentState.rooms.map { room ->
            if (room.id == targetMsg.roomId) {
                val newClientStrikes = if (targetMsg.senderRole == AppRole.CLIENT) {
                    (room.clientStrikes - 1).coerceAtLeast(0)
                } else {
                    room.clientStrikes
                }
                val newSupplierStrikes = if (targetMsg.senderRole == AppRole.SUPPLIER) {
                    (room.supplierStrikes - 1).coerceAtLeast(0)
                } else {
                    room.supplierStrikes
                }
                val shouldResume = room.status == RoomStatus.PAUSED &&
                    newClientStrikes < room.maxStrikes &&
                    newSupplierStrikes < room.maxStrikes
                room.copy(
                    clientStrikes = newClientStrikes,
                    supplierStrikes = newSupplierStrikes,
                    status = if (shouldResume) RoomStatus.ACTIVE else room.status,
                    pauseReason = if (shouldResume) null else room.pauseReason
                )
            } else {
                room
            }
        }

        repository.saveMessages(updatedMessages)
        repository.saveRooms(updatedRooms)

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                rooms = updatedRooms,
                statusBannerMessage = "Message approved & released to receiver's browser."
            )
        }
    }

    /**
     * Allows the Broker to test how an incoming Client/Supplier browser message is filtered
     * by the 2-Layer Regex + Gemini AI Guard (`gemini-3.8-flash`).
     */
    fun testIncomingBrowserMessage(
        roomId: String,
        senderRole: AppRole,
        rawText: String
    ) {
        val text = rawText.trim().take(70)
        if (text.isBlank()) return

        _uiState.update { it.copy(isCheckingAiFilter = true) }

        viewModelScope.launch {
            val roomMessages = _uiState.value.messages.filter { it.roomId == roomId }
            val senderLastTwoFreeTexts = roomMessages
                .filter { it.senderRole == senderRole && it.type == MessageType.FREE_TEXT }
                .takeLast(2)
                .map { it.content }

            val layer1Verdict = filter.checkLayer1Regex(
                rawMessage = text,
                senderLastTwoMessages = senderLastTwoFreeTexts
            )

            val finalVerdict = if (!layer1Verdict.allowed) {
                layer1Verdict
            } else {
                filter.checkLayer2GeminiGuard(
                    currentMessage = text,
                    senderRoleName = senderRole.name,
                    recentRoomMessages = roomMessages.takeLast(5)
                )
            }

            val isBlocked = !finalVerdict.allowed
            val newMsg = ChatMessage(
                id = "MSG-${System.currentTimeMillis()}-${Random.nextInt(10, 99)}",
                roomId = roomId,
                senderRole = senderRole,
                type = MessageType.FREE_TEXT,
                content = text,
                timestampMillis = System.currentTimeMillis(),
                status = if (isBlocked) MessageStatus.BLOCKED else MessageStatus.ALLOWED,
                blockLayer = if (isBlocked) finalVerdict.layer else null,
                blockReason = if (isBlocked) finalVerdict.reason else null
            )

            var updatedTargetRoom: BrokerRoom? = null
            _uiState.update { state ->
                val updatedRooms = state.rooms.map { r ->
                    if (r.id == roomId) {
                        val nextClientUsed = if (senderRole == AppRole.CLIENT) r.clientMsgUsed + 1 else r.clientMsgUsed
                        val nextSupplierUsed = if (senderRole == AppRole.SUPPLIER) r.supplierMsgUsed + 1 else r.supplierMsgUsed
                        val nextClientStrikes = if (isBlocked && senderRole == AppRole.CLIENT) r.clientStrikes + 1 else r.clientStrikes
                        val nextSupplierStrikes = if (isBlocked && senderRole == AppRole.SUPPLIER) r.supplierStrikes + 1 else r.supplierStrikes
                        val reachedTwoStrikes = nextClientStrikes >= r.maxStrikes || nextSupplierStrikes >= r.maxStrikes

                        r.copy(
                            clientMsgUsed = nextClientUsed,
                            supplierMsgUsed = nextSupplierUsed,
                            clientStrikes = nextClientStrikes,
                            supplierStrikes = nextSupplierStrikes,
                            status = if (reachedTwoStrikes) RoomStatus.PAUSED else r.status,
                            pauseReason = if (reachedTwoStrikes) {
                                "Auto-paused: 2 strikes reached (${senderRole.name})."
                            } else {
                                r.pauseReason
                            }
                        ).also { updatedTargetRoom = it }
                    } else {
                        r
                    }
                }
                val updatedMessages = state.messages + newMsg
                repository.saveRooms(updatedRooms)
                repository.saveMessages(updatedMessages)

                state.copy(
                    rooms = updatedRooms,
                    messages = updatedMessages,
                    isCheckingAiFilter = false,
                    statusBannerMessage = if (isBlocked) {
                        "🚨 BLOCKED (${finalVerdict.layer}): ${finalVerdict.reason}"
                    } else {
                        "✓ ALLOWED by 2-Layer Filter"
                    }
                )
            }

            if (isBlocked) {
                val muted = _uiState.value.settings.soundMuted
                notificationEngine.playAdminBlockedAlarm(muted)
                notificationEngine.triggerHapticVibration(isUrgentAlert = true)
                notificationEngine.sendNativeNotification(
                    title = "🚨 BLOCKED in $roomId (${senderRole.name})",
                    body = "\"$text\" — ${finalVerdict.reason ?: "Security Filter"}",
                    isSecurityAlert = true
                )
            } else {
                notificationEngine.playIncomingMessageChime(_uiState.value.settings.soundMuted)
            }

            repository.syncMessageAndAlert(newMsg, updatedTargetRoom, _uiState.value.settings)
        }
    }

    fun toggleMuteSound() {
        _uiState.update { state ->
            val updatedSettings = state.settings.copy(soundMuted = !state.settings.soundMuted)
            repository.saveSettings(updatedSettings)
            state.copy(settings = updatedSettings)
        }
    }

    fun saveSettingsConfig(
        gasWebAppUrl: String,
        telegramBotToken: String,
        telegramChatId: String
    ) {
        val newSettings = _uiState.value.settings.copy(
            gasWebAppUrl = gasWebAppUrl.trim(),
            telegramBotToken = telegramBotToken.trim(),
            telegramChatId = telegramChatId.trim(),
            useGoogleSheetsSync = gasWebAppUrl.isNotBlank()
        )
        repository.saveSettings(newSettings)
        _uiState.update {
            it.copy(
                settings = newSettings,
                syncStatusMessage = if (gasWebAppUrl.isNotBlank()) {
                    "Saved! Client & Supplier browser links now point to your Web App URL."
                } else {
                    "Saved settings locally."
                }
            )
        }
    }

    fun manualGoogleSheetsSync() {
        val settings = _uiState.value.settings
        if (settings.gasWebAppUrl.isBlank()) {
            _uiState.update {
                it.copy(syncStatusMessage = "Paste your deployed Google Apps Script Web App URL first.")
            }
            return
        }
        _uiState.update { it.copy(syncStatusMessage = "Syncing with Google Sheets...") }
        viewModelScope.launch {
            val result = repository.pullFromGoogleSheets(settings)
            result.onSuccess { (remoteRooms, remoteMessages) ->
                if (remoteRooms.isNotEmpty()) {
                    repository.saveRooms(remoteRooms)
                    repository.saveMessages(remoteMessages)
                    _uiState.update {
                        it.copy(
                            rooms = remoteRooms,
                            messages = remoteMessages,
                            syncStatusMessage = "Synced ${remoteRooms.size} rooms & ${remoteMessages.size} messages."
                        )
                    }
                } else {
                    for (r in _uiState.value.rooms) {
                        repository.syncRoomToGoogleSheets(r, settings)
                    }
                    _uiState.update {
                        it.copy(syncStatusMessage = "Connected & pushed ${_uiState.value.rooms.size} rooms to your Google Sheet.")
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(syncStatusMessage = "Sync failed: ${err.localizedMessage ?: "Check Web App URL"}")
                }
            }
        }
    }

    fun sendTestTelegramAlert() {
        val settings = _uiState.value.settings
        if (settings.telegramBotToken.isBlank() || settings.telegramChatId.isBlank()) {
            _uiState.update {
                it.copy(syncStatusMessage = "Enter both Telegram Bot Token and Chat ID first.")
            }
            return
        }
        _uiState.update { it.copy(syncStatusMessage = "Sending test alert to Telegram...") }
        viewModelScope.launch {
            val sampleBlocked = ChatMessage(
                id = "TEST-ALERT",
                roomId = _uiState.value.activeRoomId,
                senderRole = AppRole.SUPPLIER,
                type = MessageType.FREE_TEXT,
                content = "01712-345678 call me on whatsapp",
                status = MessageStatus.BLOCKED,
                blockLayer = "LAYER 1 (Regex + Phone Rule)",
                blockReason = "Test Alert from BrokerVault Admin App"
            )
            val res = repository.sendDirectTelegramAlert(sampleBlocked, _uiState.value.activeRoom, settings)
            res.onSuccess {
                _uiState.update { it.copy(syncStatusMessage = "Telegram test alert delivered!") }
            }.onFailure { err ->
                _uiState.update { it.copy(syncStatusMessage = "Telegram error: ${err.localizedMessage}") }
            }
        }
    }

    private fun updateRoomById(roomId: String, transform: (BrokerRoom) -> BrokerRoom) {
        var updatedRoomRef: BrokerRoom? = null
        _uiState.update { state ->
            val updatedRooms = state.rooms.map { r ->
                if (r.id == roomId) {
                    transform(r).also { updatedRoomRef = it }
                } else {
                    r
                }
            }
            repository.saveRooms(updatedRooms)
            state.copy(rooms = updatedRooms)
        }
        val target = updatedRoomRef
        if (target != null) {
            viewModelScope.launch {
                repository.syncRoomToGoogleSheets(target, _uiState.value.settings)
            }
        }
    }
}
