package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.AppRole
import com.example.model.AppSettings
import com.example.model.BrokerRoom
import com.example.model.ChatMessage
import com.example.model.MessageStatus
import com.example.model.MessageType
import com.example.model.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "BrokerRepository"
private const val PREFS_NAME = "broker_vault_storage_v1"
private const val KEY_ROOMS = "rooms_json"
private const val KEY_MESSAGES = "messages_json"
private const val KEY_SETTINGS = "settings_json"
private const val KEY_SEEDED = "initial_seeded"

class BrokerRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            seedInitialDemoData()
        }
    }

    private fun seedInitialDemoData() {
        val now = System.currentTimeMillis()
        val room1 = BrokerRoom(
            id = "RM-8042",
            label = "Netflix 1-Month - Client Rahim",
            status = RoomStatus.ACTIVE,
            createdAtMillis = now - 6 * 60 * 1000L,
            expiresAtMillis = now + 24 * 60 * 1000L,
            clientMsgUsed = 2,
            clientMsgLimit = 6,
            supplierMsgUsed = 1,
            supplierMsgLimit = 6,
            clientStrikes = 0,
            supplierStrikes = 1,
            maxStrikes = 2
        )

        val room2 = BrokerRoom(
            id = "RM-3190",
            label = "Spotify Duo 6M - Client Tanvir",
            status = RoomStatus.ACTIVE,
            createdAtMillis = now - 12 * 60 * 1000L,
            expiresAtMillis = now + 18 * 60 * 1000L,
            clientMsgUsed = 1,
            clientMsgLimit = 6,
            supplierMsgUsed = 1,
            supplierMsgLimit = 6,
            clientStrikes = 0,
            supplierStrikes = 0,
            maxStrikes = 2
        )

        val initialMessages = listOf(
            ChatMessage(
                id = "MSG-1001",
                roomId = "RM-8042",
                senderRole = AppRole.CLIENT,
                type = MessageType.PRESET,
                content = "Send Email & Password",
                timestampMillis = now - 5 * 60 * 1000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1002",
                roomId = "RM-8042",
                senderRole = AppRole.SUPPLIER,
                type = MessageType.CREDENTIAL_EMAIL,
                content = "rahim.stream4k@vaultmail.io",
                credentialLabel = "Account Email",
                timestampMillis = now - 4 * 60 * 1000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1003",
                roomId = "RM-8042",
                senderRole = AppRole.SUPPLIER,
                type = MessageType.CREDENTIAL_PASSWORD,
                content = "Nf#9982!xPro",
                credentialLabel = "Account Password",
                timestampMillis = now - 4 * 60 * 1000L + 5000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1004",
                roomId = "RM-8042",
                senderRole = AppRole.CLIENT,
                type = MessageType.PRESET,
                content = "Wait 2 minutes, logging in...",
                timestampMillis = now - 3 * 60 * 1000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1005",
                roomId = "RM-8042",
                senderRole = AppRole.CLIENT,
                type = MessageType.FREE_TEXT,
                content = "It is asking for household verification code now",
                timestampMillis = now - 2 * 60 * 1000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1006",
                roomId = "RM-8042",
                senderRole = AppRole.SUPPLIER,
                type = MessageType.CREDENTIAL_OTP,
                content = "482910",
                credentialLabel = "OTP / Verification Code",
                timestampMillis = now - 90 * 1000L,
                status = MessageStatus.ALLOWED
            ),
            ChatMessage(
                id = "MSG-1007",
                roomId = "RM-8042",
                senderRole = AppRole.SUPPLIER,
                type = MessageType.FREE_TEXT,
                content = "bro msg me on wa 01712-345678 for direct cheap renew",
                timestampMillis = now - 45 * 1000L,
                status = MessageStatus.BLOCKED,
                blockLayer = "LAYER 1 (Keyword Blacklist)",
                blockReason = "Blocked restricted contact/price keyword \"wa\" & phone pattern"
            ),
            ChatMessage(
                id = "MSG-2001",
                roomId = "RM-3190",
                senderRole = AppRole.SUPPLIER,
                type = MessageType.CREDENTIAL_EMAIL,
                content = "tanvir.audio@musicbox.org",
                credentialLabel = "Account Email",
                timestampMillis = now - 8 * 60 * 1000L,
                status = MessageStatus.ALLOWED
            )
        )

        saveRooms(listOf(room1, room2))
        saveMessages(initialMessages)
        saveSettings(AppSettings())
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    fun loadRooms(): List<BrokerRoom> {
        val raw = prefs.getString(KEY_ROOMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                BrokerRoom(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    status = RoomStatus.valueOf(o.optString("status", "ACTIVE")),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    expiresAtMillis = o.optLong("expiresAtMillis", System.currentTimeMillis() + 1800_000L),
                    clientMsgUsed = o.optInt("clientMsgUsed", 0),
                    clientMsgLimit = o.optInt("clientMsgLimit", 6),
                    supplierMsgUsed = o.optInt("supplierMsgUsed", 0),
                    supplierMsgLimit = o.optInt("supplierMsgLimit", 6),
                    clientStrikes = o.optInt("clientStrikes", 0),
                    supplierStrikes = o.optInt("supplierStrikes", 0),
                    maxStrikes = o.optInt("maxStrikes", 2),
                    pauseReason = o.optString("pauseReason", "").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stored rooms", e)
            emptyList()
        }
    }

    fun saveRooms(rooms: List<BrokerRoom>) {
        val arr = JSONArray()
        for (r in rooms) {
            arr.put(roomToJson(r))
        }
        prefs.edit().putString(KEY_ROOMS, arr.toString()).apply()
    }

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                ChatMessage(
                    id = o.getString("id"),
                    roomId = o.getString("roomId"),
                    senderRole = AppRole.valueOf(o.optString("senderRole", "CLIENT")),
                    type = MessageType.valueOf(o.optString("type", "FREE_TEXT")),
                    content = o.getString("content"),
                    credentialLabel = o.optString("credentialLabel", ""),
                    timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis()),
                    status = MessageStatus.valueOf(o.optString("status", "ALLOWED")),
                    blockLayer = o.optString("blockLayer", "").ifBlank { null },
                    blockReason = o.optString("blockReason", "").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stored messages", e)
            emptyList()
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        for (m in messages) {
            arr.put(messageToJson(m))
        }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            AppSettings(
                adminPin = o.optString("adminPin", "1234").ifBlank { "1234" },
                gasWebAppUrl = o.optString("gasWebAppUrl", ""),
                telegramBotToken = o.optString("telegramBotToken", ""),
                telegramChatId = o.optString("telegramChatId", ""),
                useGoogleSheetsSync = o.optBoolean("useGoogleSheetsSync", false),
                soundMuted = o.optBoolean("soundMuted", false)
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val o = JSONObject().apply {
            put("adminPin", settings.adminPin)
            put("gasWebAppUrl", settings.gasWebAppUrl)
            put("telegramBotToken", settings.telegramBotToken)
            put("telegramChatId", settings.telegramChatId)
            put("useGoogleSheetsSync", settings.useGoogleSheetsSync)
            put("soundMuted", settings.soundMuted)
        }
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    /**
     * Push room update to Google Apps Script Web App if Live Google Sheets Sync is active.
     */
    suspend fun syncRoomToGoogleSheets(room: BrokerRoom, settings: AppSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!settings.useGoogleSheetsSync || settings.gasWebAppUrl.isBlank()) {
                return@withContext Result.success(Unit)
            }
            try {
                val payload = JSONObject().apply {
                    put("action", "upsertRoom")
                    put("room", roomToJson(room))
                }
                val req = Request.Builder()
                    .url(settings.gasWebAppUrl.trim())
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Google Sheets room sync failed", e)
                Result.failure(e)
            }
        }

    /**
     * Push message + room counters to Google Apps Script Web App & send direct Telegram Bot alert if BLOCKED.
     */
    suspend fun syncMessageAndAlert(
        message: ChatMessage,
        room: BrokerRoom?,
        settings: AppSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Direct Telegram Bot Alert if message is BLOCKED and Telegram credentials are configured
        if (message.status == MessageStatus.BLOCKED &&
            settings.telegramBotToken.isNotBlank() &&
            settings.telegramChatId.isNotBlank()
        ) {
            sendDirectTelegramAlert(message, room, settings)
        }

        // 2. Google Sheets Web App Sync if enabled
        if (!settings.useGoogleSheetsSync || settings.gasWebAppUrl.isBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            val payload = JSONObject().apply {
                put("action", "postMessage")
                put("message", messageToJson(message))
                if (room != null) {
                    put("room", roomToJson(room))
                }
                put("telegramBotToken", settings.telegramBotToken)
                put("telegramChatId", settings.telegramChatId)
            }
            val req = Request.Builder()
                .url(settings.gasWebAppUrl.trim())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Google Sheets message sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch rooms and messages from deployed Google Apps Script Web App (`doGet`).
     */
    suspend fun pullFromGoogleSheets(settings: AppSettings): Result<Pair<List<BrokerRoom>, List<ChatMessage>>> =
        withContext(Dispatchers.IO) {
            if (settings.gasWebAppUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
            }
            try {
                val url = settings.gasWebAppUrl.trim()
                val separator = if (url.contains("?")) "&" else "?"
                val req = Request.Builder()
                    .url("${url}${separator}action=syncAll")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || bodyStr.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                    }
                    val root = JSONObject(bodyStr)
                    val roomsArr = root.optJSONArray("rooms") ?: JSONArray()
                    val msgsArr = root.optJSONArray("messages") ?: JSONArray()

                    val fetchedRooms = (0 until roomsArr.length()).mapNotNull { idx ->
                        val o = roomsArr.optJSONObject(idx) ?: return@mapNotNull null
                        val statusStr = o.optString("status", "ACTIVE")
                        BrokerRoom(
                            id = o.optString("id"),
                            label = o.optString("label"),
                            status = runCatching { RoomStatus.valueOf(statusStr) }.getOrDefault(RoomStatus.ACTIVE),
                            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                            expiresAtMillis = o.optLong("expiresAtMillis", System.currentTimeMillis() + 1800_000L),
                            clientMsgUsed = o.optInt("clientMsgUsed", 0),
                            clientMsgLimit = o.optInt("clientMsgLimit", 6),
                            supplierMsgUsed = o.optInt("supplierMsgUsed", 0),
                            supplierMsgLimit = o.optInt("supplierMsgLimit", 6),
                            clientStrikes = o.optInt("clientStrikes", 0),
                            supplierStrikes = o.optInt("supplierStrikes", 0),
                            maxStrikes = o.optInt("maxStrikes", 2),
                            pauseReason = o.optString("pauseReason", "").ifBlank { null }
                        )
                    }

                    val fetchedMsgs = (0 until msgsArr.length()).mapNotNull { idx ->
                        val o = msgsArr.optJSONObject(idx) ?: return@mapNotNull null
                        ChatMessage(
                            id = o.optString("id"),
                            roomId = o.optString("roomId"),
                            senderRole = runCatching { AppRole.valueOf(o.optString("senderRole", "CLIENT")) }
                                .getOrDefault(AppRole.CLIENT),
                            type = runCatching { MessageType.valueOf(o.optString("type", "FREE_TEXT")) }
                                .getOrDefault(MessageType.FREE_TEXT),
                            content = o.optString("content"),
                            credentialLabel = o.optString("credentialLabel", ""),
                            timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis()),
                            status = runCatching { MessageStatus.valueOf(o.optString("status", "ALLOWED")) }
                                .getOrDefault(MessageStatus.ALLOWED),
                            blockLayer = o.optString("blockLayer", "").ifBlank { null },
                            blockReason = o.optString("blockReason", "").ifBlank { null }
                        )
                    }

                    Result.success(fetchedRooms to fetchedMsgs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pull from Google Sheets failed", e)
                Result.failure(e)
            }
        }

    suspend fun sendDirectTelegramAlert(
        message: ChatMessage,
        room: BrokerRoom?,
        settings: AppSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (settings.telegramBotToken.isBlank() || settings.telegramChatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Telegram Bot Token or Chat ID missing"))
        }
        try {
            val alertText = buildString {
                appendLine("🚨 BROKERVAULT SECURITY BLOCK")
                appendLine("• Room: ${room?.label ?: message.roomId} (${message.roomId})")
                appendLine("• Sender: ${message.senderRole.name}")
                appendLine("• Blocked Message: \"${message.content}\"")
                appendLine("• Layer: ${message.blockLayer ?: "Security Filter"}")
                appendLine("• Reason: ${message.blockReason ?: "Policy violation"}")
            }
            val payload = JSONObject().apply {
                put("chat_id", settings.telegramChatId.trim())
                put("text", alertText)
            }
            val req = Request.Builder()
                .url("https://api.telegram.org/bot${settings.telegramBotToken.trim()}/sendMessage")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Telegram HTTP ${resp.code}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Direct Telegram alert failed", e)
            Result.failure(e)
        }
    }

    private fun roomToJson(r: BrokerRoom): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("label", r.label)
        put("status", r.status.name)
        put("createdAtMillis", r.createdAtMillis)
        put("expiresAtMillis", r.expiresAtMillis)
        put("clientMsgUsed", r.clientMsgUsed)
        put("clientMsgLimit", r.clientMsgLimit)
        put("supplierMsgUsed", r.supplierMsgUsed)
        put("supplierMsgLimit", r.supplierMsgLimit)
        put("clientStrikes", r.clientStrikes)
        put("supplierStrikes", r.supplierStrikes)
        put("maxStrikes", r.maxStrikes)
        put("pauseReason", r.pauseReason ?: "")
    }

    private fun messageToJson(m: ChatMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("roomId", m.roomId)
        put("senderRole", m.senderRole.name)
        put("type", m.type.name)
        put("content", m.content)
        put("credentialLabel", m.credentialLabel)
        put("timestampMillis", m.timestampMillis)
        put("status", m.status.name)
        put("blockLayer", m.blockLayer ?: "")
        put("blockReason", m.blockReason ?: "")
    }
}
