package com.example.security

import android.util.Log
import com.example.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AntiStealFilter"

data class FilterVerdict(
    val allowed: Boolean,
    val layer: String? = null,
    val reason: String? = null
)

class AntiStealFilter {

    companion object {
        private val BANGLA_DIGITS = setOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

        private val NUMBER_WORDS = setOf(
            // English number words
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            // Banglish number words
            "shunno", "ek", "dui", "tin", "char", "pach", "choy", "shat", "aat", "noy",
            // Bangla script number words
            "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়"
        )

        private val WORD_BOUNDARY_KEYWORDS = setOf(
            "tg", "wp", "wa", "ig", "fb", "imo", "dc", "inb", "dm", "pm", "id", "num", "no",
            "whatsapp", "telegram", "tele", "insta", "instagram", "facebook", "messenger",
            "discord", "viber", "signal", "call", "contact", "number", "phone",
            "taka", "tk", "bdt", "dollar", "usd", "rate", "price", "cost",
            "bkash", "nagad", "rocket", "upay", "binance", "pay", "buy", "cheap",
            "direct", "seller", "supplier", "agent", "broker"
        )

        private val PHRASE_OR_SYMBOL_BLACKLIST = listOf(
            "mail me", "text me", "$", "৳", "@"
        )

        private val LONG_STRIPPED_KEYWORDS = listOf(
            "whatsapp", "telegram", "instagram", "facebook", "messenger", "discord",
            "viber", "signal", "contact", "bkash", "nagad", "rocket", "binance",
            "mailme", "textme"
        )

        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val OTP_REGEX = Regex("^\\d{4,8}$")
        private val NINE_PLUS_DIGITS_REGEX = Regex("\\d{9,}")
        private val TEN_PLUS_DIGITS_REGEX = Regex("\\d{10,}")

        fun validateEmail(input: String): Boolean = EMAIL_REGEX.matches(input.trim())

        fun validatePassword(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed.isNotEmpty() && trimmed.length <= 40 && !trimmed.contains(' ')
        }

        fun validateOtp(input: String): Boolean = OTP_REGEX.matches(input.trim())
    }

    private val aiSchema = Schema.obj(
        properties = mapOf(
            "decision" to Schema.enumeration(
                values = listOf("ALLOW", "BLOCK"),
                description = "Whether to ALLOW or BLOCK the message"
            ),
            "reason" to Schema.string(
                description = "Short explanation of why the message was allowed or blocked"
            )
        )
    )

    private val aiSystemInstruction = content {
        text(
            "You are a strict security filter for an anonymous broker chat room. " +
                "A Supplier and a Client are communicating strictly to complete an account login " +
                "(sharing email, password, or 4-8 digit OTP). BLOCK the message if the sender attempts to: " +
                "1) Share or ask for personal contact info or social media (WhatsApp, Telegram, Facebook, " +
                "Instagram, IMO, phone numbers, usernames) using creative spelling, symbols, Bangla, or Banglish. " +
                "2) Split a phone number or username across multiple messages. " +
                "3) Discuss prices, money, rates, bKash/Nagad, or bypassing the broker. " +
                "4) Ask to communicate outside this chat room. " +
                "Reply strictly in JSON: {\"decision\": \"ALLOW\" | \"BLOCK\", \"reason\": \"short explanation\"}."
        )
    }

