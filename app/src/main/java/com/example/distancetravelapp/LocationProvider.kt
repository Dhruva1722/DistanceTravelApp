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

  private val SMOOTHING_WINDOW_SIZE = 126

  private val smoothedLocations = mutableListOf<LatLng>()


  val liveLocations = MutableLiveData<List<LatLng>>()
  val liveDistance = MutableLiveData<Int>()
  val liveLocation = MutableLiveData<LatLng>()

  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val currentLocation = result.lastLocation
      val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)

      Log.d("--------------", "onLocationResult: LatLon ${latLng}")
      val lastLocation = locations.lastOrNull()


      if (lastLocation != null) {
        // Calculate the distance between the current location and the las`t location
        val distanceBetweenLocations = calculateDistance(
          lastLocation.latitude, lastLocation.longitude,
          latLng.latitude, latLng.longitude
        )

        // Update the distance and LiveData
        distance += distanceBetweenLocations.roundToInt()
        liveDistance.value = distance
        Log.d("--------------", "onLocationResult: Distance traveled: $distanceBetweenLocations")
      }

      val locationInfo ="latitude and longitude : $latLng, Distance : $distance"
      saveLocationToFile(locationInfo)
      smoothedLocations.add(latLng)

      // Keep the size of smoothedLocations within a certain limit (e.g., 5 data points)
      if (smoothedLocations.size > SMOOTHING_WINDOW_SIZE) {
        smoothedLocations.removeAt(0)
      }

      // Calculate the smoothed latitude and longitude
      val smoothedLatLng = calculateSmoothedLatLng(smoothedLocations)

      locations.add(smoothedLatLng)
//      locations.add(latLng)
      liveLocations.value = locations
    }
  }


  private fun calculateSmoothedLatLng(locations: List<LatLng>): LatLng {
    var sumLat = 0.0
    var sumLng = 0.0

    for (location in locations) {
      sumLat += location.latitude
      sumLng += location.longitude
    }

    val smoothedLat = sumLat / locations.size
    val smoothedLng = sumLng / locations.size

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


fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val R = 6371 // Earth's radius in kilometers

  val dLat = deg2rad(lat2 - lat1)
  val dLon = deg2rad(lon2 - lon1)
  val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
          Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
          Math.sin(dLon / 2) * Math.sin(dLon / 2)
  val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  val d = R * c // Distance in km
  return d
}
private fun deg2rad(deg: Double): Double {
  return deg * (Math.PI / 180)
}