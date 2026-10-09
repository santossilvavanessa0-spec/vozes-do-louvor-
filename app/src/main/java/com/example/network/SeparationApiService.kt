package com.example.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

interface SeparationApiService {

    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>

    @Multipart
    @POST("api/separate")
    suspend fun separateAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("extract_parts") extractParts: RequestBody
    ): Response<SeparationResponse>

    @Streaming
    @GET
    suspend fun downloadStemFile(@Url url: String): Response<ResponseBody>
}