    private val geminiGuardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = aiSchema
            },
            systemInstruction = aiSystemInstruction
        )
    }

    /**
     * Layer 1: Instant 0ms Regex & Normalization Check
     */
    fun checkLayer1Regex(
        rawMessage: String,
        senderLastTwoMessages: List<String>
    ): FilterVerdict {
        val text = rawMessage.trim()
        val lower = text.lowercase()

        // 1. Check direct symbol/phrase blacklist (@, $, ৳, mail me, text me)
        for (symbolOrPhrase in PHRASE_OR_SYMBOL_BLACKLIST) {
            if (lower.contains(symbolOrPhrase)) {
                return FilterVerdict(
                    allowed = false,
                    layer = "LAYER 1 (Regex Blacklist)",
                    reason = "Blocked restricted symbol/phrase: \"$symbolOrPhrase\""
                )
            }
        }

        // 2. Check Bangla digits (০১২৩৪৫৬৭৮৯)
        val foundBanglaDigit = text.firstOrNull { it in BANGLA_DIGITS }
        if (foundBanglaDigit != null) {
            return FilterVerdict(
                allowed = false,
                layer = "LAYER 1 (Bangla Digit Filter)",
                reason = "Blocked Bangla digit '$foundBanglaDigit' (anti-phone-disguise rule)"
            )
        }

        // 3. Tokenize by non-letter/non-Bengali characters to inspect words
        val wordTokens = lower.split(Regex("[^a-z0-9\\u0980-\\u09FF]+")).filter { it.isNotBlank() }

        for (token in wordTokens) {
            if (token in NUMBER_WORDS) {
                return FilterVerdict(
                    allowed = false,
                    layer = "LAYER 1 (Word Number Filter)",
                    reason = "Blocked number word \"$token\" (anti-spelled-phone rule)"
                )
            }
            if (token in WORD_BOUNDARY_KEYWORDS) {
                return FilterVerdict(
                    allowed = false,
                    layer = "LAYER 1 (Keyword Blacklist)",
                    reason = "Blocked restricted contact/price keyword \"$token\""
                )
            }
        }

        // 4. Create stripped version removing all spaces, dots, commas, dashes, underscores, parentheses, and special symbols
        val stripped = lower.replace(Regex("[\\s.,\\-_()!#%^&*+=\\[\\]{}|\\\\:;\"'<>/~`?]+"), "")

        // Check stripped string for obfuscated social/payment keywords (e.g. "w.h.a.t.s.a.p.p" or "b-k-a-s-h")
        for (kw in LONG_STRIPPED_KEYWORDS) {
            if (stripped.contains(kw)) {
                return FilterVerdict(
                    allowed = false,
                    layer = "LAYER 1 (Normalized Keyword)",
                    reason = "Blocked disguised contact/payment keyword \"$kw\" in stripped text"
                )
            }
        }

        // 5. Phone Number Rule: Block if stripped string contains 9 or more digits in a row
        if (NINE_PLUS_DIGITS_REGEX.containsMatchIn(stripped)) {
            return FilterVerdict(
                allowed = false,
                layer = "LAYER 1 (9+ Digit Phone Rule)",
                reason = "Stripped text contains 9+ consecutive digits (phone number detected)"
            )
        }

        // Also check total digits in single message >= 9 even if separated by letters
        val digitsInCurrent = text.filter { it.isDigit() }
        if (digitsInCurrent.length >= 9) {
            return FilterVerdict(
                allowed = false,
                layer = "LAYER 1 (9+ Digit Phone Rule)",
                reason = "Message contains ${digitsInCurrent.length} digits (exceeds 8-digit OTP limit)"
            )
        }

        // 6. Multi-Message Number Split Check:
        // Combine the digits of the current message with the digits of the sender's last 2 messages
        if (digitsInCurrent.isNotEmpty() && senderLastTwoMessages.isNotEmpty()) {
            val previousDigits = senderLastTwoMessages.takeLast(2).joinToString("") { msg ->
                msg.filter { it.isDigit() }
            }
            val combinedDigits = previousDigits + digitsInCurrent
            if (previousDigits.isNotEmpty() && TEN_PLUS_DIGITS_REGEX.containsMatchIn(combinedDigits)) {
                return FilterVerdict(
                    allowed = false,
                    layer = "LAYER 1 (Multi-Message Split Phone)",
                    reason = "Combined digits across last 3 messages form a ${combinedDigits.length}-digit number ($combinedDigits)"
                )
            }
        }

        return FilterVerdict(allowed = true)
    }

    /**
     * Layer 2: Gemini AI Guard (`gemini-3.8-flash` via Firebase AI Logic)
     * Inspects current message + last 5 messages in the room for creative spelling, Banglish,
     * split usernames, or off-platform contact attempts.
     */
    suspend fun checkLayer2GeminiGuard(
        currentMessage: String,
        senderRoleName: String,
        recentRoomMessages: List<ChatMessage>
    ): FilterVerdict {
        val contextHistory = if (recentRoomMessages.isEmpty()) {
            "No prior messages in room."
        } else {
            recentRoomMessages.takeLast(5).joinToString("\n") { msg ->
                "[${msg.senderRole.name} - ${msg.type.name}]: ${msg.content}"
            }
        }

        val prompt = buildString {
            appendLine("Recent 5 messages in the room:")
            appendLine(contextHistory)
            appendLine()
            appendLine("Current free-text message from [$senderRoleName]:")
            appendLine("\"$currentMessage\"")
            appendLine()
            appendLine("Evaluate strictly according to your security filter instructions and respond in JSON.")
        }

        return try {
            val response = withTimeout(60_000L) {
                geminiGuardModel.generateContent(prompt)
            }
            val jsonText = response.text ?: return FilterVerdict(allowed = true)
            val json = JSONObject(jsonText)
            val decision = json.optString("decision", "ALLOW").uppercase()
            val reason = json.optString("reason", "Flagged by Gemini AI Security Guard")
            if (decision == "BLOCK") {
                FilterVerdict(
                    allowed = false,
                    layer = "LAYER 2 (Gemini AI Guard)",
                    reason = reason
                )
            } else {
                FilterVerdict(
                    allowed = true,
                    layer = "LAYER 2 (Gemini AI Guard)",
                    reason = reason
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI Guard timed out after 60s", e)
            FilterVerdict(allowed = true, layer = "LAYER 2 (Timeout Fallback)", reason = "AI Guard timed out; Layer 1 passed.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI Guard error", e)
            FilterVerdict(allowed = true, layer = "LAYER 2 (Offline Fallback)", reason = e.localizedMessage)
        }
    }
}
