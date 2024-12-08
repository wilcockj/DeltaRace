package com.example.deltarace

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.deltarace.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.deltarace.LocationSpeedTracker
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var logTextView: TextView
    private lateinit var viewModel: LocationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        /*
        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
         */
        // Button to start location tracking
        findViewById<Button>(R.id.btnStartTracking).setOnClickListener {
            checkLocationPermissionsAndStartTracking()
        }

        // Button to stop location tracking
        findViewById<Button>(R.id.btnStopTracking).setOnClickListener {
            stopLocationTracking()
        }

        logTextView = findViewById(R.id.logTextView)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]

        // Observe location updates
        viewModel.recentLocations.observe(this) { locations ->
            // Update TextView with recent locations
            logTextView.text = locations.joinToString("\n") { location ->
                "ID: ${location.id}, " +
                        "Lat: ${location.latitude}, " +
                        "Lon: ${location.longitude}, " +
                        "Speed: ${location.speed} m/s, " +
                        "Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(location.timestamp)}"
            }
        }

        findViewById<Button>(R.id.btnViewMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
    private fun checkLocationPermissionsAndStartTracking() {
        // Check if location permissions are granted
        if (hasLocationPermissions()) {
            startLocationTracking()
        } else {
            // Request permissions if not granted
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationTracking()
            } else {
                Toast.makeText(
                    this,
                    "Location permissions are required to track speed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startLocationTracking() {
        Log.d(TAG, "Attempting to Start Location Tracking")
        val intent = Intent(this, LocationTrackingService::class.java)
        startService(intent)
        Toast.makeText(this, "Location Tracking Started", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Started location tracking service from main activity")
    }

    private fun stopLocationTracking() {
        val intent = Intent(this, LocationTrackingService::class.java)
        stopService(intent)
        Toast.makeText(this, "Location Tracking Stopped", Toast.LENGTH_SHORT).show()
    }
}

