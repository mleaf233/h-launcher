package org.love2d.android.util

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.love2d.android.LoveApplication
import org.love2d.android.net.WsMessage
import java.util.concurrent.TimeUnit

/**
 * ClassName WebSocketManager
 * Description WebSocket连接管理器，提供线程安全的连接、断开和消息处理
 * Create by hjr
 * Date 2025/7/9 15:36
 */
object WebSocketManager {
    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var currentStatus = "未连接"

    // 线程安全
    private val connectionMutex = Mutex()

    // 复用OkHttpClient实例，避免重复创建和资源浪费
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // 心跳间隔
            .build()
    }

    private val jsonAdapter by lazy {
        val build = Moshi.Builder().build()
        val jsonAdapter = build.adapter(WsMessage::class.java)
        jsonAdapter
    }

    /**
     * 连接WebSocket，线程安全
     */
    suspend fun connect(
        url: String,
        onStatusUpdate: (String) -> Unit,
    ) = connectionMutex.withLock {
        // 避免重复连接
        if (isConnecting || webSocket != null) {
            onStatusUpdate("已连接")
            currentStatus = "已连接"
            return@withLock
        }

        isConnecting = true

        try {
            val request = Request.Builder().url(url).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    onStatusUpdate("已连接")
                    currentStatus = "已连接"
                    sendPing()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    messageHandler(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebSocketManager", "WebSocket连接失败", t)
                    onStatusUpdate("连接失败：${t.localizedMessage}")
                    currentStatus = "连接失败：${t.localizedMessage}"
                    isConnecting = false
                    webSocket = null
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocketManager", "WebSocket连接关闭: $code, $reason")
                    onStatusUpdate("连接关闭：$reason")
                    currentStatus = "连接关闭：$reason"
                    isConnecting = false
                    webSocket = null
                }
            }

            // 使用复用的OkHttpClient
            webSocket = okHttpClient.newWebSocket(request, listener)

        } catch (e: Exception) {
            Log.e("WebSocketManager", "创建WebSocket连接时发生异常", e)
            onStatusUpdate("连接失败：${e.localizedMessage}")
            currentStatus = "连接失败：${e.localizedMessage}"
            isConnecting = false
            webSocket = null
        }
    }

    fun messageHandler(message: String) {
        try {
            val wsMessage = jsonAdapter.fromJson(message)
            when (wsMessage?.type) {
                "pong" -> {
                    //客户端心跳 移动端回复
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(2500)
                        sendPing()
                    }
                }

                "synced" -> {
                    ZipManager.decodeBase64AndUnzip(LoveApplication.appContext, wsMessage.payload)
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocketManager", "处理消息时发生异常", e)
        }
    }

    /**
     * 断开WebSocket连接，线程安全
     */
    suspend fun disconnect(disconnect: () -> Unit) = connectionMutex.withLock {
        try {
            webSocket?.let { ws ->
                val success = ws.close(1000, "客户端主动关闭")
                Log.d("WebSocketManager", "WebSocket关闭结果: $success")
            }
        } catch (e: Exception) {
            Log.e("WebSocketManager", "关闭WebSocket时发生异常", e)
        } finally {
            webSocket = null
            isConnecting = false
            currentStatus = "未连接"
            disconnect()
        }
    }

    /**
     * 发送心跳包
     */
    private fun sendPing() {
        try {
            webSocket?.let { ws ->
                val message = JSONObject()
                message.put("type", "ping")
                message.put("payload", System.currentTimeMillis().toString())
                val sent = ws.send(message.toString())
                if (!sent) {
                    Log.w("WebSocketManager", "发送ping消息失败")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocketManager", "发送ping消息时发生异常", e)
        }
    }

    /**
     * 发送同步消息
     */
    fun sendSync() {
        try {
            webSocket?.let { ws ->
                val message = JSONObject()
                message.put("type", "sync")
                message.put("payload", System.currentTimeMillis().toString())
                val sent = ws.send(message.toString())
                if (!sent) {
                    Log.w("WebSocketManager", "发送sync消息失败")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocketManager", "发送sync消息时发生异常", e)
        }
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = webSocket != null

    /**
     * 获取当前连接状态
     */
    fun getCurrentStatus(): String = currentStatus

    /**
     * 安全日志输出
     */
    private fun showLog(message: Any) {
        Log.d("WebSocketManager", message.toString())
    }
}