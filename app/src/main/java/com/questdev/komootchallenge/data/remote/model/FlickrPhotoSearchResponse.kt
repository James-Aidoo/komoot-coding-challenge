package com.questdev.komootchallenge.data.remote.model

data class FlickrPhotoSearchResponse(
    val photos: FlickrPhotoPagingResult,
    val stat: String
)
