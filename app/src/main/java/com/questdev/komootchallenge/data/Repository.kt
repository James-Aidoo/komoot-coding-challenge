package com.questdev.komootchallenge.data

import com.questdev.komootchallenge.data.remote.RemoteDataService
import com.questdev.komootchallenge.data.remote.model.FlickrPhotoSearchResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

class Repository @Inject constructor(private val remoteDataService: RemoteDataService) {

    suspend fun searchFlickrForPhotos(
        scope: CoroutineScope,
        lat: String,
        long: String,
        onResult: (FlickrPhotoSearchResponse?) -> Unit
    ) {
        return request(
            scope,
            remoteDataService::searchFlickrForPhotos,
            param1 = lat,
            param2 = long,
            onResult
        )
    }

    private suspend fun <T> request(
        scope: CoroutineScope,
        call: suspend (String, String) -> T,
        param1: String,
        param2: String,
        onResult: (T?) -> Unit
    ) {
        scope.launch(Dispatchers.Main) {
            val deferred = async(Dispatchers.IO) {
                try {
                    call(param1, param2)
                } catch (e: Exception) {
                    print(e)
                    null
                }
            }
            onResult(deferred.await())
        }
    }

}
