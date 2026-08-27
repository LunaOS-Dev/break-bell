package com.breakbell.app.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.breakbell.app.data.AppState
import com.breakbell.app.data.BreakBellStore
import com.breakbell.app.data.BridgeConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

enum class BridgeConnectionResult {
    CONNECTED,
    TOKEN_REJECTED,
    UNREACHABLE,
    SERVER_ERROR,
}

object AgentBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun publish(context: Context, state: AppState) {
        val config = BreakBellStore(context).bridgeConfig()
        if (!config.isConfigured) return
        executor.execute { send(state, config) }
    }

    fun verify(
        state: AppState,
        config: BridgeConfig,
        onResult: (BridgeConnectionResult) -> Unit,
    ) {
        executor.execute {
            val result = send(state, config)
            mainHandler.post { onResult(result) }
        }
    }

    private fun send(state: AppState, config: BridgeConfig): BridgeConnectionResult {
        if (!config.isConfigured) return BridgeConnectionResult.UNREACHABLE
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("${config.address.trim().trimEnd('/')}/v1/status")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${config.token.trim()}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(state.toJson(System.currentTimeMillis()).toByteArray(Charsets.UTF_8))
            }
            when (connection.responseCode) {
                in 200..299 -> BridgeConnectionResult.CONNECTED
                HttpURLConnection.HTTP_UNAUTHORIZED -> BridgeConnectionResult.TOKEN_REJECTED
                else -> BridgeConnectionResult.SERVER_ERROR
            }
        } catch (_: Exception) {
            BridgeConnectionResult.UNREACHABLE
        } finally {
            connection?.disconnect()
        }
    }

    private fun AppState.toJson(updatedAt: Long): String = """
        {
          "updatedAt": $updatedAt,
          "isActive": $isActive,
          "phase": "${phase.name}",
          "workdayStartedAt": $workdayStartedAt,
          "phaseStartedAt": $phaseStartedAt,
          "phaseEndsAt": $phaseEndsAt,
          "currentBlockIndex": $currentBlockIndex,
          "completedBreaks": $completedBreaks,
          "currentBlock": {
            "name": "${currentBlock.displayName}",
            "workMinutes": ${currentBlock.workMinutes},
            "breakMinutes": ${currentBlock.breakMinutes}
          }
        }
    """.trimIndent()
}
