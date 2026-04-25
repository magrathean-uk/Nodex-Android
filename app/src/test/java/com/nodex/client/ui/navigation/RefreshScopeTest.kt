package com.nodex.client.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshScopeTest {

    @Test
    fun `docker route maps to docker-only refresh`() {
        val scope = RefreshScope.forRoute(Screen.Docker.route)

        assertEquals(RefreshScope.DOCKER, scope)
        assertFalse(scope.includesSlowPoll)
        assertTrue(scope.includesDocker)
    }

    @Test
    fun `settings route maps to full refresh`() {
        val scope = RefreshScope.forRoute(Screen.Settings.route)

        assertEquals(RefreshScope.ALL, scope)
        assertTrue(scope.includesFastPoll)
        assertTrue(scope.includesSlowPoll)
        assertTrue(scope.includesDocker)
    }

    @Test
    fun `all refresh includes docker refresh`() {
        assertTrue(RefreshScope.ALL.includesDocker)
    }

    @Test
    fun `overview route maps to metrics refresh only`() {
        val scope = RefreshScope.forRoute(Screen.Overview.route)

        assertEquals(RefreshScope.OVERVIEW, scope)
        assertTrue(scope.includesFastPoll)
        assertFalse(scope.includesSlowPoll)
        assertFalse(scope.includesDocker)
    }
}
