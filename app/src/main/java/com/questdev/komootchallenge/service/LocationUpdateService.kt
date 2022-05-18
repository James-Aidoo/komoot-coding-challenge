package com.questdev.komootchallenge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.questdev.komootchallenge.R
import com.questdev.komootchallenge.data.Repository
import com.questdev.komootchallenge.data.remote.model.FlickrPhoto
import com.questdev.komootchallenge.data.remote.model.FlickrPhotoSearchResponse
import com.questdev.komootchallenge.util.log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random


@AndroidEntryPoint
class LocationUpdateService : LifecycleService() {

    private val binder: IBinder = LocalBinder()

    @Inject
    lateinit var repository: Repository

    private var currentLocation: Location? = null

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(application.applicationContext)
    }

    private val locationRequest by lazy {
        LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 15000
        }
    }

    private var locationImageUrls = ArrayList<String>()

    private val notificationId = 12

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
    }

    private var isConfigurationChange = false

    private fun createNotification(): Notification {
        val title = "Komoot challenge"

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel() else ""

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("listening for location updates")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(): String {
        val channelId = "location_updates"
        val channel = NotificationChannel(
            channelId,
            "Location updates service", NotificationManager.IMPORTANCE_NONE
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        isConfigurationChange = true
        super.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        stopForeground(true)
        isConfigurationChange = false
        return binder
    }

    override fun onRebind(intent: Intent?) {
        stopForeground(true)
        isConfigurationChange = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        log("Last client unbound from service")
        if (!isConfigurationChange) {
            log("Starting foreground service")
            startForegroundService()
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log("Service started")

        getLastLocation()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(notificationId, createNotification())
        }
    }

    private fun searchForImagesOnFlickr(location: Location) {
        lifecycleScope.launch(Dispatchers.IO) {
            coroutineScope {
                repository.searchFlickrForPhotos(
                    this,
                    location.latitude.toString(),
                    location.longitude.toString(),
                    ::handleFlickrSearchResult
                )
            }
        }
    }

    private fun handleFlickrSearchResult(response: FlickrPhotoSearchResponse?) {
        response?.let {
            if (it.stat.equals("ok", ignoreCase = true) && it.photos.photo.isNotEmpty()) {
                val resultSize = response.photos.photo.size
                val randomImage = response.photos.photo[Random.nextInt(until = resultSize)]

                val imageUrl = createImageUrl(randomImage)
                val images = mutableListOf(imageUrl).apply { addAll(locationImageUrls) }
                locationImageUrls = images as ArrayList<String>
                sendBroadcast()
            }
        } ?: run {
            log("Received an empty response")
            Toast.makeText(
                application,
                "Unable to find images of your current location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun sendBroadcast() {
        val intent = Intent(ACTION_BROADCAST)
        intent.putStringArrayListExtra(EXTRA_URL, locationImageUrls)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun createImageUrl(photo: FlickrPhoto): String {
        return "https://live.staticflickr.com/${photo.server}/${photo.id}_${photo.secret}_z.jpg"
    }

    private val locationCallback by lazy {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                log("LATITUDE: ${result.lastLocation.latitude} LONGITUDE: ${result.lastLocation.longitude}")
                if (currentLocation == null && locationImageUrls.isEmpty()) {
                    currentLocation = result.lastLocation
                    searchForImagesOnFlickr(result.lastLocation)
                } else {
                    val distance = currentLocation?.distanceTo(result.lastLocation)
                    log("Distance is $distance")
                    distance?.let {
                        if (it >= 100) {
                            currentLocation = result.lastLocation
                            searchForImagesOnFlickr(result.lastLocation)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation() {
        locationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = location
                if (locationImageUrls.isEmpty()) searchForImagesOnFlickr(location)
                log("LATITUDE: ${location.latitude} LONGITUDE: ${location.longitude}")
            }
        }.also {
            trackLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun trackLocation() {
        locationClient.requestLocationUpdates(
            locationRequest, locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        locationImageUrls = ArrayList()
    }


    companion object {
        private val packageName = LocationUpdateService::class.java.simpleName
        val ACTION_BROADCAST = "${packageName}_broadcast"
        val EXTRA_URL = "${packageName}_url"

        fun getIntent(context: Context) = Intent(context, LocationUpdateService::class.java)
    }

    inner class LocalBinder : Binder() {
        val service: LocationUpdateService
            get() = this@LocationUpdateService
    }
}
