package com.example.distancetravelapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class LocationProvider(private val activity: AppCompatActivity) {

  private val client by lazy { LocationServices.getFusedLocationProviderClient(activity) }

  private val locations = mutableListOf<LatLng>()

  private var distance = 0

  private val DISTANCE_THRESHOLD_METERS = 10 // Increase the threshold
  private val SMOOTHING_WINDOW_SIZE = 200

  private val smoothedLocations = mutableListOf<LatLng>()

  val liveLocations = MutableLiveData<List<LatLng>>()
  val liveDistance = MutableLiveData<Int>()
  val liveLocation = MutableLiveData<LatLng>()

  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val currentLocation = result.lastLocation
      val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)

      val lastLocation = locations.lastOrNull()

      Log.d("--------------", "onLocationResult: LatLon ${latLng}")

      if (lastLocation != null) {
        val calculatedDistance =
          SphericalUtil.computeDistanceBetween(lastLocation, latLng).roundToInt()

        if (calculatedDistance >= DISTANCE_THRESHOLD_METERS) {
          distance += calculatedDistance
          liveDistance.value = distance

        }
      }
      val locationInfo ="latitude and longitude : $latLng, Distance : $distance"
      Log.d("--------------", "onLocationResult:  ${locationInfo}")
      saveLocationToFile(locationInfo)

      smoothedLocations.add(latLng)

      // Calculate the smoothed latitude and longitude
      val smoothedLatLng = calculateSmoothedLatLng(smoothedLocations)

      locations.add(smoothedLatLng)
//      locations.add(latLng)
      liveLocations.value = locations
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
      val fileOutputStream = activity.openFileOutput(fileName, Context.MODE_APPEND)
      val outputStreamWriter = OutputStreamWriter(fileOutputStream)
      outputStreamWriter.write(locationInfo + "\n")
      outputStreamWriter.close()
      Log.d("LocationSaved", "Location data saved to file.")
    } catch (e: Exception) {
      Log.e("LocationSaveError", "Error saving location data: ${e.message}")
    }
  }
  fun getUserLocation() {
    client.lastLocation.addOnSuccessListener { location ->
      val latLng = LatLng(location.latitude, location.longitude)
      locations.add(latLng)
      liveLocation.value = latLng
    }
  }
  fun trackUser() {
    val locationRequest = LocationRequest.create()
    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    locationRequest.interval = 5000
    client.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
  }

  fun stopTracking() {
    client.removeLocationUpdates(locationCallback)
    locations.clear()
    distance = 0
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