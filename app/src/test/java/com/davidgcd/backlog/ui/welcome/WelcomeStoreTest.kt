package com.davidgcd.backlog.ui.welcome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeStoreTest {
    @Test
    fun `shown on the first launch of a fresh install only`() {
        assertTrue(shouldShowWelcome(alreadyShown = false, updatedInstall = false))
        assertFalse(shouldShowWelcome(alreadyShown = true, updatedInstall = false))
    }

    @Test
    fun `someone updating the app is not sent to it`() {
        assertFalse(shouldShowWelcome(alreadyShown = false, updatedInstall = true))
    }
}
