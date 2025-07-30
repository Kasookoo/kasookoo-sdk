package com.example.callkotlin.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    companion object {
        const val BASE_URL = "https://voiceai.kasookoo.com/"
    }
    
    @POST("api/v1/bot/sdk/get-token")
    suspend fun getLiveKitToken(@Body request: TokenRequest): Response<TokenResponse>
    
    @GET("api/v1/drivers/{driverId}/details")
    suspend fun getDriverDetails(@Path("driverId") driverId: String): Response<DriverDetails>
    
    // Legacy support call API (keeping for backward compatibility)
    @POST("api/v1/support/initiate-call")
    suspend fun initiateSupportCall(@Body request: SupportCallRequest): Response<SupportCallResponse>
    
    // New SIP-based call support APIs
    @POST("api/v1/bot/sdk-sip/calls/make")
    suspend fun makeSupportCall(@Body request: SupportCallMakeRequest): Response<SupportCallMakeResponse>
    
    @POST("api/v1/bot/sdk-sip/calls/end")
    suspend fun endSupportCall(@Body request: SupportCallEndRequest): Response<SupportCallEndResponse>
}

// Request/Response models for LiveKit token API
data class TokenRequest(
    val room_name: String,
    val participant_identity: String
)

data class TokenResponse(
    val accessToken: String,  // Changed to match backend response
    val wsUrl: String
)

// Driver details models (placeholder)
data class DriverDetails(
    val driverId: String,
    val name: String,
    val phoneNumber: String,
    val status: String,
    val location: DriverLocation?
)

data class DriverLocation(
    val latitude: Double,
    val longitude: Double
)

// Legacy support call models (keeping for backward compatibility)
data class SupportCallRequest(
    val customerId: String,
    val issueType: String,
    val priority: String = "normal"
)

data class SupportCallResponse(
    val callId: String,
    val estimatedWaitTime: Int,
    val queuePosition: Int
)

// New SIP-based support call models
data class SupportCallMakeRequest(
    val phone_number: String = "+443333054030",  // Hardcoded phone number
    val room_name: String = "sdk-room",         // Hardcoded room name
    val participant_name: String = "waseem"     // Hardcoded participant name
)

data class SupportCallMakeResponse(
    val success: Boolean,
    val message: String,
    val data: SupportCallMakeData?,
    val error: String?
)

data class SupportCallMakeData(
    val success: Boolean,
    val call_details: SupportCallDetails,
    val room_token: String,
    val room_name: String
)

data class SupportCallDetails(
    val participant_id: String,
    val participant_identity: String,
    val room_name: String,
    val phone_number: String
)

data class SupportCallEndRequest(
    val participant_identity: String,
    val room_name: String = "sdk-room"  // Hardcoded room name
)

data class SupportCallEndResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
) 