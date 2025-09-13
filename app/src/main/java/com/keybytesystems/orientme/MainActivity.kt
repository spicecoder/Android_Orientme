package com.keybytesystems.orientme

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var statusTextView: TextView
    private lateinit var coordinatesTextView: TextView
    private lateinit var movementTextView: TextView
    private lateinit var lastUpdateTextView: TextView

    // Location services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                showToast("✅ Fine location permission granted")
                startLocationUpdates()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                showToast("✅ Coarse location permission granted")
                startLocationUpdates()
            }
            else -> {
                showToast("❌ Location permission denied")
                updateStatusDisplay("Permission denied - Limited functionality")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        initializeViews()

        // Initialize location services
        initializeLocationServices()

        // Setup initial display
        setupInitialDisplay()

        // Check and request permissions
        checkLocationPermissions()
    }

    private fun initializeViews() {
        statusTextView = findViewById(R.id.statusTextView)
        coordinatesTextView = findViewById(R.id.coordinatesTextView)
        movementTextView = findViewById(R.id.movementTextView)
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView)
    }

    private fun setupInitialDisplay() {
        statusTextView.text = "🔍 Initializing Orient Me..."
        coordinatesTextView.text = "📍 Waiting for location data..."
        movementTextView.text = "🚶 Movement data pending..."
        lastUpdateTextView.text = "🕐 No updates yet"
    }

    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create high-accuracy location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000L)
            .setMaxUpdateDelayMillis(15000L)
            .build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    updateLocationDisplay(location)
                }
            }
        }
    }

    private fun checkLocationPermissions() {
        when {
            hasLocationPermissions() -> {
                showToast("✅ Location permissions already granted")
                startLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionRationale()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("📍 Location Permission Required")
            .setMessage("""
                Orient Me needs location access to provide you with:
                
                • Current street name and address
                • Precise coordinates (lat/long)
                • Altitude and movement data
                • Nearby points of interest
                • Weather and environmental data
                
                Your location data stays on your device.
            """.trimIndent())
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateStatusDisplay("❌ Permission denied - Limited functionality")
            }
            .show()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermissions()) {
            updateStatusDisplay("❌ Missing location permissions")
            return
        }

        try {
            // Start location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )

            // Get last known location immediately
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        updateLocationDisplay(location)
                    } else {
                        updateStatusDisplay("🔍 Searching for GPS signal...")
                    }
                }
                .addOnFailureListener { exception ->
                    updateStatusDisplay("❌ Failed to get location: ${exception.message}")
                }

            updateStatusDisplay("🔍 Getting your location...")

        } catch (e: SecurityException) {
            updateStatusDisplay("❌ Security error: ${e.message}")
        }
    }

    private fun updateLocationDisplay(location: Location) {
        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormatter.format(Date())

        // Status
        updateStatusDisplay("✅ Location updated successfully")

        // Coordinates
        val coordinatesText = """
            📍 COORDINATES
            Latitude: ${String.format("%.6f", location.latitude)}
            Longitude: ${String.format("%.6f", location.longitude)}
            Accuracy: ${location.accuracy.toInt()}m
        """.trimIndent()
        coordinatesTextView.text = coordinatesText

        // Movement data
        val movementText = """
            🚶 MOVEMENT DATA
            Speed: ${if (location.hasSpeed()) "${String.format("%.1f", location.speed * 3.6)} km/h" else "Stationary"}
            Bearing: ${if (location.hasBearing()) "${location.bearing.toInt()}°" else "Unknown"}
            Altitude: ${if (location.hasAltitude()) "${location.altitude.toInt()}m" else "Unknown"}
        """.trimIndent()
        movementTextView.text = movementText

        // Last update
        lastUpdateTextView.text = "🕐 Last update: $currentTime"

        // Log detailed info for debugging
        logLocationDetails(location)
    }

    private fun logLocationDetails(location: Location) {
        val locationInfo = """
            ===== ORIENT ME LOCATION UPDATE =====
            📍 Coordinates: ${location.latitude}, ${location.longitude}
            📏 Accuracy: ${location.accuracy}m
            ⛰️ Altitude: ${if (location.hasAltitude()) "${location.altitude}m" else "N/A"}
            🧭 Bearing: ${if (location.hasBearing()) "${location.bearing}°" else "N/A"}
            ⚡ Speed: ${if (location.hasSpeed()) "${location.speed * 3.6} km/h" else "0 km/h"}
            🕐 Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
            🎯 Provider: ${location.provider}
            =====================================
        """.trimIndent()

        println(locationInfo)
    }

    private fun updateStatusDisplay(status: String) {
        statusTextView.text = status
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermissions()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}