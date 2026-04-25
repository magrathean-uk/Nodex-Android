package com.nodex.client.domain.model

data class ProcessInfo(
    val pid: Int,
    val ppid: Int = 0,
    val user: String,
    val cpuPercent: Double,
    val memPercent: Double,
    val command: String
)
