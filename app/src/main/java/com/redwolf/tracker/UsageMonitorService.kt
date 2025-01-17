package com.redwolf.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat


class UsageMonitorService : Service() {

  private lateinit var handler: Handler
  private lateinit var preferencesHelper: PreferencesHelper
  private val trackedAppUsage = mutableMapOf<String, Long>()
  private val usageCheckInterval: Long = 600000 // 10 minutes

  //    private val usageLimit: Long = 10 * 60 * 1000 // 10 minutes
  private val usageLimit: Long = 15000 // .25 minute

  override fun onCreate() {
    super.onCreate()
    handler = Handler(Looper.getMainLooper())
    preferencesHelper = PreferencesHelper(this)
    startUsageMonitoring()
  }

  private fun createNotification(): Notification {
    val notificationChannelId = "background_service_channel"

    val channel = NotificationChannel(
      notificationChannelId,
      "Background Service",
      NotificationManager.IMPORTANCE_LOW
    )
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)

    return NotificationCompat.Builder(this, notificationChannelId)
      .setContentTitle("App Tracker")
      .setContentText("Tracking app usage in the background.")
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .build()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = createNotification()
    startForeground(1, notification)
    return START_STICKY
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startUsageMonitoring() {
    handler.post(object : Runnable {
      override fun run() {
        checkAppUsage()
        handler.postDelayed(this, 1000)
      }
    })
  }

  private fun checkAppUsage() {
    val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val currentTime = System.currentTimeMillis()
    val startTime = currentTime - usageCheckInterval

    val toggledApps = preferencesHelper.getToggledApps()
    val usageEvents = usageStatsManager.queryEvents(startTime, currentTime)
    val event = UsageEvents.Event()
    var currentForegroundApp: String? = null
    var foregroundStartTime: Long = 0
    var currentAppDuration: Long = 0

    while (usageEvents.hasNextEvent()) {
      usageEvents.getNextEvent(event)

      when (event.eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> {
          // If a new app is opened, reset tracking
          if (currentForegroundApp != event.packageName) {
            currentForegroundApp = event.packageName
            foregroundStartTime = event.timeStamp
          }
        }

        UsageEvents.Event.ACTIVITY_PAUSED -> {
          // If the currently tracked app goes to the background, calculate its usage
          if (currentForegroundApp == event.packageName) {
            currentAppDuration = event.timeStamp - foregroundStartTime
            currentForegroundApp = null
            foregroundStartTime = 0
          }
        }
      }
    }

//     If the app is still in the foreground, update the duration
    if (currentForegroundApp != null) {
      currentAppDuration = currentTime - foregroundStartTime
    }

    // Log or take action if the duration exceeds the limit
    currentForegroundApp?.let { app ->
      if (toggledApps.contains(app) && currentAppDuration > usageLimit) {
        val packageManager = applicationContext.packageManager
        val appName = try {
          val applicationInfo = packageManager.getApplicationInfo(app, 0)
          packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
          app // Fallback to package name if the app name cannot be retrieved
        }

        Log.d(
          "UsageMonitorService",
          "App $appName exceeded usage limit: $currentAppDuration ms more than usageLimit"
        )
        showOverlay(appName) // Pass the app name to your overlay logic if needed
      }
    }

    // Debugging logs
    System.out.println("Currently tracked app: $currentForegroundApp")
    System.out.println("Current app usage duration: $currentAppDuration")
  }

  private fun showOverlay(app: String) {
    val overlayIntent = Intent(this, OverlayService::class.java)
    overlayIntent.putExtra("APP_NAME", app)
    startService(overlayIntent)
  }
}
