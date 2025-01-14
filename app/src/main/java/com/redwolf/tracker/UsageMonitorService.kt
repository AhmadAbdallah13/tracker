package com.redwolf.tracker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

class UsageMonitorService : Service() {

    private lateinit var handler: Handler
    private lateinit var preferencesHelper: PreferencesHelper
    private val trackedAppUsage = mutableMapOf<String, Long>()
    private val usageCheckInterval: Long = 1000 // 1 second
//    private val usageLimit: Long = 10 * 60 * 1000 // 10 minutes
    private val usageLimit: Long = 120000 // 2 minutes

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        preferencesHelper = PreferencesHelper(this)
        System.out.println("shit started")
        startUsageMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                handler.postDelayed(this, usageCheckInterval)
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
        var currentApp: String? = null
        var foregroundStartTime: Long = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
                foregroundStartTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED && event.packageName == currentApp) {
                if (currentApp != null && event.packageName == currentApp) {
                    val duration = event.timeStamp - foregroundStartTime
                    trackedAppUsage[currentApp] = duration
                    currentApp = null
                }
            }
        }

        System.out.println("periodic shit")
        System.out.println(currentApp)
        System.out.println(trackedAppUsage[currentApp])

        // If a tracked app exceeds the limit, trigger the overlay service
        currentApp?.let { app ->
            if (toggledApps.contains(app)) {
                val duration = (currentTime - (trackedAppUsage[app] ?: currentTime))
                if (duration > usageLimit) {
                    Log.d("UsageMonitorService", "App $app exceeded usage limit: $duration ms")
                    showOverlay(app)
                }
            }
        }
    }

    private fun showOverlay(app: String) {
        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.putExtra("APP_NAME", app)
        startService(overlayIntent)
    }
}
