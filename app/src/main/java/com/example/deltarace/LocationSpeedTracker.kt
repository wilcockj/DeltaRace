package com.example.deltarace

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.Query
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private var TAG = "SpeedTracker"

// If you want to make the entity Parcelable (optional, but useful for passing between components)
@Entity(tableName = "location_speed_data")
@Parcelize
data class LocationSpeedData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readLong(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readFloat()
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeLong(timestamp)
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeFloat(speed)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LocationSpeedData> {
        override fun createFromParcel(parcel: Parcel): LocationSpeedData {
            return LocationSpeedData(parcel)
        }

        override fun newArray(size: Int): Array<LocationSpeedData?> {
            return arrayOfNulls(size)
        }
    }
}

annotation class Parcelize


// Update the DAO to return LiveData
@Dao
interface LocationSpeedDao {
    @Insert
    fun insertLocationSpeed(locationSpeedData: LocationSpeedData)

    // Add this method to retrieve recent locations as LiveData
    @Query("SELECT * FROM location_speed_data ORDER BY timestamp DESC LIMIT 1000")
    fun getRecentLocations(): LiveData<List<LocationSpeedData>>
}

// Room Database
@Database(entities = [LocationSpeedData::class], version = 1)
abstract class LocationSpeedDatabase : RoomDatabase() {
    abstract fun locationSpeedDao(): LocationSpeedDao

    companion object {
        @Volatile
        private var INSTANCE: LocationSpeedDatabase? = null

        fun getDatabase(context: Context): LocationSpeedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationSpeedDatabase::class.java,
                    "location_speed_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Optionally, create a ViewModel to manage the data
class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LocationSpeedDatabase.getDatabase(application)
    val recentLocations: LiveData<List<LocationSpeedData>> = database.locationSpeedDao().getRecentLocations()
}

// Main tracking class
class LocationSpeedTracker(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.create().apply {
        interval = 10000 // 10 seconds
        fastestInterval = 5000 // 5 seconds
        priority = Priority.PRIORITY_HIGH_ACCURACY
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                saveLocationSpeed(location)
            }
        }
    }

    private val database = LocationSpeedDatabase.getDatabase(context)

    fun startLocationTracking() {
        try {
            Log.d(TAG, "Started location tracking")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Location permission not granted", securityException)
        }
    }

    fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun saveLocationSpeed(location: Location) {
        coroutineScope.launch(Dispatchers.IO) {
            val locationSpeedData = LocationSpeedData(
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed
            )

            database.locationSpeedDao().insertLocationSpeed(locationSpeedData)
            Log.d(TAG, "Saved location: ${location.latitude}, ${location.longitude}, Speed: ${location.speed}")
        }
    }
}

// Foreground Service for Location Tracking
class LocationTrackingService: Service() {
    private lateinit var locationSpeedTracker: LocationSpeedTracker
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating location tracking service")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        locationSpeedTracker = LocationSpeedTracker(
            applicationContext,
            serviceScope
        )
    }

    private fun createNotificationChannel() {
        Log.d(TAG,"Creating notification to be able to track in background")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking your location in the background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Create this icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        return notificationBuilder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting location tracking inside of onStartCommand")
        // Start location tracking
        locationSpeedTracker.startLocationTracking()

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    override fun onDestroy() {
        locationSpeedTracker.stopLocationTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "LocationTrackingService"
    }
}