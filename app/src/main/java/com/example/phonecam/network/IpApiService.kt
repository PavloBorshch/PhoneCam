package com.example.phonecam.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// Data Model (Serialization)
// Відповідь від API приходить у форматі: {"ip":"123.123.123.123"}
@Serializable
data class IpResponse(
    val ip: String
)

// Retrofit Interface
interface IpApiService {
    // Запит GET на адресу https://api.ipify.org/?format=json
    @GET("/")
    suspend fun getPublicIp(@Query("format") format: String = "json"): IpResponse
}

// Singleton для створення клієнта
object RetrofitInstance {
    private const val BASE_URL = "https://api.ipify.org/"

    private val json = Json { ignoreUnknownKeys = true }

    val api: IpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(IpApiService::class.java)
    }
}