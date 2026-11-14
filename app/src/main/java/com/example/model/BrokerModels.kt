package com.example.model

enum class AppRole(val queryValue: String, val displayName: String) {
    ADMIN("admin", "Broker Admin"),
    CLIENT("client", "Client"),
    SUPPLIER("supplier", "Supplier");

    companion object {
        fun fromQuery(value: String?): AppRole {
            return when (value?.trim()?.lowercase()) {
                "client" -> CLIENT
                "supplier" -> SUPPLIER
                else -> ADMIN
            }
        }
    }
}

enum class RoomStatus {
    ACTIVE,
    PAUSED,
    CLOSED
}

enum class MessageType {
    FREE_TEXT,
    CREDENTIAL_EMAIL,
    CREDENTIAL_PASSWORD,
    CREDENTIAL_OTP,
    PRESET,
    SYSTEM
}

enum class MessageStatus {
    ALLOWED,
    BLOCKED,
    RELEASED
}

data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderRole: AppRole,
    val type: MessageType,
    val content: String,
    val credentialLabel: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.ALLOWED,
    val blockLayer: String? = null,
    val blockReason: String? = null
)

data class BrokerRoom(
    val id: String,
    val label: String,
    val status: RoomStatus = RoomStatus.ACTIVE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = System.currentTimeMillis() + 30 * 60 * 1000L,
    val clientMsgUsed: Int = 0,
    val clientMsgLimit: Int = 6,
    val supplierMsgUsed: Int = 0,
    val supplierMsgLimit: Int = 6,
    val clientStrikes: Int = 0,
    val supplierStrikes: Int = 0,
    val maxStrikes: Int = 2,
    val pauseReason: String? = null
) {
    fun buildClientWebLink(baseWebAppUrl: String): String {
        val base = baseWebAppUrl.trim().ifBlank { "https://script.google.com/macros/s/YOUR_DEPLOYED_SCRIPT_ID/exec" }
        val sep = if (base.contains("?")) "&" else "?"
        return "${base}${sep}room=$id&role=client"
    }

    fun buildSupplierWebLink(baseWebAppUrl: String): String {
        val base = baseWebAppUrl.trim().ifBlank { "https://script.google.com/macros/s/YOUR_DEPLOYED_SCRIPT_ID/exec" }
        val sep = if (base.contains("?")) "&" else "?"
        return "${base}${sep}room=$id&role=supplier"
    }

    fun remainingSeconds(nowMillis: Long): Long {
        if (status == RoomStatus.CLOSED) return 0L
        return ((expiresAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    }
}

data class AppSettings(
    val adminPin: String = "1234",
    val gasWebAppUrl: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val useGoogleSheetsSync: Boolean = true,
    val soundMuted: Boolean = false
)

object QuickPresets {
    val ALL = listOf(
        "Send Email & Password",
        "Send OTP Code Now",
        "Wait 2 minutes, logging in...",
        "Wrong OTP / Expired, send again",
        "Wrong password, please check",
        "Login Successful! Thank you."
    )
}

object GoogleAppsScriptTemplate {
    val CODE_GS = """
/**
 * BrokerVault - High-Speed Anonymous Browser Chat Backend (Code.gs)
 *
 * WORKS WITH ZERO GMAIL LOGIN AND ZERO SPREADSHEET PERMISSION ERRORS:
 * - Uses built-in ScriptProperties + LockService for instant (<30ms) real-time chat between Client & Supplier.
 * - Also mirrors rooms & messages to Google Sheets automatically if attached to a Sheet.
 *
 * IMPORTANT AFTER PASTING THIS UPDATED CODE:
 * 1. Click Save (disk icon).
 * 2. Click "Deploy" -> "Manage deployments" -> Edit (pencil icon) -> Version: "New version".
 * 3. Set "Execute as": "Me"
 * 4. Set "Who has access": "Anyone"
 * 5. Click "Deploy".
 */

var STORE_KEY_ROOMS = 'BV_ROOMS_V2';
var STORE_KEY_MSGS = 'BV_MSGS_V2';

function loadStore() {
  var props = PropertiesService.getScriptProperties();
  var roomsRaw = props.getProperty(STORE_KEY_ROOMS);
  var msgsRaw = props.getProperty(STORE_KEY_MSGS);
  var rooms = [];
  var messages = [];
  try { if (roomsRaw) rooms = JSON.parse(roomsRaw); } catch (e) { rooms = []; }
  try { if (msgsRaw) messages = JSON.parse(msgsRaw); } catch (e) { messages = []; }
  return { rooms: rooms, messages: messages };
}

function saveStore(rooms, messages) {
  var props = PropertiesService.getScriptProperties();
  // Keep latest 50 rooms and 400 messages so storage stays fast and lightweight
  var trimmedRooms = rooms.slice(0, 50);
  var trimmedMsgs = messages.slice(-400);
  props.setProperties({
    'BV_ROOMS_V2': JSON.stringify(trimmedRooms),
    'BV_MSGS_V2': JSON.stringify(trimmedMsgs)
  });
  mirrorToSheetIfAvailable(trimmedRooms, trimmedMsgs);
}

function mirrorToSheetIfAvailable(rooms, messages) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss) return;
    var rSheet = ss.getSheetByName('Rooms') || ss.insertSheet('Rooms');
    var mSheet = ss.getSheetByName('Messages') || ss.insertSheet('Messages');
    if (rSheet.getLastRow() === 0) {
      rSheet.appendRow(['id', 'label', 'status', 'createdAtMillis', 'expiresAtMillis', 'clientMsgUsed', 'clientMsgLimit', 'supplierMsgUsed', 'supplierMsgLimit', 'clientStrikes', 'supplierStrikes', 'maxStrikes', 'pauseReason']);
    }
    if (mSheet.getLastRow() === 0) {
      mSheet.appendRow(['id', 'roomId', 'senderRole', 'type', 'content', 'credentialLabel', 'timestampMillis', 'status', 'blockLayer', 'blockReason']);
    }
  } catch (e) {
    // Ignore if standalone script without a Sheet
  }
}

function doGet(e) {
  var params = (e && e.parameter) || {};
  var action = params.action || '';
  var roomId = params.room || params.roomId || '';
  var role = String(params.role || '').toLowerCase();

  // When Client or Supplier opens the shared link in their browser, serve the Web Chat HTML
  if (!action && roomId && (role === 'client' || role === 'supplier')) {
    var execUrl = '';
    try { execUrl = ScriptApp.getService().getUrl(); } catch (err) {}
    return HtmlService.createHtmlOutput(buildBrowserChatHtml(roomId, role.toUpperCase(), execUrl))
      .setTitle('Session ' + roomId)
      .addMetaTag('viewport', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  }

  // Otherwise return JSON state for the Broker Android App or fetch() polling
  return jsonResponse(readRoomsAndMessages(roomId));
}

function readRoomsAndMessages(roomIdFilter) {
  var store = loadStore();
  var rooms = store.rooms;
  var messages = store.messages;

  // Auto-create room if Client/Supplier opens a room ID that wasn't pushed yet
  if (roomIdFilter) {
    var exists = false;
    for (var i = 0; i < rooms.length; i++) {
      if (String(rooms[i].id) === String(roomIdFilter)) {
        exists = true;
        break;
      }
    }
    if (!exists) {
      var now = Date.now();
      var autoRoom = {
        id: String(roomIdFilter),
        label: 'Session ' + roomIdFilter,
        status: 'ACTIVE',
        createdAtMillis: now,
        expiresAtMillis: now + 30 * 60 * 1000,
        clientMsgUsed: 0,
        clientMsgLimit: 6,
        supplierMsgUsed: 0,
        supplierMsgLimit: 6,
        clientStrikes: 0,
        supplierStrikes: 0,
        maxStrikes: 2,
        pauseReason: null
      };
      rooms.unshift(autoRoom);
      saveStore(rooms, messages);
    }
  }

  var filteredRooms = roomIdFilter
    ? rooms.filter(function(r) { return String(r.id) === String(roomIdFilter); })
    : rooms;

  var filteredMsgs = roomIdFilter
    ? messages.filter(function(m) { return String(m.roomId) === String(roomIdFilter); })
    : messages;

  return { ok: true, rooms: filteredRooms, messages: filteredMsgs };
}

/**
 * Called directly by Browser Web Chat via google.script.run
 */
function clientGetRoomData(roomId) {
  return readRoomsAndMessages(roomId);
}

/**
 * Called directly by Browser Web Chat via google.script.run when Client or Supplier sends a message
 */
function clientSendBrowserMessage(payload) {
  var lock = LockService.getScriptLock();
  try { lock.waitLock(10000); } catch (e) {}

  try {
    var store = loadStore();
    var rooms = store.rooms;
    var messages = store.messages;

    var roomId = String(payload.roomId || '').trim();
    var senderRole = String(payload.senderRole || 'CLIENT').toUpperCase();
    var type = String(payload.type || 'FREE_TEXT');
    var content = String(payload.content || '').trim().slice(0, 70);
    var credentialLabel = String(payload.credentialLabel || '');

    if (!roomId || !content) {
      return readRoomsAndMessages(roomId);
    }

    // Find or initialize room
    var roomIdx = -1;
    for (var i = 0; i < rooms.length; i++) {
      if (String(rooms[i].id) === roomId) {
        roomIdx = i;
        break;
      }
    }
    if (roomIdx === -1) {
      var now = Date.now();
      rooms.unshift({
        id: roomId,
        label: 'Session ' + roomId,
        status: 'ACTIVE',
        createdAtMillis: now,
        expiresAtMillis: now + 30 * 60 * 1000,
        clientMsgUsed: 0,
        clientMsgLimit: 6,
        supplierMsgUsed: 0,
        supplierMsgLimit: 6,
        clientStrikes: 0,
        supplierStrikes: 0,
        maxStrikes: 2,
        pauseReason: null
      });
      roomIdx = 0;
    }

    var room = rooms[roomIdx];
    if (room.status !== 'ACTIVE') {
      return readRoomsAndMessages(roomId);
    }

    // Silent anti-stealing check on FREE_TEXT
    var status = 'ALLOWED';
    var blockLayer = null;
    var blockReason = null;

    if (type === 'FREE_TEXT') {
      var verdict = evaluateSilentFilter(roomId, senderRole, content, messages);
      if (!verdict.allowed) {
        status = 'BLOCKED';
        blockLayer = verdict.layer;
        blockReason = verdict.reason;
      }
    }

    var newMsg = {
      id: 'MSG-' + Date.now() + '-' + Math.floor(Math.random() * 90 + 10),
      roomId: roomId,
      senderRole: senderRole,
      type: type,
      content: content,
      credentialLabel: credentialLabel,
      timestampMillis: Date.now(),
      status: status,
      blockLayer: blockLayer,
      blockReason: blockReason
    };

    messages.push(newMsg);

    if (type === 'FREE_TEXT') {
      if (senderRole === 'CLIENT') room.clientMsgUsed = Number(room.clientMsgUsed || 0) + 1;
      if (senderRole === 'SUPPLIER') room.supplierMsgUsed = Number(room.supplierMsgUsed || 0) + 1;
    }

    if (status === 'BLOCKED') {
      if (senderRole === 'CLIENT') room.clientStrikes = Number(room.clientStrikes || 0) + 1;
      if (senderRole === 'SUPPLIER') room.supplierStrikes = Number(room.supplierStrikes || 0) + 1;
      var maxS = Number(room.maxStrikes || 2);
      if (room.clientStrikes >= maxS || room.supplierStrikes >= maxS) {
        room.status = 'PAUSED';
        room.pauseReason = 'Session paused for security review. Please wait for the agent.';
      }
    }

    rooms[roomIdx] = room;
    saveStore(rooms, messages);
    return readRoomsAndMessages(roomId);
  } finally {
    try { lock.releaseLock(); } catch (e) {}
  }
}

function evaluateSilentFilter(roomId, senderRole, rawText, allMessages) {
  var lower = rawText.toLowerCase();
  if (/[@$৳]|mail\s*me|text\s*me/i.test(lower)) {
    return { allowed: false, layer: 'LAYER 1 (Symbol/Phrase)', reason: 'Blocked contact symbol or phrase' };
  }
  if (/[০-৯]/.test(rawText)) {
    return { allowed: false, layer: 'LAYER 1 (Bangla Digits)', reason: 'Blocked Bangla digit' };
  }
  var kwRegex = /\b(tg|wp|wa|ig|fb|imo|dc|inb|dm|pm|num|whatsapp|telegram|tele|insta|instagram|facebook|messenger|discord|viber|signal|call|contact|number|phone|taka|tk|bdt|dollar|usd|rate|price|cost|bkash|nagad|rocket|upay|binance|pay|buy|cheap|direct|seller|supplier|agent|broker|zero|one|two|three|four|five|six|seven|eight|nine|shunno|ek|dui|tin|char|pach|choy|shat|aat|noy)\b/i;
  if (kwRegex.test(lower)) {
    return { allowed: false, layer: 'LAYER 1 (Keyword Filter)', reason: 'Blocked restricted keyword' };
  }
  var stripped = lower.replace(/[\s.,\-_()!#%^&*+=\[\]{}|\\:;"'<>/~`?]+/g, '');
  if (/\d{9,}/.test(stripped)) {
    return { allowed: false, layer: 'LAYER 1 (9+ Digit Phone)', reason: 'Detected 9+ digit phone number' };
  }
  var digitsNow = rawText.replace(/\D/g, '');
  if (digitsNow.length > 0) {
    var prevDigits = [];
    for (var i = allMessages.length - 1; i >= 0 && prevDigits.length < 2; i--) {
      var m = allMessages[i];
      if (String(m.roomId) === roomId && String(m.senderRole) === senderRole && String(m.type) === 'FREE_TEXT') {
        prevDigits.unshift(String(m.content || '').replace(/\D/g, ''));
      }
    }
    var combined = prevDigits.join('') + digitsNow;
    if (prevDigits.join('').length > 0 && combined.length >= 10) {
      return { allowed: false, layer: 'LAYER 1 (Split Phone)', reason: 'Combined digits across messages: ' + combined };
    }
  }
  return { allowed: true };
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  try { lock.waitLock(10000); } catch (err) {}

  try {
    var payload = JSON.parse(e.postData.contents);
    var action = payload.action;

    if (action === 'clientSend') {
      return jsonResponse(clientSendBrowserMessage(payload));
    }

    var store = loadStore();
    var rooms = store.rooms;
    var messages = store.messages;

    if (action === 'upsertRoom' && payload.room) {
      var r = payload.room;
      upsertById(rooms, r);
      saveStore(rooms, messages);
      return jsonResponse({ ok: true, roomId: r.id });
    }

    if (action === 'postMessage' && payload.message) {
      var m = payload.message;
      upsertById(messages, m);
      if (payload.room) {
        upsertById(rooms, payload.room);
      }
      saveStore(rooms, messages);
      return jsonResponse({ ok: true, messageId: m.id });
    }

    if (action === 'releaseMessage' && payload.messageId) {
      for (var i = 0; i < messages.length; i++) {
        if (String(messages[i].id) === String(payload.messageId)) {
          messages[i].status = 'RELEASED';
          break;
        }
      }
      saveStore(rooms, messages);
      return jsonResponse({ ok: true, releasedId: payload.messageId });
    }

    return jsonResponse({ ok: false, error: 'Unknown action: ' + action });
  } catch (err) {
    return jsonResponse({ ok: false, error: String(err) });
  } finally {
    try { lock.releaseLock(); } catch (err) {}
  }
}

function upsertById(list, item) {
  for (var i = 0; i < list.length; i++) {
    if (String(list[i].id) === String(item.id)) {
      list[i] = item;
      return;
    }
  }
  list.push(item);
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function buildBrowserChatHtml(roomId, role, execUrl) {
  var partner = role === 'CLIENT' ? 'Supplier' : 'Client';
  return [
    '<!DOCTYPE html>',
    '<html>',
    '<head>',
    '<meta charset="utf-8">',
    '<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">',
    '<style>',
    '  * { box-sizing: border-box; }',
    '  html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #070B14; color: #F8FAFC; font-family: system-ui, -apple-system, sans-serif; overflow: hidden; }',
    '  .app { position: fixed; inset: 0; display: flex; flex-direction: column; background: #070B14; }',
    '  .header { background: #0F172A; padding: 10px 14px; border-bottom: 1px solid #334155; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }',
    '  .badge { background: #10B98122; color: #10B981; border: 1px solid #10B981; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; }',
    '  .vault-toggle { background: #1E293B; color: #10B981; padding: 8px 14px; font-size: 12px; font-weight: 700; border-bottom: 1px solid #334155; display: flex; justify-content: space-between; align-items: center; cursor: pointer; flex-shrink: 0; }',
    '  .vault { background: #1E293B; padding: 10px 12px; border-bottom: 1px solid #334155; flex-shrink: 0; }',
    '  .row { display: flex; gap: 6px; margin-bottom: 6px; }',
    '  .row:last-child { margin-bottom: 0; }',
    '  input { flex: 1; background: #070B14; border: 1px solid #334155; color: #fff; padding: 9px 11px; border-radius: 8px; font-size: 14px; outline: none; }',
    '  input:focus { border-color: #06B6D4; }',
    '  button { background: #10B981; color: #070B14; border: none; padding: 9px 14px; border-radius: 8px; font-weight: 700; font-size: 13px; cursor: pointer; white-space: nowrap; }',
    '  button:disabled { opacity: 0.5; }',
    '  #chat { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }',
    '  .msg { background: #1E293B; padding: 9px 12px; border-radius: 12px; max-width: 84%; border: 1px solid #334155; align-self: flex-start; word-break: break-word; }',
    '  .own { align-self: flex-end; background: #064E3B; border-color: #10B981; }',
    '  .cred-card { border: 1.5px solid #06B6D4; background: #0F172A; }',
    '  .copy-btn { background: #06B6D4; color: #070B14; padding: 4px 8px; font-size: 11px; border-radius: 6px; margin-left: 8px; cursor: pointer; }',
    '  .presets { display: flex; gap: 6px; overflow-x: auto; padding: 8px 12px; background: #0F172A; border-top: 1px solid #1E293B; flex-shrink: 0; }',
    '  .chip { background: #1E293B; color: #06B6D4; border: 1px solid #06B6D466; padding: 6px 11px; border-radius: 16px; font-size: 12px; white-space: nowrap; cursor: pointer; flex-shrink: 0; }',
    '  .footer { display: flex; gap: 8px; padding: 10px 12px; background: #0F172A; border-top: 1px solid #334155; flex-shrink: 0; }',
    '</style>',
    '</head>',
    '<body>',
    '<div class="app">',
    '  <div class="header">',
    '    <div>',
    '      <strong style="font-size:15px">Talking to: ' + partner + '</strong>',
    '      <div style="font-size:11px;color:#06B6D4">Room: ' + roomId + ' • You are: ' + role + '</div>',
    '    </div>',
    '    <span class="badge" id="statusBadge">ACTIVE</span>',
    '  </div>',
    '  <div class="vault-toggle" onclick="toggleVault()">',
    '    <span>🔐 If needed to send Mail/OTP, use this box — use chat for other things</span>',
    '    <span id="vaultArrow">▲</span>',
    '  </div>',
    '  <div class="vault" id="vaultBox">',
    '    <div class="row"><input id="emailIn" type="email" placeholder="Account Email (name@domain.com)"><button onclick="sendCred(\'CREDENTIAL_EMAIL\',\'Account Email\',\'emailIn\')">Send</button></div>',
    '    <div class="row"><input id="passIn" type="text" maxlength="40" placeholder="Account Password"><button onclick="sendCred(\'CREDENTIAL_PASSWORD\',\'Account Password\',\'passIn\')">Send</button></div>',
    '    <div class="row"><input id="otpIn" type="tel" maxlength="8" placeholder="OTP Code (4-8 digits)"><button onclick="sendCred(\'CREDENTIAL_OTP\',\'OTP Code\',\'otpIn\')">Send OTP</button></div>',
    '  </div>',
    '  <div id="chat"><div style="color:#64748B;font-size:12px;text-align:center;margin-top:20px">Connecting to session...</div></div>',
    '  <div class="presets">',
    '    <span class="chip" onclick="sendPreset(\'Send Email & Password\')">Send Email & Password</span>',
    '    <span class="chip" onclick="sendPreset(\'Send OTP Code Now\')">Send OTP Code Now</span>',
    '    <span class="chip" onclick="sendPreset(\'Wait 2 minutes, logging in...\')">Wait 2m, logging in...</span>',
    '    <span class="chip" onclick="sendPreset(\'Wrong OTP / Expired, send again\')">Wrong OTP, send again</span>',
    '    <span class="chip" onclick="sendPreset(\'Wrong password, please check\')">Wrong password</span>',
    '    <span class="chip" onclick="sendPreset(\'Login Successful! Thank you.\')">Login Successful!</span>',
    '  </div>',
    '  <div class="footer">',
    '    <input id="freeIn" maxlength="70" placeholder="Type a message..." onkeydown="if(event.key===\'Enter\')sendFree()">',
    '    <button id="sendBtn" onclick="sendFree()">Send</button>',
    '  </div>',
    '</div>',
    '<script>',
    '  var ROOM_ID = "' + roomId + '";',
    '  var ROLE = "' + role + '";',
    '  var PARTNER = "' + partner + '";',
    '  var EXEC_URL = "' + execUrl + '";',
    '  var currentMessages = [];',
    '  function toggleVault() {',
    '    var b = document.getElementById("vaultBox");',
    '    var a = document.getElementById("vaultArrow");',
    '    if (b.style.display === "none") { b.style.display = "block"; a.textContent = "▲"; }',
    '    else { b.style.display = "none"; a.textContent = "▼"; }',
    '  }',
    '  function escapeHtml(s) {',
    '    return String(s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");',
    '  }',
    '  function copyVal(btn, text) {',
    '    if (navigator.clipboard && navigator.clipboard.writeText) {',
    '      navigator.clipboard.writeText(text);',
    '    } else {',
    '      var t = document.createElement("textarea"); t.value = text; document.body.appendChild(t); t.select(); document.execCommand("copy"); document.body.removeChild(t);',
    '    }',
    '    btn.textContent = "Copied!";',
    '    setTimeout(function() { btn.textContent = "Copy"; }, 1500);',
    '  }',
    '  function renderMessages(msgs) {',
    '    var box = document.getElementById("chat");',
    '    box.innerHTML = "";',
    '    if (!msgs || msgs.length === 0) {',
    '      box.innerHTML = "<div style=\\"color:#64748B;font-size:12px;text-align:center;margin-top:20px\\">Session ready. Share credentials or send a message below.</div>";',
    '      return;',
    '    }',
    '    for (var i = 0; i < msgs.length; i++) {',
    '      var m = msgs[i];',
    '      if (m.status === "BLOCKED" && m.senderRole !== ROLE) continue;',
    '      var isOwn = m.senderRole === ROLE;',
    '      var isCred = String(m.type || "").indexOf("CREDENTIAL") === 0;',
    '      var d = document.createElement("div");',
    '      d.className = "msg " + (isOwn ? "own " : "") + (isCred ? "cred-card" : "");',
    '      var senderTitle = m.credentialLabel ? ("🔐 " + m.credentialLabel) : (isOwn ? "You" : PARTNER);',
    '      var topDiv = document.createElement("div");',
    '      topDiv.style.cssText = "font-size:10px;color:#94A3B8;margin-bottom:3px;font-weight:600";',
    '      topDiv.textContent = senderTitle;',
    '      d.appendChild(topDiv);',
    '      var bodyRow = document.createElement("div");',
    '      bodyRow.style.cssText = "display:flex;align-items:center;justify-content:space-between;gap:8px";',
    '      var contentSpan = document.createElement("strong");',
    '      contentSpan.style.cssText = "font-size:14px;color:#fff";',
    '      contentSpan.textContent = m.content;',
    '      bodyRow.appendChild(contentSpan);',
    '      if (isCred) {',
    '        var cBtn = document.createElement("button");',
    '        cBtn.className = "copy-btn";',
    '        cBtn.textContent = "Copy";',
    '        (function(val, b) { b.onclick = function() { copyVal(b, val); }; })(m.content, cBtn);',
    '        bodyRow.appendChild(cBtn);',
    '      }',
    '      d.appendChild(bodyRow);',
    '      var statusDiv = document.createElement("div");',
    '      statusDiv.style.cssText = "font-size:9px;color:#10B981;text-align:right;margin-top:2px";',
    '      statusDiv.textContent = "Sent ✓";',
    '      d.appendChild(statusDiv);',
    '      box.appendChild(d);',
    '    }',
    '    box.scrollTop = box.scrollHeight;',
    '  }',
    '  function onServerData(data) {',
    '    if (!data) return;',
    '    if (data.rooms && data.rooms.length > 0) {',
    '      var r = data.rooms[0];',
    '      var sb = document.getElementById("statusBadge");',
    '      if (sb && r.status) sb.textContent = r.status;',
    '    }',
    '    if (data.messages) {',
    '      currentMessages = data.messages;',
    '      renderMessages(currentMessages);',
    '    }',
    '  }',
    '  function poll() {',
    '    if (typeof google !== "undefined" && google.script && google.script.run) {',
    '      google.script.run.withSuccessHandler(onServerData).clientGetRoomData(ROOM_ID);',
    '    } else if (EXEC_URL) {',
    '      fetch(EXEC_URL + "?action=syncAll&room=" + encodeURIComponent(ROOM_ID))',
    '        .then(function(r) { return r.json(); })',
    '        .then(onServerData)',
    '        .catch(function() {});',
    '    }',
    '  }',
    '  function postMsg(type, content, label) {',
    '    var optimistic = {',
    '      id: "TEMP-" + Date.now(),',
    '      roomId: ROOM_ID,',
    '      senderRole: ROLE,',
    '      type: type,',
    '      content: content,',
    '      credentialLabel: label || "",',
    '      timestampMillis: Date.now(),',
    '      status: "ALLOWED"',
    '    };',
    '    currentMessages.push(optimistic);',
    '    renderMessages(currentMessages);',
    '    var payload = { action: "clientSend", roomId: ROOM_ID, senderRole: ROLE, type: type, content: content, credentialLabel: label || "" };',
    '    if (typeof google !== "undefined" && google.script && google.script.run) {',
    '      google.script.run.withSuccessHandler(onServerData).clientSendBrowserMessage(payload);',
    '    } else if (EXEC_URL) {',
    '      fetch(EXEC_URL, { method: "POST", headers: { "Content-Type": "text/plain;charset=utf-8" }, body: JSON.stringify(payload) })',
    '        .then(function(r) { return r.json(); })',
    '        .then(onServerData)',
    '        .catch(function() {});',
    '    }',
    '  }',
    '  function sendCred(type, label, id) {',
    '    var el = document.getElementById(id);',
    '    var v = (el.value || "").trim();',
    '    if (!v) return;',
    '    el.value = "";',
    '    postMsg(type, v, label);',
    '  }',
    '  function sendPreset(txt) {',
    '    postMsg("PRESET", txt, "");',
    '  }',
    '  function sendFree() {',
    '    var el = document.getElementById("freeIn");',
    '    var v = (el.value || "").trim();',
    '    if (!v) return;',
    '    el.value = "";',
    '    postMsg("FREE_TEXT", v, "");',
    '  }',
    '  setInterval(poll, 2500);',
    '  poll();',
    '</script>',
    '</body>',
    '</html>'
  ].join('\n');
}
""".trimIndent()
}
