package com.trust3.xcpro.weglide.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeGlidePkceFactoryTest {

    @Test
    fun create_returnsNonBlankVerifierChallengeAndState() {
        val pkce = WeGlidePkceFactory.create()

        assertFalse(pkce.codeVerifier.isBlank())
        assertFalse(pkce.codeChallenge.isBlank())
        assertFalse(pkce.state.isBlank())
    }

    @Test
    fun create_generatesDistinctStateValues() {
        val first = WeGlidePkceFactory.create()
        val second = WeGlidePkceFactory.create()

        assertNotEquals(first.state, second.state)
    }
}
