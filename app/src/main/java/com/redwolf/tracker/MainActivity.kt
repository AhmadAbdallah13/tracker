package com.redwolf.tracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView


class MainActivity : Activity() {

  private lateinit var preferencesHelper: PreferencesHelper
  private lateinit var appsAdapter: AppsAdapter
  private var installedApps: List<AppInfo> = listOf()

  @SuppressLint("MissingInflatedId")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    System.out.println("shit called here")

    if (!haveUsageAccess(this)) {
      val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    }

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
      val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      startActivity(intent)
    }

    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (!alarmManager.canScheduleExactAlarms()) {
      val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
      intent.data = Uri.parse("package:$packageName")
      startActivity(intent)
    }

    if (!Settings.canDrawOverlays(this)) {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivityForResult(intent, 1234)
    }

    val intent = Intent(this, UsageMonitorService::class.java)
    val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val triggerAtMillis = System.currentTimeMillis() + 15 * 60 * 1000
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)

    startService(intent)

    preferencesHelper = PreferencesHelper(this)

    val appsListView: ListView = findViewById(R.id.appsListView)
    installedApps = getInstalledApps()

    // Retrieve toggled apps from preferences
    val toggledApps = preferencesHelper.getToggledApps()
    installedApps.forEach { it.isToggled = it.packageName in toggledApps }

    appsAdapter = AppsAdapter(this, installedApps) { app, isToggled ->
      handleToggleChange(app, isToggled)
    }

    appsListView.adapter = appsAdapter
  }

  private fun getInstalledApps(): List<AppInfo> {
    val packageManager = packageManager
    val packages = packageManager.getInstalledApplications(0)

    return packages.filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
      .map {
        AppInfo(
          name = it.loadLabel(packageManager).toString(),
          packageName = it.packageName,
          icon = it.loadIcon(packageManager)
        )
      }
  }

  private fun handleToggleChange(app: AppInfo, isToggled: Boolean) {
    val toggledApps = preferencesHelper.getToggledApps().toMutableSet()
    if (isToggled) {
      toggledApps.add(app.packageName)
    } else {
      toggledApps.remove(app.packageName)
    }
    preferencesHelper.saveToggledApps(toggledApps)
  }
}

private fun haveUsageAccess(activity: MainActivity): Boolean {
//  fixme: make the permission check work.
  return true
//  try {
//    val packageManager: PackageManager = activity.packageManager
//    val applicationInfo = packageManager.getApplicationInfo(activity.packageName, 0)
//    val mode = activity.getSystemService(Context.APP_OPS_SERVICE)
//      .checkOpNoThrow(
//        AppOpsManager.OPSTR_GET_USAGE_STATS,
//        applicationInfo.uid,
//        applicationInfo.packageName
//      )
//    return mode == AppOpsManager.MODE_ALLOWED
//  } catch (e: PackageManager.NameNotFoundException) {
//    return false
//  }
}

data class AppInfo(
  val name: String,
  val packageName: String,
  val icon: Drawable,
  var isToggled: Boolean = false
)

class AppsAdapter(
  private val context: Context,
  private val apps: List<AppInfo>,
  private val onToggleChanged: (AppInfo, Boolean) -> Unit
) : BaseAdapter() {

  override fun getCount(): Int = apps.size
  override fun getItem(position: Int): Any = apps[position]
  override fun getItemId(position: Int): Long = position.toLong()

  @SuppressLint("UseSwitchCompatOrMaterialCode")
  override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)

    val appIcon: ImageView = view.findViewById(R.id.appIcon)
    val appName: TextView = view.findViewById(R.id.appName)
    val toggleSwitch: Switch = view.findViewById(R.id.appToggleSwitch)

    val app = apps[position]
    appIcon.setImageDrawable(app.icon)
    appName.text = app.name
    toggleSwitch.isChecked = app.isToggled

    toggleSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
      app.isToggled = isChecked
      onToggleChanged(app, isChecked)
    }

    return view
  }
}
