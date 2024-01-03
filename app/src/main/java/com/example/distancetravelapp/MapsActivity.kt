package com.example.distancetravelapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.annotation.SuppressLint
import android.os.SystemClock
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.example.distancetravelapp.databinding.ActivityMapsBinding
import com.google.android.gms.maps.model.Marker


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

  private lateinit var map: GoogleMap
  private lateinit var locationProvider: LocationProvider

  private lateinit var binding: ActivityMapsBinding

  private val presenter = MapPresenter(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.AppTheme)

    super.onCreate(savedInstanceState)

    binding = ActivityMapsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val mapFragment = supportFragmentManager
      .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    locationProvider = LocationProvider(this)

    binding.btnStartStop.setOnClickListener {
      if (binding.btnStartStop.text == getString(R.string.start_label)) {
        startTracking()
        binding.btnStartStop.setText(R.string.stop_label)
      } else {
        stopTracking()
        binding.btnStartStop.setText(R.string.start_label)
      }
    }

    presenter.onViewCreated()
  }


  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap
    locationProvider.initializeMap(map)

    presenter.ui.observe(this) { ui ->
      updateUi(ui)
    }
    presenter.onMapLoaded()
    map.uiSettings.isZoomControlsEnabled = true
  }

  private fun startTracking() {
    binding.container.txtPace.text = ""
    binding.container.txtDistance.text = ""
    binding.container.txtTime.base = SystemClock.elapsedRealtime()
    binding.container.txtTime.start()
    map.clear()

    presenter.startTracking()
  }

  private fun stopTracking() {
    presenter.stopTracking()
    binding.container.txtTime.stop()
  }

  @SuppressLint("MissingPermission")
  private fun updateUi(ui: Ui) {
    if (ui.currentLocation != null && ui.currentLocation != map.cameraPosition.target) {
      map.isMyLocationEnabled = true
      map.animateCamera(CameraUpdateFactory.newLatLngZoom(ui.currentLocation, 18f))
    }
    binding.container.txtDistance.text = ui.formattedDistance
    binding.container.txtPace.text = ui.formattedPace
    drawRoute(ui.userPath)
  }

  private fun drawRoute(locations: List<LatLng>) {
    val polylineOptions = PolylineOptions()

//    map.clear()
    val points = polylineOptions.points
    points.addAll(locations)

    map.addPolyline(polylineOptions)
  }

}