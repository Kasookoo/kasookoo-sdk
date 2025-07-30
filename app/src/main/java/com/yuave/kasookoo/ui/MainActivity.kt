package com.yuave.kasookoo.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuave.kasookoo.CallApplication
import com.yuave.kasookoo.R
import com.yuave.kasookoo.core.CallState
import com.yuave.kasookoo.core.CallType
import com.yuave.kasookoo.core.LiveKitManager
import com.yuave.kasookoo.core.RoomConnectionStatus
import com.yuave.kasookoo.data.CallRepository
import com.yuave.kasookoo.databinding.ActivityMainBinding
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.view.View
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
        
        // Static variables for global state management
        private var currentSupportCallParticipantIdentity: String? = null
        private var currentCallType: CallType? = null
        private var isEndingCallProgrammatically = false
        
        fun setCurrentSupportCallParticipantIdentity(identity: String?) {
            currentSupportCallParticipantIdentity = identity
            Log.d(TAG, "Support call participant identity set to: $identity")
        }
        
        fun getCurrentSupportCallParticipantIdentity(): String? {
            return currentSupportCallParticipantIdentity
        }
        
        fun setCurrentCallType(callType: CallType?) {
            currentCallType = callType
            Log.d(TAG, "Current call type set to: $callType")
        }
        
        fun getCurrentCallType(): CallType? {
            return currentCallType
        }
        
        fun setEndingCallProgrammatically(ending: Boolean) {
            isEndingCallProgrammatically = ending
            Log.d(TAG, "isEndingCallProgrammatically set to: $ending")
        }
        
        fun resetEndingCallProgrammatically() {
            isEndingCallProgrammatically = false
            Log.d(TAG, "isEndingCallProgrammatically reset to false")
        }
        
        // Static values for testing
        private const val ROOM_NAME = "sdk-room"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var liveKitManager: LiveKitManager
    private lateinit var repository: CallRepository
    
    private var isCustomer: Boolean = true
    private val deviceId = "device_${android.os.Build.SERIAL ?: System.currentTimeMillis()}"
    
    // Use static room name for testing
    private fun getRoomName(): String {
        val roomName = ROOM_NAME // Always use "sdk-room"
        Log.d(TAG, "Using room name: $roomName")
        return roomName
    }
    
    // Generate unique participant identity for each device
    private fun getParticipantIdentity(): String {
        val identity = if (isCustomer) {
            "customer_${deviceId}"
        } else {
            "driver_${deviceId}"
        }
        Log.d(TAG, "Generated participant identity: $identity (isCustomer: $isCustomer)")
        return identity
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user type from intent
        isCustomer = intent.getBooleanExtra("isCustomer", true)
        
        // Initialize components
        liveKitManager = (application as CallApplication).liveKitManager
        repository = CallRepository()
        
        // Request permissions
        requestPermissions()
        
        // Setup UI based on user type
        setupUI()
        
        // Setup LiveKit state observers
        setupObservers()
        
        // Setup driver mode if user is driver
        if (!isCustomer) {
            setupDriverMode()
        }
    }
    
    private fun setupUI() {
        if (isCustomer) {
            // Customer UI - show both call buttons
            binding.btnCallDriver.setOnClickListener {
                initiateCall(CallType.DRIVER)
            }
            
            binding.btnCallSupport.setOnClickListener {
                initiateCall(CallType.SUPPORT)
            }
            
            binding.btnCallHistory.setOnClickListener {
                openCallHistory()
            }
            
            binding.tvWelcome.text = "Welcome Customer!\nChoose who to call:"
            binding.btnCallDriver.isEnabled = true
            binding.btnCallSupport.isEnabled = true
            
        } else {
            // Driver UI - show incoming call simulation button
            binding.btnCallDriver.text = "Simulate Incoming Call"
            binding.btnCallDriver.isEnabled = true
            binding.btnCallSupport.visibility = View.GONE
            binding.tvWelcome.text = "Driver Mode\nAvailable - Waiting for calls"
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            liveKitManager.callState.collect { state ->
                Log.d(TAG, "🔄 Call state changed: $state")
                Log.d(TAG, "📍 Current activity: ${this.javaClass.simpleName}")
                Log.d(TAG, "👤 Is customer: $isCustomer")
                Log.d(TAG, "🛑 Is ending call programmatically: $isEndingCallProgrammatically")
                
                // Don't trigger ANY navigation if we're ending a call programmatically
                if (isEndingCallProgrammatically) {
                    Log.d(TAG, "🛑 Skipping ALL navigation - call being ended programmatically")
                    // Only reset the flag when we reach IDLE state
                    if (state == CallState.IDLE) {
                        Log.d(TAG, "🔄 Resetting isEndingCallProgrammatically flag")
                        resetEndingCallProgrammatically()
                    }
                    return@collect
                }
                
                when (state) {
                    CallState.CONNECTING -> {
                        Log.d(TAG, "🚀 Navigating to ringing activity (CONNECTING)")
                        // Navigate directly to ringing screen instead of modal
                        navigateToRingingActivity()
                    }
                    CallState.CONNECTED -> {
                        if (!isCustomer) {
                            Log.d(TAG, "🚗 Driver connected to room, now listen for incoming calls")
                            // Driver connected to room, now listen for incoming calls
                            observeIncomingCalls()
                        } else {
                            Log.d(TAG, "👤 Customer connected to room")
                        }
                    }
                    CallState.INCOMING_CALL -> {
                        if (!isCustomer) {
                            Log.d(TAG, "📞 Showing incoming call dialog for driver")
                            showIncomingCallDialog()
                        } else {
                            Log.d(TAG, "📞 Customer sees ringing screen")
                            // Customer sees ringing screen
                            navigateToRingingActivity()
                        }
                    }
                    CallState.IN_CALL -> {
                        Log.d(TAG, "📱 Navigating to call activity (IN_CALL)")
                        navigateToCallActivity()
                    }
                    CallState.ERROR -> {
                        Log.d(TAG, "❌ Connection failed, showing error")
                        showError("Connection failed. Please try again.")
                    }
                    CallState.IDLE -> {
                        Log.d(TAG, "🔄 Call state is IDLE - call ended")
                        // Don't navigate when call ends, let the current activity handle it
                    }
                    else -> {
                        Log.d(TAG, "ℹ️ Call state: $state - no action taken")
                        // Do nothing
                    }
                }
            }
        }
    }
    
    private fun setupDriverMode() {
        // Driver mode: Just show available status, don't connect to room yet
        Log.d(TAG, "Driver mode activated - waiting for incoming calls")
        
        // Update UI to show driver is available
        binding.tvWelcome.text = "Driver Mode\nAvailable - Waiting for calls"
        
        // Don't connect to room automatically
        // Driver will only connect when receiving an incoming call
        
        // Set up incoming call detection (for when customer calls)
        observeIncomingCalls()
    }
    
    private fun observeRoomStatus() {
        lifecycleScope.launch {
            liveKitManager.roomConnectionStatus.collect { status ->
                Log.d(TAG, "Driver room status: $status")
                updateDriverStatus(status)
            }
        }
        
        lifecycleScope.launch {
            liveKitManager.participants.collect { participants ->
                Log.d(TAG, "Driver participants: ${liveKitManager.getParticipantDetails()}")
                updateDriverStatusText()
            }
        }
    }
    
    private fun updateDriverStatus(status: RoomConnectionStatus) {
        val statusText = when (status) {
            RoomConnectionStatus.CONNECTING -> "Connecting to room..."
            RoomConnectionStatus.CONNECTED -> "Connected to room - Waiting for customers"
            RoomConnectionStatus.MULTIPLE_PARTICIPANTS -> "Customer joined - Incoming call!"
            RoomConnectionStatus.CALL_ACTIVE -> "Call in progress"
            RoomConnectionStatus.DISCONNECTED -> "Disconnected from room"
            RoomConnectionStatus.ERROR -> "Connection error"
            else -> "Unknown status"
        }
        
        binding.tvWelcome.text = "Driver Mode\n$statusText"
        Log.d(TAG, "Driver status updated: $statusText")
    }
    
    private fun updateDriverStatusText() {
        val participantDetails = liveKitManager.getParticipantDetails()
        Log.d(TAG, "Current room participants: $participantDetails")
        
        if (liveKitManager.hasCustomerAndDriver()) {
            Log.d(TAG, "🎉 SUCCESS: Customer and Driver are in the same room!")
            Log.d(TAG, "Room: ${getRoomName()}")
            Log.d(TAG, "Participants: $participantDetails")
        }
    }
    
    private fun observeIncomingCalls() {
        // For driver mode: Listen for incoming calls from customers
        if (!isCustomer) {
            // Driver mode: Set up a simple way to simulate incoming calls
            // In a real app, this would be triggered by push notifications
            
            // For demo purposes, add a button to simulate incoming call
            binding.btnCallDriver.text = "Simulate Incoming Call"
            binding.btnCallDriver.setOnClickListener {
                simulateIncomingCall()
            }
            
            Log.d(TAG, "Driver mode: Waiting for incoming calls (simulated)")
        }
    }
    
    private fun simulateIncomingCall() {
        Log.d(TAG, "Simulating incoming call for driver...")
        
        // Show incoming call dialog
        showIncomingCallDialog()
    }
    
    private fun connectDriverToRoom() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Driver connecting to room for incoming call...")
                
                // Get LiveKit token for driver to connect to room
                val participantIdentity = getParticipantIdentity()
                val roomName = getRoomName()
                Log.d(TAG, "Driver connecting with identity: $participantIdentity to room: $roomName")
                
                val tokenResult = repository.getLiveKitToken(roomName, participantIdentity)
                
                tokenResult.onSuccess { tokenResponse ->
                    Log.d(TAG, "Driver got token, connecting to room...")
                    
                    // Connect driver to LiveKit room
                    liveKitManager.connectToRoom(
                        token = tokenResponse.accessToken,
                        wsUrl = tokenResponse.wsUrl,
                        roomName = roomName,
                        callType = CallType.DRIVER
                    )
                    
                    // Start observing room status for driver
                    observeRoomStatus()
                    
                    // Navigation will happen automatically through call state observer
                    // when the connection state changes to CONNECTING
                }.onFailure { error ->
                    Log.e(TAG, "Failed to get driver token", error)
                    showError("Failed to connect as driver: ${error.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting driver to room", e)
                showError("Error connecting driver to room: ${e.message}")
            }
        }
    }
    
    private fun showIncomingCallDialog() {
        AlertDialog.Builder(this)
            .setTitle("Incoming Call")
            .setMessage("Customer is calling. Accept?")
            .setPositiveButton("Accept") { _, _ ->
                // Driver accepts call - connect to room first, then navigate
                if (!isCustomer) {
                    connectDriverToRoom()
                    // Don't navigate immediately - let the connection complete first
                    // Navigation will happen in the call state observer
                } else {
                    navigateToRingingActivity()
                }
            }
            .setNegativeButton("Decline") { _, _ ->
                // Handle call decline
                liveKitManager.disconnect()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun initiateCall(callType: CallType) {
        lifecycleScope.launch {
            try {
                when (callType) {
                    CallType.SUPPORT -> {
                        // Use new SIP-based support call API
                        Log.d(TAG, "🚀 Initiating support call using SIP API...")
                        Log.d(TAG, "📞 Using hardcoded values:")
                        Log.d(TAG, "   - Phone number: +443333054030")
                        Log.d(TAG, "   - Room name: sdk-room")
                        Log.d(TAG, "   - Participant name: waseem")
                        
                        // Set current call type for UI updates
                        setCurrentCallType(CallType.SUPPORT)
                        
                        val supportCallResult = repository.makeSupportCall()
                        
                        supportCallResult.onSuccess { supportResponse ->
                            Log.d(TAG, "✅ Support call initiated successfully!")
                            Log.d(TAG, "📋 Response: ${supportResponse.message}")
                            
                            if (supportResponse.success && supportResponse.data != null) {
                                val callData = supportResponse.data!!
                                
                                // Store participant identity for ending call later
                                setCurrentSupportCallParticipantIdentity(callData.call_details.participant_identity)
                                Log.d(TAG, "💾 Support call participant identity stored: ${callData.call_details.participant_identity}")
                                Log.d(TAG, "📱 Call details:")
                                Log.d(TAG, "   - Participant ID: ${callData.call_details.participant_id}")
                                Log.d(TAG, "   - Room name: ${callData.call_details.room_name}")
                                Log.d(TAG, "   - Phone number: ${callData.call_details.phone_number}")
                                
                                // Set LiveKitManager call type for proper disconnect handling
                                liveKitManager.setCallType(CallType.SUPPORT)
                                
                                // Navigate to ringing screen for support call
                                navigateToRingingActivity()
                                
                                // Simulate support call acceptance after delay
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(3000) // Wait 3 seconds
                                    Log.d(TAG, "🔄 Simulating support call acceptance...")
                                    navigateToCallActivity()
                                }
                                
                            } else {
                                Log.e(TAG, "❌ Support call failed: ${supportResponse.message}")
                                showError("Failed to initiate support call: ${supportResponse.message}")
                                // Clear call type on failure
                                setCurrentCallType(null)
                            }
                        }.onFailure { error ->
                            Log.e(TAG, "❌ Failed to make support call", error)
                            showError("Failed to initiate support call: ${error.message}")
                            // Clear call type on failure
                            setCurrentCallType(null)
                        }
                    }
                    
                    CallType.DRIVER -> {
                        // Use existing LiveKit token API for driver calls
                        Log.d(TAG, "🚗 Initiating driver call using LiveKit API...")
                        
                        // Set current call type for UI updates
                        setCurrentCallType(CallType.DRIVER)
                        
                        val participantIdentity = getParticipantIdentity()
                        val roomName = getRoomName()
                        val tokenResult = repository.getLiveKitToken(roomName, participantIdentity)
                        
                        tokenResult.onSuccess { tokenResponse ->
                            Log.d(TAG, "Got token, connecting to room...")
                            
                            // Connect to LiveKit room
                            // For customer, always use CallType.CUSTOMER regardless of who they're calling
                            val participantCallType = if (isCustomer) CallType.CUSTOMER else CallType.DRIVER
                            Log.d(TAG, "Customer initiating call to $callType, but connecting as $participantCallType")
                            
                            liveKitManager.connectToRoom(
                                token = tokenResponse.accessToken,
                                wsUrl = tokenResponse.wsUrl,
                                roomName = roomName,
                                callType = participantCallType
                            )
                            
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to get token", error)
                            showError("Failed to initiate call: ${error.message}")
                            // Clear call type on failure
                            setCurrentCallType(null)
                        }
                    }
                    
                    else -> {
                        Log.w(TAG, "Unsupported call type: $callType")
                        showError("Unsupported call type")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
                showError("Error initiating call: ${e.message}")
            }
        }
    }
    
    private fun navigateToRingingActivity() {
        val intent = Intent(this, RingingActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
    }
    
    private fun navigateToCallActivity() {
        Log.d(TAG, "📱 Navigating to CallActivity...")
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                showError("Permissions are required for voice calling")
            }
        }
    }
    
    // Removed modal dialog - using direct navigation instead
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun openCallHistory() {
        try {
            Log.d(TAG, "Opening call history...")
            val intent = Intent(this, CallHistoryActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Call history activity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening call history", e)
            showError("Failed to open call history: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // No modal to clean up
    }
} 