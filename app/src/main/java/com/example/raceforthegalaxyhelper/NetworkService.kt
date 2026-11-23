package com.example.raceforthegalaxyhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.Timer
import java.util.TimerTask

data class NetworkEvent(val event: String, val remoteId: String, val message: String? = null)

object NetworkEventBus {
    val events = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 128)
}

class NetworkService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var webSocketServer: AppWebSocketServer? = null
    private var webSocketClient: AppWebSocketClient? = null
    private var reconnectTimer: Timer? = null
    private var targetIp: String? = null
    private var targetPort: Int = 8080

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWifiLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        when (action) {
            ACTION_START_SERVER -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startWebSocketServer(port)
            }
            ACTION_STOP_SERVER -> {
                stopWebSocketServer()
                stopSelf()
            }

            ACTION_START_CLIENT -> {
                val ip = intent.getStringExtra(EXTRA_IP)
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                if (ip != null) {
                    targetIp = ip
                    targetPort = port
                    startWebSocketClient(ip, port)
                }
            }

            ACTION_STOP_CLIENT -> {
                stopWebSocketClient()
                stopSelf()
            }

            ACTION_SEND_MESSAGE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                val ip = intent.getStringExtra(EXTRA_IP)
                if (message != null) sendMessage(message, ip)
            }
        }

        return START_STICKY
    }

    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "RaceHelper:WifiLock"
            )
            wifiLock?.acquire()
        } catch (e: Exception) {
            Log.e("NetworkService", "WifiLock error: ${e.message}")
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.e("NetworkService", "WifiLock release error: ${e.message}")
        }
    }

    private fun startWebSocketServer(port: Int) {
        if (webSocketServer == null) {
            try {
                webSocketServer = AppWebSocketServer(port)
                webSocketServer?.start()
                Log.d("NetworkService", "WebSocketServer started on $port")
                Toast.makeText(this, "Master Server started (Port $port)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("NetworkService", "Server start error: ${e.message}")
                Toast.makeText(this, "Error starting server: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopWebSocketServer() {
        try {
            webSocketServer?.stop(1000)
        } catch (e: Exception) {
            Log.e("NetworkService", "Server stop error: ${e.message}")
        } finally {
            webSocketServer = null
        }
    }

    private fun startWebSocketClient(ip: String, port: Int) {
        stopWebSocketClient()

        try {
            val uri = URI("ws://$ip:$port")
            webSocketClient = AppWebSocketClient(uri)
            webSocketClient?.setConnectionLostTimeout(10)
            webSocketClient?.connect()
        } catch (e: Exception) {
            Log.e("NetworkService", "Client start error: ${e.message}")
        }
    }

    private fun stopWebSocketClient() {
        reconnectTimer?.cancel()
        reconnectTimer = null

        try {
            webSocketClient?.close()
        } catch (e: Exception) {
            Log.e("NetworkService", "Client stop error: ${e.message}")
        } finally {
            webSocketClient = null
        }
    }

    private fun sendMessage(message: String, targetIp: String? = null) {
        try {
            if (targetIp != null && webSocketServer != null) {
                val normalizedTarget = normalizeIp(targetIp)
                val connections = webSocketServer?.connections
                val client = connections?.find {
                    val addr = it.remoteSocketAddress
                    addr != null && normalizeIp(addr.address.hostAddress ?: "") == normalizedTarget
                }
                if (client != null) {
                    client.send(message)
                } else {
                    Log.w("NetworkService", "Could not find client with IP: $normalizedTarget. Active connections: ${connections?.size}")
                    connections?.forEach {
                        Log.d("NetworkService", "  Connection: ${normalizeIp(it.remoteSocketAddress?.address?.hostAddress ?: "")}")
                    }
                }
            } else {
                webSocketServer?.broadcast(message)
            }

            if (webSocketClient?.isOpen == true) {
                webSocketClient?.send(message)
            }
        } catch (e: Exception) {
            Log.e("NetworkService", "Send error: ${e.message}")
        }
    }

    private fun normalizeIp(ip: String): String {
        return when {
            ip.contains(":") -> if (ip.contains(".")) ip.substringAfterLast(":") else ip
            else -> ip
        }.trim().removePrefix("/")
    }

    override fun onDestroy() {
        stopWebSocketServer()
        stopWebSocketClient()
        releaseWifiLock()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Race For The Galaxy Helper")
            .setContentText("Network connection active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RaceHelper Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendBroadcastToActivity(event: String, remoteIp: String, message: String? = null) {
        NetworkEventBus.events.tryEmit(NetworkEvent(event, remoteIp, message))
        
        // Keep old broadcast for compatibility if needed, but not strictly necessary now
        val intent = Intent(ACTION_WS_EVENT).apply {
            putExtra("event", event)
            putExtra("remoteId", remoteIp)
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }

    inner class AppWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

        init {
            isReuseAddr = true
            setConnectionLostTimeout(20)
        }

        private fun getRemoteIp(conn: WebSocket): String {
            val addr = conn.remoteSocketAddress ?: return "unknown"
            val ip = addr.address.hostAddress ?: "unknown"
            return normalizeIp(ip)
        }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val ip = getRemoteIp(conn)
            sendBroadcastToActivity(EVENT_OPEN, ip)
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            val ip = getRemoteIp(conn)
            sendBroadcastToActivity(EVENT_CLOSE, ip)
        }

        override fun onMessage(conn: WebSocket, message: String) {
            val ip = getRemoteIp(conn)
            sendBroadcastToActivity(EVENT_MESSAGE, ip, message)
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e("WebSocketServer", "Error: ${ex.message}")
        }

        override fun onStart() {
            Log.d("WebSocketServer", "Server started")
        }
    }

    inner class AppWebSocketClient(uri: URI) : WebSocketClient(uri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            sendBroadcastToActivity(EVENT_OPEN, normalizeIp(uri.host ?: ""))
        }

        override fun onMessage(message: String?) {
            sendBroadcastToActivity(EVENT_MESSAGE, normalizeIp(uri.host ?: ""), message)
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            sendBroadcastToActivity(EVENT_CLOSE, normalizeIp(uri.host ?: ""))

            if (remote && targetIp != null) {
                reconnectTimer?.cancel()
                reconnectTimer = Timer()
                reconnectTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        startWebSocketClient(targetIp!!, targetPort)
                    }
                }, 2000)
            }
        }

        override fun onError(ex: Exception?) {
            sendBroadcastToActivity(EVENT_ERROR, normalizeIp(uri.host ?: ""), ex?.message)
        }
    }

    companion object {
        const val CHANNEL_ID = "RaceHelperServiceChannel"

        const val ACTION_START_SERVER = "com.example.raceforthegalaxyhelper.START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.raceforthegalaxyhelper.STOP_SERVER"
        const val ACTION_START_CLIENT = "com.example.raceforthegalaxyhelper.START_CLIENT"
        const val ACTION_STOP_CLIENT = "com.example.raceforthegalaxyhelper.STOP_CLIENT"
        const val ACTION_SEND_MESSAGE = "com.example.raceforthegalaxyhelper.SEND_MESSAGE"
        const val ACTION_WS_EVENT = "com.example.raceforthegalaxyhelper.WS_EVENT"

        const val EXTRA_IP = "ip"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PORT = "port"

        const val EVENT_OPEN = "EVENT_OPEN"
        const val EVENT_CLOSE = "EVENT_CLOSE"
        const val EVENT_MESSAGE = "EVENT_MESSAGE"
        const val EVENT_ERROR = "EVENT_ERROR"
    }
}
