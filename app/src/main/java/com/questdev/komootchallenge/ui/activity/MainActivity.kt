package com.questdev.komootchallenge.ui.activity

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.questdev.komootchallenge.receiver.LocationUpdatesReceiver
import com.questdev.komootchallenge.service.LocationUpdateService
import com.questdev.komootchallenge.ui.theme.KomootChallengeTheme
import com.questdev.komootchallenge.util.log
import com.skydoves.landscapist.glide.GlideImage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var locationUpdateService: LocationUpdateService? = null
    private val serviceIntent by lazy { LocationUpdateService.getIntent(this) }
    private val locationReceiver by lazy { LocationUpdatesReceiver(::onImageUrlReady) }
    private val locationRequest by lazy {
        LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 15000
        }
    }

    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder: LocationUpdateService.LocalBinder =
                service as LocationUpdateService.LocalBinder
            locationUpdateService = binder.service
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            locationUpdateService = null
            isServiceBound = false
        }
    }

    private val locationUrls = mutableStateOf<List<String>>(emptyList())
    private val isStarted = mutableStateOf(false)

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                    || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                    || permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false -> {

                checkLocationSettings()
            }
            else -> {
                Toast.makeText(
                    this,
                    "Permission request denied. App won't work in this state",
                    Toast.LENGTH_LONG
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KomootChallengeTheme {
                val urls by rememberSaveable { locationUrls }
                val isStartClicked by rememberSaveable { isStarted }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                modifier = Modifier.wrapContentWidth(align = Alignment.End),
                                elevation = 4.dp,
                                backgroundColor = MaterialTheme.colors.surface,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = {
                                        onButtonClick(!isStartClicked)
                                    }) {
                                        Text(
                                            text = if (isStartClicked) "Stop" else "Start",
                                            color = MaterialTheme.colors.onSurface
                                        )
                                    }
                                }
                            }
                        }) {
                        if (urls.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxHeight()
                                    .fillMaxHeight()
                            ) {
                                items(urls) { url ->
                                    Column {
                                        Card(
                                            shape = MaterialTheme.shapes.medium,
                                            elevation = 4.dp,
                                        ) {
                                            GlideImage(
                                                imageModel = url,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(250.dp),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Text(
                                                            text = "Fetching image",
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                },
                                                failure = {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Text(
                                                            text = "Unable to fetch image",
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isServiceBound) {
            bindService(
                Intent(serviceIntent), serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        if (isServiceBound) unbindService(serviceConnection)
        isServiceBound = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter(LocationUpdateService.ACTION_BROADCAST)
        )
        locationUpdateService?.sendBroadcast()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
        super.onPause()
    }

    private fun onImageUrlReady(imageUrls: List<String>) {
        locationUrls.value = imageUrls
    }

    private fun onButtonClick(state: Boolean) {
        when (state) {
            true -> requestLocationPermission()
            else -> {
                locationUpdateService?.stopTracking()
                stopService(serviceIntent)
                isStarted.value = !isStarted.value
            }
        }
        log("Started: ${isStarted.value}")
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            permissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            checkLocationSettings()
        }
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            startService(serviceIntent)
            isStarted.value = !isStarted.value
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, 42)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    log(sendEx.stackTraceToString())
                    Toast.makeText(this, "Couldn't get location settings", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        locationUpdateService?.stopTracking()
        stopService(serviceIntent)
        super.onDestroy()
    }
}
