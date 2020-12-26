package com.better.alarm.background

import android.content.Context
import com.better.alarm.interfaces.MqttManager
import com.better.alarm.logger.Logger
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.*
import kotlin.jvm.Throws

data class SunriseMessageJson (
    val id: Int,
    val daysOfWeek: BooleanArray,
    val hour: Int,
    val minute: Int,
    val sunriseStartTime: Int,
    val enabled: Boolean
)

class MqttPlugin(
        private val logger: Logger,
        private val mContext: Context,
        private var mqttAndroidClient: MqttAndroidClient? = null,
        private val mqttMessageQueue: Queue<Pair<String, String>> = LinkedList<Pair<String, String>>()
): MqttManager {

    override val topics = mapOf(
            "alarmInfo" to "simplealarm/alarm-info",
    )

    fun init() {
        mqttAndroidClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                logger.debug { "Connected to MQTT server" }
            }
            override fun connectionLost(cause: Throwable) {
                logger.debug { "Lost connection to MQTT server: $cause" }
            }
            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                logger.debug { "MQTT message server acknowledgement" }
            }
            override fun deliveryComplete(token: IMqttDeliveryToken) {}
        })
    }

    override fun connect(serverUri: String) {
        if (mqttAndroidClient != null || mqttAndroidClient?.serverURI != serverUri) {
            mqttAndroidClient = MqttAndroidClient(mContext, serverUri, "simple-alarm-clock")
        }

        mqttAndroidClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                logger.debug { "Connected to MQTT server" }
                try {
                    val (topic, message) = mqttMessageQueue.remove()
                    sendMessage(serverUri, topic, message)
                } catch (e: NoSuchElementException) {
                    logger.debug { "No MQTT message in queue" }
                }
            }
            override fun connectionLost(cause: Throwable) {
                logger.debug { "Lost connection to MQTT server: $cause" }
            }
            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                logger.debug { "MQTT message server acknowledgement" }
            }
            override fun deliveryComplete(token: IMqttDeliveryToken) {}
        })

        try {
            mqttAndroidClient?.connect(createConnectOptions(), null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    logger.debug { "Successfully connected MQTT client" }
                }
                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    logger.debug { "Failed connecting to MQTT client: $exception" }
                }
            })
        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }

    override fun disconnect() {
        mqttAndroidClient?.unregisterResources()
        mqttAndroidClient?.close()
    }

    override fun sendMessage(serverUri: String, topic: String, message: String) {
        if (mqttAndroidClient?.isConnected == true) {
            mqttAndroidClient.let {
                try {
                    val mqttMessage = MqttMessage().apply {
                        payload = message.toByteArray()
                        qos = 1
                    }
                    it?.publish(topic, mqttMessage)
                } catch (e: MqttException) {
                    e.printStackTrace()
                }
            }
        } else {
            mqttMessageQueue.offer(Pair(topic, message))
            connect(serverUri)
        }
    }

    private fun createConnectOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
        }
    }
}
