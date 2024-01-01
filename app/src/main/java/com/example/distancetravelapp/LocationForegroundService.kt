package com.example.distancetravelapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LocationForegroundService : Service() {
    private lateinit var locationProvider: LocationProvider

    override fun onCreate() {
        super.onCreate()

        // Create a notification channel (required on Android 8.0 and higher)
        createNotificationChannel()

        // Create a notification
        val notification = createNotification()

        // Make the service run in the foreground to avoid being killed by the system
        startForeground(NOTIFICATION_ID, notification)

        // Start your location tracking logic here
        locationProvider = LocationProvider(this)
        locationProvider.trackUser()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The rest of your onStartCommand logic
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop your location tracking logic here
        locationProvider.stopTracking()

        // Release the WakeLock if acquired
        locationProvider.releaseWakeLock()

        // Remove the service from the foreground
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Your Channel Name",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            // Set any additional properties for the channel if needed
            // ...
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create a notification with a PendingIntent that opens the app when tapped
        val notificationIntent = Intent(this, MapsActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("App is running in background.....")
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "1"
        private const val NOTIFICATION_ID = 1
    }
}