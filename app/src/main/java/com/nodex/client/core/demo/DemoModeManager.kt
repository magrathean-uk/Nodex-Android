package com.nodex.client.core.demo

import com.nodex.client.data.prefs.UserPreferences
import com.nodex.client.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoModeManager @Inject constructor(
    private val userPreferences: UserPreferences,
    private val serverRepository: ServerRepository
) {

    val isDemoMode: Flow<Boolean> = userPreferences.isDemoMode

    suspend fun enterDemoMode() {
        userPreferences.setDemoMode(true)
        userPreferences.setOnboardingCompleted(true)
        ensureDemoServer()
    }

    suspend fun exitDemoMode() {
        userPreferences.setDemoMode(false)
        val demoServer = serverRepository.getServerById(DemoData.DEMO_SERVER_ID)
        if (demoServer != null) {
            serverRepository.deleteServer(demoServer)
        }
    }

    suspend fun ensureDemoServer() {
        val existing = serverRepository.getServerById(DemoData.DEMO_SERVER_ID)
        if (existing == null) {
            serverRepository.addServer(DemoData.demoServer)
        }
    }
}
