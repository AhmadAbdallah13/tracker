package com.redwolf.tracker

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView


class OverlayService : Service() {

  private var overlayView: View? = null

  @SuppressLint("InflateParams")
  override fun onCreate() {
    super.onCreate()

    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    overlayView = layoutInflater.inflate(R.layout.overlay_layout, null)
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    )

    val closeButton: TextView = overlayView!!.findViewById(R.id.close_button)
    closeButton.setOnClickListener {
      stopSelf()
      val homeIntent = Intent(Intent.ACTION_MAIN)
      homeIntent.addCategory(Intent.CATEGORY_HOME)
      homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      startActivity(homeIntent)
    }

    windowManager.addView(overlayView, params)
  }

  @SuppressLint("SetTextI18n")
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val appName = intent?.getStringExtra("APP_NAME")
    val overlayMessage: TextView = overlayView!!.findViewById(R.id.overlay_message)
    overlayMessage.text = "You have been using $appName for too long. Take a break!"
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    if (overlayView != null) {
      val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
      windowManager.removeView(overlayView)
      overlayView = null
    }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
