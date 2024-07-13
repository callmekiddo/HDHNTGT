package com.kiddo.antitheft

import retrofit2.Response
import retrofit2.http.GET

interface ImageApi {
    @GET("/images")
    suspend fun getAllImages() : Response<List<ImageData>>
}