package com.redwolf.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private var isBlocked: Boolean = false

class UsageMonitorService : Service() {

  private lateinit var handler: Handler
  private lateinit var preferencesHelper: PreferencesHelper
  private val usageCheckInterval: Long = 15 * 60 * 1000 // 15 minutes

  private val usageLimit: Long = 10 * 60 * 1000 // 10 minutes
//  private val usageLimit: Long = 15000 // 15 seconds for testing

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
      .setContentTitle("Tracker")
      .setContentText("Tracking app usage in the background.")
      .setSmallIcon(R.mipmap.ic_launcher)
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
          if (currentForegroundApp != event.packageName) {
            currentForegroundApp = event.packageName
            foregroundStartTime = event.timeStamp
          }
        }

        UsageEvents.Event.ACTIVITY_PAUSED -> {
          if (currentForegroundApp == event.packageName) {
            currentAppDuration = event.timeStamp - foregroundStartTime
            currentForegroundApp = null
            foregroundStartTime = 0
          }
        }
      }
    }

    // If the app is still in the foreground, update the duration
    if (currentForegroundApp != null) {
      currentAppDuration = currentTime - foregroundStartTime
    }

    currentForegroundApp?.let { app ->
      if (toggledApps.contains(app) && isBlocked) {
        showOverlay(app)
      } else if (toggledApps.contains(app) && currentAppDuration > usageLimit) {
        Log.d(
          "UsageMonitorService",
          "App $app exceeded usage limit: $currentAppDuration ms more than usageLimit"
        )
        isBlocked = true
        // Schedule the unblocking after 1 hour
        val workRequest = OneTimeWorkRequestBuilder<UnblockWorker>()
          .setInitialDelay(1, TimeUnit.HOURS) // 1 hour delay
//          .setInitialDelay(30, TimeUnit.SECONDS) // 30 seconds for testing
          .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        showOverlay(app)
      }
    }

    // Debugging logs
//    println("Currently tracked app: $currentForegroundApp")
//    println("Current app usage duration: $currentAppDuration")
  }

  private fun showOverlay(app: String) {
    val overlayIntent = Intent(this, OverlayService::class.java)
    val packageManager = applicationContext.packageManager
    val appName = try {
      val applicationInfo = packageManager.getApplicationInfo(app, 0)
      packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
      app
    }
    overlayIntent.putExtra("APP_NAME", appName)
    startService(overlayIntent)
  }
}

class UnblockWorker(appContext: Context, workerParams: WorkerParameters) :
  Worker(appContext, workerParams) {
  override fun doWork(): Result {
    // Perform unblocking logic
    liftBlocking()
    return Result.success()
  }

  private fun liftBlocking() {
    isBlocked = false

    // Show unblocking notification or perform other actions
    val notificationManager =
      applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "unblocking_channel"

    val channel = NotificationChannel(
      channelId,
      "Blocking Notifications",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = "Notifications for app blocking status"
    }
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(applicationContext, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Tracker")
      .setContentText("You can now use the blocked apps again, enjoy amigo.")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .build()

    notificationManager.notify(1, notification)
  }
}
