package com.questdev.komootchallenge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.questdev.komootchallenge.service.LocationUpdateService

class LocationUpdatesReceiver(private val onImageUrlReady: (List<String>) -> Unit) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val urls = intent?.getStringArrayListExtra(LocationUpdateService.EXTRA_URL) as List<String>?
        urls?.let {
            onImageUrlReady.invoke(it)
        }
    }
}
