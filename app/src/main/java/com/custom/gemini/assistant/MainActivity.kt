package com.custom.gemini.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager

    private val models = arrayOf(
        "gemini-3.8-live",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    )

    private val voices = arrayOf(
        "Puck",
        "Charon",
        "Kore",
        "Fenrir",
        "Aoede"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val spinnerModel = findViewById<Spinner>(R.id.spinnerModel)
        val spinnerVoice = findViewById<Spinner>(R.id.spinnerVoice)
        val switchThinking = findViewById<SwitchCompat>(R.id.switchThinking)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)
        val btnTestOverlay = findViewById<Button>(R.id.btnTestOverlay)
        val btnGrantOverlay = findViewById<Button>(R.id.btnGrantOverlay)
        val btnGrantAccessibility = findViewById<Button>(R.id.btnGrantAccessibility)
        val btnGrantAudio = findViewById<Button>(R.id.btnGrantAudio)

        // Setup Spinners
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        spinnerModel.adapter = modelAdapter
        val modelIndex = models.indexOf(prefs.modelName).let { if (it >= 0) it else 0 }
        spinnerModel.setSelection(modelIndex)

        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        spinnerVoice.adapter = voiceAdapter
        val voiceIndex = voices.indexOf(prefs.voiceName).let { if (it >= 0) it else 0 }
        spinnerVoice.setSelection(voiceIndex)

        // Load saved preferences
        etApiKey.setText(prefs.apiKey)
        switchThinking.isChecked = prefs.thinkingMode

        btnSaveSettings.setOnClickListener {
            prefs.apiKey = etApiKey.text.toString().trim()
            prefs.modelName = spinnerModel.selectedItem.toString()
            prefs.voiceName = spinnerVoice.selectedItem.toString()
            prefs.thinkingMode = switchThinking.isChecked
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        }

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Gemini Assistant' service in the list", Toast.LENGTH_LONG).show()
        }

        btnGrantAudio.setOnClickListener {
            requestAudioPermission()
        }

        btnTestOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant Microphone permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_TOGGLE
            }
            startService(intent)
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        } else {
            Toast.makeText(this, "Microphone permission already granted!", Toast.LENGTH_SHORT).show()
        }
    }
}
