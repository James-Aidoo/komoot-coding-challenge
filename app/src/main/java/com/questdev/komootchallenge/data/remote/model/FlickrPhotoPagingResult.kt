package com.questdev.komootchallenge.data.remote.model

import com.google.gson.annotations.SerializedName

data class FlickrPhotoPagingResult(
    val page: Int,
    val pages: Int,
    @SerializedName("perpage") val perPage: Int,
    val total: Int,
    val photo: List<FlickrPhoto>
)
