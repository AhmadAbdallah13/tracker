package com.redwolf.tracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.SearchView
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : Activity() {

  private lateinit var preferencesHelper: PreferencesHelper
  private lateinit var appsAdapter: AppsAdapter
  private var installedApps: List<AppInfo> = listOf()

  override fun onResume() {
    super.onResume()
    checkAllPermissions()
  }

  @SuppressLint("MissingInflatedId")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    checkAllPermissions()
    fireScheduledUsageTracking()

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
    val infoIcon: ImageView = findViewById(R.id.infoIcon)
    infoIcon.setOnClickListener {
      // Create an AlertDialog
      val builder = AlertDialog.Builder(this)
      builder.setTitle("About This App")
      builder.setMessage("Use any of the toggled apps for 10 minutes straight and all of them will be blocked for 1 hour!")
      builder.setPositiveButton("OK") { dialog, _ ->
        dialog.dismiss() // Close the dialog
      }
      builder.create().show()
    }

    val appSearchView: SearchView = findViewById(R.id.appSearchView)
    // Handle search queries
    appSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
      override fun onQueryTextSubmit(query: String?): Boolean {
        return false // We don't need to handle query submission
      }

      override fun onQueryTextChange(newText: String?): Boolean {
        appsAdapter.filter(newText) // Filter the list based on user input
        return true
      }
    })

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

  private fun checkAllPermissions() {
    fireScheduledUsageTracking()
    if (!hasUsageAccess(this)) {
      val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    } else {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startActivity(intent)
      } else {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
          val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
          intent.data = Uri.parse("package:$packageName")
          startActivity(intent)
        } else {
          if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
          } else {
            val permissionState =
              ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            // If the permission is not granted, request it.
            if (permissionState == PackageManager.PERMISSION_DENIED) {
              ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
              )
            }
          }
        }
      }
    }
  }

  private fun fireScheduledUsageTracking() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (alarmManager.canScheduleExactAlarms()) {
      val intent = Intent(this, UsageMonitorService::class.java)
      val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

      val triggerAtMillis = System.currentTimeMillis() + 15 * 60 * 1000
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent
      )

      startService(intent)
    }
  }
}

data class AppInfo(
  val name: String,
  val packageName: String,
  val icon: Drawable,
  var isToggled: Boolean = false
)

class AppsAdapter(
  private val context: Context,
  private val originalApps: List<AppInfo>,
  private val onToggleChanged: (AppInfo, Boolean) -> Unit
) : BaseAdapter() {

  private val filteredApps: MutableList<AppInfo> = originalApps.toMutableList()

  override fun getCount(): Int = filteredApps.size
  override fun getItem(position: Int): Any = filteredApps[position]
  override fun getItemId(position: Int): Long = position.toLong()

  @SuppressLint("UseSwitchCompatOrMaterialCode")
  override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)

    val appIcon: ImageView = view.findViewById(R.id.appIcon)
    val appName: TextView = view.findViewById(R.id.appName)
    val toggleSwitch: Switch = view.findViewById(R.id.appToggleSwitch)

    val app = filteredApps[position]
    appIcon.setImageDrawable(app.icon)
    appName.text = app.name

    toggleSwitch.setOnCheckedChangeListener(null)

    toggleSwitch.isChecked = app.isToggled

    toggleSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
      if (app.isToggled != isChecked) {
        app.isToggled = isChecked
        onToggleChanged(app, isChecked)
      }
    }

    return view
  }

  // Function to filter apps based on a query
  fun filter(query: String?) {
    filteredApps.clear()
    if (query.isNullOrBlank()) {
      filteredApps.addAll(originalApps) // Show all apps if query is empty
    } else {
      val lowerCaseQuery = query.lowercase()
      filteredApps.addAll(originalApps.filter { it.name.lowercase().contains(lowerCaseQuery) })
    }
    notifyDataSetChanged()
  }
}


fun hasUsageAccess(context: Context): Boolean {
  val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
  val mode =
    appOps.unsafeCheckOpRaw(
      AppOpsManager.OPSTR_GET_USAGE_STATS,
      Process.myUid(),
      context.packageName
    )
  val granted = if (mode == AppOpsManager.MODE_DEFAULT)
    (context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
  else
    (mode == AppOpsManager.MODE_ALLOWED)
  return granted
}
