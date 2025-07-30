package com.example.callkotlin.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.callkotlin.CallApplication
import com.example.callkotlin.R
import com.example.callkotlin.core.CallState
import com.example.callkotlin.core.LiveKitManager
import com.example.callkotlin.databinding.ActivityCallBinding
import kotlinx.coroutines.launch
import android.media.AudioManager
import android.content.Context
import com.example.callkotlin.core.CallType

class CallActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "CallActivity"
    }
    
    private lateinit var binding: ActivityCallBinding
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var audioManager: AudioManager
    private var isCustomer: Boolean = true
    private var isMuted: Boolean = false
    private var isEndingCall = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize LiveKit manager and audio manager
        liveKitManager = (application as CallApplication).liveKitManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        setupUI()
        setupClickListeners()
        observeCallState()
        
        // Start call timer
        startCallTimer()
        
        // Setup audio for voice call
        setupAudioForCall()
        
        // Force enable audio to ensure it's working
        liveKitManager.forceEnableAudio()
    }
    
    private fun setupAudioForCall() {
        Log.d(TAG, "=== SETUP AUDIO FOR CALL ===")
        
        try {
            // 1. Set audio mode to communication mode for voice calls
            Log.d(TAG, "Setting audio mode to MODE_IN_COMMUNICATION...")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Audio mode set to: ${audioManager.mode}")
            
            // 2. Enable speakerphone for better audio quality
            Log.d(TAG, "Enabling speakerphone...")
            audioManager.isSpeakerphoneOn = true
            Log.d(TAG, "Speakerphone enabled: ${audioManager.isSpeakerphoneOn}")
            
            // 3. Ensure microphone is not muted
            Log.d(TAG, "Ensuring microphone is not muted...")
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            
            // 4. Check microphone permission
            val hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Microphone permission granted: $hasMicrophonePermission")
            
            // 5. Log final audio status
            Log.d(TAG, "=== FINAL AUDIO STATUS ===")
            Log.d(TAG, "Audio mode: ${audioManager.mode}")
            Log.d(TAG, "Speakerphone on: ${audioManager.isSpeakerphoneOn}")
            Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
            Log.d(TAG, "Microphone permission: $hasMicrophonePermission")
            
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION && 
                audioManager.isSpeakerphoneOn && 
                !audioManager.isMicrophoneMute && 
                hasMicrophonePermission) {
                Log.d(TAG, "✅ AUDIO SETUP COMPLETE - Voice call should work properly!")
            } else {
                Log.w(TAG, "⚠️ AUDIO SETUP ISSUE DETECTED:")
                Log.w(TAG, "   - Audio mode: ${audioManager.mode} (should be ${AudioManager.MODE_IN_COMMUNICATION})")
                Log.w(TAG, "   - Speakerphone: ${audioManager.isSpeakerphoneOn} (should be true)")
                Log.w(TAG, "   - Microphone muted: ${audioManager.isMicrophoneMute} (should be false)")
                Log.w(TAG, "   - Permission: $hasMicrophonePermission (should be true)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio for call: ${e.message}")
            Log.e(TAG, "Audio setup error:", e)
        }
        
        Log.d(TAG, "=== END AUDIO SETUP ===")
    }
    
    private fun checkAudioStatus() {
        Log.d(TAG, "=== AUDIO STATUS CHECK ===")
        
        // Check microphone permission
        val hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Microphone permission: $hasMicrophonePermission")
        
        // Check audio manager status
        Log.d(TAG, "Audio mode: ${audioManager.mode}")
        Log.d(TAG, "Speakerphone on: ${audioManager.isSpeakerphoneOn}")
        Log.d(TAG, "Microphone muted: ${audioManager.isMicrophoneMute}")
        
        // Check if we're in communication mode
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            Log.w(TAG, "⚠️ Audio not in communication mode! Current mode: ${audioManager.mode}")
            Log.w(TAG, "Setting audio mode to MODE_IN_COMMUNICATION...")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            Log.d(TAG, "✅ Audio in communication mode")
        }
        
        // Enable speakerphone for better audio
        if (!audioManager.isSpeakerphoneOn) {
            Log.d(TAG, "Enabling speakerphone for better audio...")
            audioManager.isSpeakerphoneOn = true
        }
        
        Log.d(TAG, "=== END AUDIO STATUS CHECK ===")
    }
    
    private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // Support call UI
            binding.tvContactName.text = "Customer Support"
            binding.tvCallStatus.text = "Connected to Support"
            // Set support icon (you might want to add a support icon)
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        } else if (isCustomer) {
            // Driver call UI
            binding.tvContactName.text = "Driver"
            binding.tvCallStatus.text = "Connected to Driver"
            // Set driver icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
        } else {
            // Driver receiving call UI
            binding.tvContactName.text = "Customer"
            binding.tvCallStatus.text = "Connected to Customer"
            // Set customer icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        }
        
        binding.tvCallDuration.text = "00:00"
        updateMuteButton()
    }
    
    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        binding.btnMute.setOnClickListener {
            toggleMute()
        }
        
        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }
    }
    
    private fun observeCallState() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // For support calls, don't observe LiveKit states since they use SIP
            // Just start the call timer and handle UI updates
            Log.d(TAG, "Support call - not observing LiveKit states")
        } else {
            // For regular LiveKit calls, observe call state
            lifecycleScope.launch {
                liveKitManager.callState.collect { state ->
                    Log.d(TAG, "Call state changed: $state")
                    
                    when (state) {
                        CallState.IDLE -> {
                            // Call ended - finish activity
                            finish()
                        }
                        CallState.ERROR -> {
                            // Connection error
                            showError("Call connection lost")
                            finish()
                        }
                        else -> {
                            // Continue call
                        }
                    }
                }
            }
        }
    }
    
    private fun toggleMute() {
        isMuted = !isMuted
        
        if (isMuted) {
            liveKitManager.muteAudio()
            Log.d(TAG, "Audio muted")
        } else {
            liveKitManager.unmuteAudio()
            Log.d(TAG, "Audio unmuted")
        }
        
        updateMuteButton()
    }
    
    private fun updateMuteButton() {
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off)
            binding.btnMute.setBackgroundResource(R.drawable.button_ripple_decline)
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_microphone)
            binding.btnMute.setBackgroundResource(R.drawable.button_ripple_control)
        }
    }
    
    private fun toggleSpeaker() {
        // Implementation for speaker toggle would go here
        // This is a placeholder for speaker functionality
        Log.d(TAG, "Speaker toggle not implemented yet")
        showError("Speaker toggle coming soon")
    }
    
    private fun endCall() {
        if (isEndingCall) {
            Log.d(TAG, "⚠️ End call already in progress, ignoring duplicate click")
            return
        }
        
        isEndingCall = true
        Log.d(TAG, "📞 Call ended by user")
        Log.d(TAG, "🔍 Activity instance: ${this.hashCode()}")
        
        // Check if this is a support call
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            Log.d(TAG, "🆘 Ending support call...")
        } else {
            Log.d(TAG, "🚗 Ending driver call...")
        }
        
        // Set flag to prevent navigation when state changes to IDLE
        com.example.callkotlin.ui.MainActivity.setEndingCallProgrammatically(true)
        
        // Restore audio settings
        restoreAudioSettings()
        
        Log.d(TAG, "🔄 Calling liveKitManager.disconnect()...")
        liveKitManager.disconnect()
        Log.d(TAG, "✅ liveKitManager.disconnect() completed")
        
        Log.d(TAG, "🏁 Finishing activity...")
        finish()
    }
    
    private fun restoreAudioSettings() {
        Log.d(TAG, "=== RESTORING AUDIO SETTINGS ===")
        
        try {
            // Restore normal audio mode
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio mode restored to MODE_NORMAL")
            
            // Turn off speakerphone
            audioManager.isSpeakerphoneOn = false
            Log.d(TAG, "Speakerphone turned off")
            
            Log.d(TAG, "✅ Audio settings restored successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio settings: ${e.message}")
        }
        
        Log.d(TAG, "=== END AUDIO RESTORE ===")
    }
    
    private fun startCallTimer() {
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateCallDuration()
            }
        }
    }
    
    private fun updateCallDuration() {
        val durationSeconds = liveKitManager.getCurrentCallDuration()
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        binding.tvCallDuration.text = String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        // Prevent accidental back press during call
        // User must press end call button
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Ensure audio settings are restored when activity is destroyed
        restoreAudioSettings()
    }
} 