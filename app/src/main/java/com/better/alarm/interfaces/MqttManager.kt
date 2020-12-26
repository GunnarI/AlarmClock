package com.better.alarm.interfaces

import com.better.alarm.model.AlarmValue

interface MqttManager {
    fun connect(serverUri: String)
    fun disconnect()
    fun sendMessage(serverUri: String, topic: String, message: String)

    val topics: Map<String, String>
}