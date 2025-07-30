package com.example.callkotlin.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.callkotlin.CallApplication
import com.example.callkotlin.R
import com.example.callkotlin.core.CallState
import com.example.callkotlin.core.LiveKitManager
import com.example.callkotlin.databinding.ActivityRingingBinding
import kotlinx.coroutines.launch
import com.example.callkotlin.core.CallType

class RingingActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RingingActivity"
    }
    
    private lateinit var binding: ActivityRingingBinding
    private lateinit var liveKitManager: LiveKitManager
    private var isCustomer: Boolean = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize LiveKit manager
        liveKitManager = (application as CallApplication).liveKitManager
        
        setupUI()
        setupClickListeners()
        observeCallState()
    }
    
    private fun setupUI() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // Support call UI
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvContactName.text = "Customer Support"
            binding.tvCallInfo.text = "Connecting to support team..."
            // Set support icon (you might want to add a support icon)
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
        } else if (isCustomer) {
            // Customer making outgoing call UI
            binding.tvCallingStatus.text = "Connecting..."
            binding.tvContactName.text = "Driver"
            binding.tvCallInfo.text = "Establishing connection to room"
            // Set driver icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_driver_modern)
        } else {
            // Driver receiving call UI
            binding.tvCallingStatus.text = "Incoming Call"
            binding.tvContactName.text = "Customer"
            binding.tvCallInfo.text = "Touch to answer the call"
            // Set customer icon
            binding.ivContactAvatar.setImageResource(R.drawable.ic_person)
            
            // Show answer button for driver
            binding.answerCard.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            endCall()
        }
        
        binding.btnAnswer.setOnClickListener {
            answerCall()
        }
    }
    
    private fun observeCallState() {
        // Check if this is a support call by checking the stored call type
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        val isSupportCall = currentCallType == CallType.SUPPORT
        
        if (isSupportCall) {
            // For support calls, show waiting states then navigate after delay
            lifecycleScope.launch {
                // Show connecting state
                updateUIForConnecting()
                kotlinx.coroutines.delay(1000)
                
                // Show waiting for acceptance state
                updateUIForWaitingForAcceptance()
                kotlinx.coroutines.delay(2000) // Wait 2 seconds for "acceptance"
                
                Log.d(TAG, "Support call accepted, navigating to call screen")
                navigateToCallActivity()
            }
        } else {
            // For regular LiveKit calls, observe call state
            lifecycleScope.launch {
                liveKitManager.callState.collect { state ->
                    Log.d(TAG, "Call state changed: $state")
                    
                    when (state) {
                        CallState.CONNECTING -> {
                            // Still connecting to room
                            Log.d(TAG, "Connecting to room...")
                            updateUIForConnecting()
                        }
                        CallState.CONNECTED -> {
                            // Connected to room, waiting for other participant
                            Log.d(TAG, "Connected to room, waiting for other participant...")
                            updateUIForWaiting()
                        }
                        CallState.WAITING_FOR_ACCEPTANCE -> {
                            // Waiting for driver/support to accept the call
                            Log.d(TAG, "Waiting for acceptance...")
                            updateUIForWaitingForAcceptance()
                        }
                        CallState.IN_CALL -> {
                            // Call connected - navigate to call screen immediately
                            Log.d(TAG, "IN_CALL state reached, navigating to call screen")
                            navigateToCallActivity()
                        }
                        CallState.IDLE -> {
                            // Call ended - go back to main
                            finish()
                        }
                        CallState.ERROR -> {
                            // Connection error
                            showError("Call failed")
                            finish()
                        }
                        else -> {
                            // Stay on ringing screen
                        }
                    }
                }
            }
        }
    }
    
    private fun updateUIForConnecting() {
        binding.tvCallingStatus.text = "Connecting..."
        binding.tvCallInfo.text = "Establishing connection to room"
    }
    
    private fun updateUIForWaiting() {
        binding.tvCallingStatus.text = "Connected"
        binding.tvCallInfo.text = "Waiting for other participant to join"
    }
    
    private fun updateUIForWaitingForAcceptance() {
        val currentCallType = com.example.callkotlin.ui.MainActivity.getCurrentCallType()
        
        if (currentCallType == CallType.DRIVER) {
            // Customer is waiting for driver to accept
            binding.tvCallingStatus.text = "Calling Driver"
            binding.tvCallInfo.text = "Waiting for driver to accept the call..."
        } else if (currentCallType == CallType.SUPPORT) {
            // Customer is waiting for support to accept
            binding.tvCallingStatus.text = "Calling Support"
            binding.tvCallInfo.text = "Waiting for support team to accept the call..."
        } else {
            // Generic waiting message
            binding.tvCallingStatus.text = "Calling"
            binding.tvCallInfo.text = "Waiting for acceptance..."
        }
    }
    
    private fun answerCall() {
        Log.d(TAG, "Call answered by driver")
        binding.answerCard.visibility = android.view.View.GONE
        binding.tvCallInfo.text = "Connecting..."
        
        // The call state should automatically change to IN_CALL
        // when both participants are connected
    }
    
    private fun endCall() {
        Log.d(TAG, "Call ended by user")
        liveKitManager.disconnect()
        finish()
    }
    
    private fun navigateToCallActivity() {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
        finish()
    }
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        // Prevent back button during ringing
        endCall()
        super.onBackPressed()
    }
} 