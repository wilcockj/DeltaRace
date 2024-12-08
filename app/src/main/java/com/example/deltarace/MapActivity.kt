package com.example.deltarace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewModel: LocationViewModel
    private var currentZoom: Double = 15.0
    private var currentCenter: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getPreferences(MODE_PRIVATE)
        )

        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]

        viewModel.recentLocations.observe(this) { locations ->
            // Store current view state before updating
            currentZoom = mapView.zoomLevelDouble
            currentCenter = mapView.mapCenter as GeoPoint?

            // Clear existing overlays
            mapView.overlays.clear()

            // Create a polyline to show the route
            val routeLine = Polyline()
            routeLine.color = 0xFF0000FF.toInt() // Blue color

            // Add markers and route points
            locations.forEach { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)

                // Add point to route line
                routeLine.addPoint(geoPoint)

                // Create a marker
                val marker = Marker(mapView)
                marker.position = geoPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "Speed: ${location.speed} m/s"
                marker.snippet = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(location.timestamp)

                // Add marker to map
                mapView.overlays.add(marker)
            }

            // Add route line to map
            mapView.overlays.add(routeLine)

            // Restore previous view state
            if (currentCenter != null) {
                val mapController = mapView.controller
                mapController.setCenter(currentCenter)
                mapController.setZoom(currentZoom)
            } else if (locations.isNotEmpty()) {
                // If no previous state, center on last location
                val lastLocation = locations.last()
                val mapController = mapView.controller
                mapController.setCenter(GeoPoint(lastLocation.latitude, lastLocation.longitude))
                mapController.setZoom(15.0)
            }

            // Refresh the map
            mapView.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
