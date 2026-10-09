package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object SeparationApiClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Client para upload e separação pesada (Demucs pode levar 30-90s)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    // Client rápido para ping de integridade (timeout de 2.5s para não travar celulares)
    private val healthHttpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private fun getService(baseUrl: String, client: OkHttpClient = okHttpClient): SeparationApiService {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SeparationApiService::class.java)
    }

    suspend fun checkHealth(baseUrl: String): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val service = getService(baseUrl, healthHttpClient)
            val response = service.checkHealth()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Servidor respondeu com código ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun discoverActiveServer(preferredUrl: String): String? = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            preferredUrl,
            "http://10.0.2.2:8001",
            "http://localhost:8001",
            "http://10.0.2.2:8000"
        ).distinct()

        for (url in candidateUrls) {
            val result = checkHealth(url)
            if (result.isSuccess) {
                return@withContext url
            }
        }
        null
    }

    suspend fun separateAudio(
        baseUrl: String,
        audioFile: File,
        modelName: String = "htdemucs"
    ): Result<SeparationResponse> = withContext(Dispatchers.IO) {
        try {
            val service = getService(baseUrl, okHttpClient)
            val mediaType = "audio/*".toMediaTypeOrNull()
            val requestFile = audioFile.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val modelBody = modelName.toRequestBody("text/plain".toMediaTypeOrNull())
            val extractBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = service.separateAudio(filePart, modelBody, extractBody)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Erro na separação (HTTP ${response.code()}): ${response.errorBody()?.string() ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadStem(
        fullUrl: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(fullUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Falha no download da faixa (HTTP ${response.code})"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Corpo de resposta vazio"))
            body.byteStream().use { input: InputStream ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!destinationFile.exists() || destinationFile.length() < 1024) {
                return@withContext Result.failure(Exception("Arquivo baixado está vazio ou incompleto"))
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
