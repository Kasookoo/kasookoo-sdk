package com.yuave.kasookoo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yuave.kasookoo.ui.RingingActivity

class CallActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallActionReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val callId = intent.getStringExtra("CALL_ID")
        
        Log.d(TAG, "Received action: $action for call: $callId")
        
        when (action) {
            "ACTION_ANSWER_CALL" -> {
                // Launch ringing activity to handle the call
                val ringingIntent = Intent(context, RingingActivity::class.java).apply {
                    putExtra("CALL_ID", callId)
                    putExtra("AUTO_ANSWER", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(ringingIntent)
            }
            
            "ACTION_DECLINE_CALL" -> {
                // Handle call decline
                // You might want to send a signal to the backend or LiveKit
                Log.d(TAG, "Call declined: $callId")
                
                // Dismiss notification
                dismissNotification(context)
            }
        }
    }
    
    private fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
        notificationManager.cancel(1001) // Same ID as in CallNotificationService
    }
} 