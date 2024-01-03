package com.example.distancetravelapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.SphericalUtil
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class LocationProvider(private val context: Context) {

  private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

  private var marker: Marker? = null
  private var map: GoogleMap? = null
  private val locations = mutableListOf<LatLng>()

  private lateinit var lastLocation: LatLng
  private var distance = 0

  private val DISTANCE_THRESHOLD_METERS = 10 // Increase the threshold
  private val SMOOTHING_WINDOW_SIZE = 200

  private val WAKELOCK_TAG_PREFIX = "com.example.distancetravelapp:MyAppWakeLock:"
  private var wakeLock: PowerManager.WakeLock? = null

  private val smoothedLocations = mutableListOf<LatLng>()

  val liveLocations = MutableLiveData<List<LatLng>>()
  val liveDistance = MutableLiveData<Int>()
  val liveLocation = MutableLiveData<LatLng>()

  val currentTimeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())


  val database = FirebaseDatabase.getInstance()
  val locationsReference = database.getReference("locations")

  fun initializeMap(map: GoogleMap) {
    this.map = map
  }
  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val currentLocation = result.lastLocation
      val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)

      var lastLocation = locations.lastOrNull()

      if (lastLocation != null) {
        val calculatedDistance =
          SphericalUtil.computeDistanceBetween(lastLocation, latLng).roundToInt()

        if (calculatedDistance >= DISTANCE_THRESHOLD_METERS) {
          distance += calculatedDistance
          liveDistance.value = distance
        }
      }
      lastLocation = latLng

      val locationInfo =
        "latitude and longitude : $latLng, Distance : $distance  ----- Time : $currentTimeStamp"
      Log.d("--------------", "onLocationResult:  ${locationInfo}")
      saveLocationToFile(locationInfo)

      smoothedLocations.add(latLng)

      // Calculate the smoothed latitude and longitude
      val smoothedLatLng = calculateSmoothedLatLng(smoothedLocations)

      locations.add(smoothedLatLng)
      liveLocations.value = locations

      val dateFormat = SimpleDateFormat("yyyy-MM-dd  ", Locale.getDefault())
      val date = dateFormat.format(Date())

      val locationData = mapOf(
        "latitude" to latLng.latitude,
        "longitude" to latLng.longitude,
        "distance" to distance,
        "time" to currentTimeStamp
      )

      locationsReference
        .child(date)
        .push()
        .setValue(locationData)
        .addOnSuccessListener {
          Log.d("---------", "Location data added for date: $date")
        }
        .addOnFailureListener { e ->
          Log.e("---------", "Error adding location data", e)
        }


      updateMarker(latLng)
    }
  }
  private fun calculateSmoothedLatLng(locations: List<LatLng>): LatLng {
    val windowSize = SMOOTHING_WINDOW_SIZE
    if (locations.size < windowSize) {
      return locations.last()
    }
    var sumLat = 0.0
    var sumLng = 0.0

    for (i in locations.size - windowSize until locations.size) {
      sumLat += locations[i].latitude
      sumLng += locations[i].longitude
    }
    val smoothedLat = sumLat / windowSize
    val smoothedLng = sumLng / windowSize

    return LatLng(smoothedLat, smoothedLng)
  }
  private fun saveLocationToFile(locationInfo: String) {
    try {
      val fileName = "location_data.txt"
      val fileOutputStream = context.openFileOutput(fileName, Context.MODE_APPEND)
      val outputStreamWriter = OutputStreamWriter(fileOutputStream)
      outputStreamWriter.write(locationInfo +  "\n")
      outputStreamWriter.close()
      Log.d("LocationSaved", "Location data saved to file.")
    } catch (e: Exception) {
      Log.e("LocationSaveError", "Error saving location data: ${e.message}")
    }
  }
  fun getUserLocation() {
    client.lastLocation.addOnSuccessListener { location ->
      if (location != null) {
        lastLocation = LatLng(location.latitude, location.longitude)
        locations.add(lastLocation)
        liveLocation.value = lastLocation
      } else {
        requestSingleLocationUpdate()
        Log.e("LocationError", "Last location is null")
      }
    }
  }
  private fun requestSingleLocationUpdate() {
    val locationRequest = LocationRequest.create()
    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

    val callback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        val location = result.lastLocation
        if (location != null) {
          val latLng = LatLng(location.latitude, location.longitude)
          locations.add(latLng)
          liveLocation.value = latLng

          // Remove location updates after receiving a single location
          client.removeLocationUpdates(this)
        } else {
          Log.e("LocationError", "Unable to get current location.")
        }
      }
    }
    // Request location updates
    client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
  }
  fun trackUser() {

    acquireWakeLock()

    val locationRequest = LocationRequest.create()
    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    locationRequest.interval = 5000

    val serviceIntent = Intent(context, LocationForegroundService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)

    client.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
  }

  fun stopTracking() {
    releaseWakeLock()
    client.removeLocationUpdates(locationCallback)
    locations.clear()
    distance = 0

    val serviceIntent = Intent(context, LocationForegroundService::class.java)
    context.stopService(serviceIntent)

    addMarkerAtLastLocation()
  }

  private fun updateMarker(latLng: LatLng) {
    // Remove the existing marker if it exists
    marker?.remove()

    // Add a marker at the current location
    marker = map?.addMarker(MarkerOptions().position(latLng).title("Last Known Location"))
  }

  private fun addMarkerAtLastLocation() {
    // Add a marker at the last known location when tracking stops
    lastLocation.let { lastLatLng ->
      updateMarker(lastLatLng)
    }
  }

  private fun acquireWakeLock() {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      WAKELOCK_TAG_PREFIX + System.currentTimeMillis()
    )
    wakeLock?.acquire()
  }
  fun releaseWakeLock() {
    try {
      wakeLock?.let {
        if (it.isHeld) {
          it.release()
          Log.d("======", "releaseWakeLock: Wakelock released.")
        } else {
          Log.d("======", "releaseWakeLock: Wakelock not held.")
        }
      }
    } catch (e: Exception) {
      Log.e("======", "releaseWakeLock: Error releasing Wakelock: ${e.message}")
    }
  }

}





//fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
//  val R = 6371
//
//  val dLat = deg2rad(lat2 - lat1)
//  val dLon = deg2rad(lon2 - lon1)
//  val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//          Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
//          Math.sin(dLon / 2) * Math.sin(dLon / 2)
//  val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
//  val d = R * c // Distance in km
//  return d
//}
//private fun deg2rad(deg: Double): Double {
//  return deg * (Math.PI / 180)
//}
//val minDistanceThreshold = 5.0 // Adjust this threshold as needed
//if (lastLocation != null) {
//  val distanceBetweenLocations = calculateDistance(
//    lastLocation.latitude, lastLocation.longitude,
//    latLng.latitude, latLng.longitude
//  )
//
//  // Update the distance and LiveData
//  distance += distanceBetweenLocations.roundToInt()
//  liveDistance.value = distance
//}