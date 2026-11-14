package com.example

import com.example.security.AntiStealFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val filter = AntiStealFilter()

    @Test
    fun layer1_blocksPhoneNumbersAndAllowsShortText() {
        // 9+ digits with spaces and dashes should be blocked
        val phoneAttempt = filter.checkLayer1Regex("my num is 017 12-34.56.78", emptyList())
        assertFalse(phoneAttempt.allowed)

        // Normal login status message should be allowed
        val cleanText = filter.checkLayer1Regex("Logged in to the profile screen now", emptyList())
        assertTrue(cleanText.allowed)
    }

    @Test
    fun layer1_blocksMultiMessageSplitPhoneNumbers() {
        val lastTwo = listOf("code 0171", "next 2345")
        val splitAttempt = filter.checkLayer1Regex("last 678", lastTwo)
        assertFalse(splitAttempt.allowed)
    }

    @Test
    fun layer1_blocksBanglaAndSpelledNumbersAndKeywords() {
        assertFalse(filter.checkLayer1Regex("call me ০১৭১২", emptyList()).allowed)
        assertFalse(filter.checkLayer1Regex("zero one seven one", emptyList()).allowed)
        assertFalse(filter.checkLayer1Regex("ek dui tin char", emptyList()).allowed)
        assertFalse(filter.checkLayer1Regex("add me on whatsapp bro", emptyList()).allowed)
        assertFalse(filter.checkLayer1Regex("send bkash 500 tk", emptyList()).allowed)
    }

    @Test
    fun structuredCredentialValidation_enforcesStrictFormats() {
        assertTrue(AntiStealFilter.validateEmail("client.rahim@gmail.com"))
        assertFalse(AntiStealFilter.validateEmail("not-an-email"))

        assertTrue(AntiStealFilter.validatePassword("Pass#1234!"))
        assertFalse(AntiStealFilter.validatePassword("has space inside"))

        assertTrue(AntiStealFilter.validateOtp("482910"))
        assertFalse(AntiStealFilter.validateOtp("123")) // too short
        assertFalse(AntiStealFilter.validateOtp("123456789")) // 9 digits blocked
    }
}
