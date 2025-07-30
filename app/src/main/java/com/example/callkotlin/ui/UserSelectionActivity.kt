package com.example.callkotlin.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.callkotlin.databinding.ActivityUserSelectionBinding

class UserSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserSelectionBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.btnCustomer.setOnClickListener {
            navigateToMainActivity(isCustomer = true)
        }
        
        binding.btnDriver.setOnClickListener {
            navigateToMainActivity(isCustomer = false)
        }
    }
    
    private fun navigateToMainActivity(isCustomer: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("isCustomer", isCustomer)
        }
        startActivity(intent)
        finish()
    }
} 