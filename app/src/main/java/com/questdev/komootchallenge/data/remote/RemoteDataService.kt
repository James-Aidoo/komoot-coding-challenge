package com.questdev.komootchallenge.data.remote

import com.questdev.komootchallenge.data.remote.model.FlickrPhotoSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RemoteDataService {
    @GET("https://www.flickr.com/services/rest/?method=flickr.photos.search&api_key=${RemoteDataClient.FLICKER_API_KEY}&per_page=20&format=json&nojsoncallback=1")
    suspend fun searchFlickrForPhotos(
        @Query("lat") lat: String,
        @Query("lon") long: String
    ): FlickrPhotoSearchResponse
}
