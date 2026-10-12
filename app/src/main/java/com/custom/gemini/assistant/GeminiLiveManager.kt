package com.custom.gemini.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveManager(
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val thinking: Boolean,
    private val listener: LiveEventsListener
) {
    interface LiveEventsListener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onTranscription(text: String, isModel: Boolean)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var isMuted = false

    private val sampleRate = 16000
    private var audioTrack: AudioTrack? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            24000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                listener.onConnected()
                sendSetupConfig()
                startAudioRecording()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Connection failure")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                listener.onDisconnected(reason)
            }
        })
    }

    private fun sendSetupConfig() {
        try {
            val setupJson = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", "models/$model")
                    val genConfig = JSONObject().apply {
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", voice)
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                        if (thinking) {
                            val thinkingConfig = JSONObject().apply {
                                put("thinkingBudget", 2048)
                            }
                            put("thinkingConfig", thinkingConfig)
                        }
                    }
                    put("generationConfig", genConfig)
                }
                put("setup", setup)
            }
            webSocket?.send(setupJson.toString())
        } catch (e: Exception) {
            listener.onError("Setup serialization error: ${e.message}")
        }
    }

    fun setMute(mute: Boolean) {
        isMuted = mute
    }

    private fun startAudioRecording() {
        recordingJob = scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                audioRecord.startRecording()
                val buffer = ByteArray(2048)
                while (isActive) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0 && !isMuted) {
                        val base64Chunk = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                        val realtimeInput = JSONObject().apply {
                            val input = JSONObject().apply {
                                val mediaChunks = org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", base64Chunk)
                                    })
                                }
                                put("mediaChunks", mediaChunks)
                            }
                            put("realtimeInput", input)
                        }
                        webSocket?.send(realtimeInput.toString())
                    }
                }
            } catch (e: Exception) {
                // Audio recording stopped or permission missing
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {}
            }
        }
    }

    fun sendScreenshot(jpegBytes: ByteArray) {
        scope.launch {
            try {
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val realtimeInput = JSONObject().apply {
                    val input = JSONObject().apply {
                        val mediaChunks = org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        }
                        put("mediaChunks", mediaChunks)
                    }
                    put("realtimeInput", input)
                }
                webSocket?.send(realtimeInput.toString())
                listener.onTranscription("Screenshot sent to Gemini", false)
            } catch (e: Exception) {
                listener.onError("Screenshot send failed: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            val text = part.getString("text")
                            listener.onTranscription(text, true)
                        }
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val data = inlineData.getString("data")
                            val audioBytes = Base64.decode(data, Base64.DEFAULT)
                            audioTrack?.write(audioBytes, 0, audioBytes.size)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-critical JSON parse error
        }
    }

    fun disconnect() {
        recordingJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
    }
}
