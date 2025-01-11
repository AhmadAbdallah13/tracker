package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.app.AppOpsManager;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import androidx.core.app.NotificationCompat;

@CapacitorPlugin(name = "AppUsageStats")
public class AppUsageStats extends Plugin {
  @PluginMethod
  public void getCurrentApp(PluginCall call) {
    Context context = getContext();
    UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

    long currentTime = System.currentTimeMillis();
    long startTime = currentTime - 1000 * 60 * 10; // Check for the last 10 minutes
    UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);

    String currentApp = null;

    UsageEvents.Event event = new UsageEvents.Event();
    while (usageEvents.hasNextEvent()) {
      usageEvents.getNextEvent(event);
      String packageName = event.getPackageName();

      if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
        currentApp = packageName;
      }
    }
    JSObject result = new JSObject();
    result.put("currentApp", currentApp);
    call.resolve(result);
  }

  @PluginMethod
  public void periodicCalls(PluginCall call) {
    Context context = getContext();
    UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

    long currentTime = System.currentTimeMillis();
    long startTime = currentTime - 1000 * 60 * 10; // Check for the last 10 minutes

    UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);

    String currentApp = null;
    long appStartTime = 0;
    long appDuration = 0;

    UsageEvents.Event event = new UsageEvents.Event();
    while (usageEvents.hasNextEvent()) {
      usageEvents.getNextEvent(event);
      String packageName = event.getPackageName();

      if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
        // Start tracking the new app
        currentApp = packageName;
        appStartTime = event.getTimeStamp();
      } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
        // Calculate time spent in foreground for the current app
        if (currentApp != null && currentApp.equals(packageName)) {
          appDuration = event.getTimeStamp() - appStartTime;
          if (appDuration > 10 * 60 * 1000) { // More than 10 minutes
            System.out.println("App exceeded 10 minutes in a single session: " + currentApp);
          }
          // Reset tracking
          currentApp = null;
          appStartTime = 0;
        }
      }
    }

    if (currentApp != null && appStartTime > 0) {
      appDuration = currentTime - appStartTime;
      if (appDuration > 10 * 60 * 1000) { // More than 10 minutes
        System.out.println("App exceeded 10 minutes in a single session: " + currentApp);
      }
    }

    JSObject result = new JSObject();
    result.put("currentApp", currentApp);
    result.put("duration", appDuration);
    call.resolve(result);
  }

  @PluginMethod
  public void getInstalledAppsUsageStats(PluginCall call) {
      Context context = getContext();
      UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

      Calendar calendar = Calendar.getInstance();
      calendar.setTimeZone(TimeZone.getTimeZone("Asia/Amman"));
      long endTime = System.currentTimeMillis();
      calendar.set(Calendar.HOUR_OF_DAY,0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND,0);
      long startTime = calendar.getTimeInMillis();

      List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

      JSArray statsArray = new JSArray();

      Map<String , Long> usageTimeOfApps = new HashMap<>();
      Map<String , Long> prev = new HashMap<>();

      UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
      usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
      while (usageEvents.hasNextEvent()) {
        UsageEvents.Event event = new UsageEvents.Event();
        usageEvents.getNextEvent(event);
        String currPackageName=event.getPackageName();

        if(!usageTimeOfApps.containsKey(currPackageName)){
          usageTimeOfApps.put(currPackageName, 0L);
        }
        if(!prev.containsKey(currPackageName)){
          prev.put(currPackageName, -1L);
        }

        if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
          prev.put(currPackageName, event.getTimeStamp());
        } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && prev.get(currPackageName) != -1) {
          Long time = usageTimeOfApps.get(currPackageName) + (event.getTimeStamp() - prev.get(currPackageName));
          usageTimeOfApps.put(currPackageName, time);
        }
      }

      PackageManager packageManager = context.getPackageManager();

      for (Map.Entry<String, Long> entry : usageTimeOfApps.entrySet()) {
        if(entry.getValue() > 0){
          String packageName = entry.getKey();
          String appName;

          try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            appName = packageManager.getApplicationLabel(appInfo).toString();
          } catch (PackageManager.NameNotFoundException e) {
            appName = packageName;
          }

          JSObject statsObject = new JSObject();
          statsObject.put("packageName", packageName);
          statsObject.put("appName", appName);
          statsObject.put("totalTimeForeground", entry.getValue());
          statsArray.put(statsObject);
        }
      }

    JSObject result = new JSObject();
    result.put("stats", statsArray);
    call.resolve(result);
  }

  @PluginMethod
  public void getUsageAccess(PluginCall call) {
    Context context = getContext();


//    todo: add this permission check to the list of checks and prompts.
//    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
//    getActivity().startActivity(intent);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!Settings.canDrawOverlays(context)){
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        getActivity().startActivity(intent);
      }
    }

    AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());

    if (mode == AppOpsManager.MODE_ALLOWED) {
      call.resolve(new JSObject().put("granted", true));
    } else {
      call.resolve(new JSObject().put("granted", false));
    }
  }

  @PluginMethod
  public void grantUsageAccess(PluginCall call) {
    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    getActivity().startActivity(intent);
  }

  @PluginMethod
  public void getInstalledApps(PluginCall call) {
    Context context = getContext();
    PackageManager packageManager = context.getPackageManager();

    // Get a list of all installed apps
    List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

    JSArray appsArray = new JSArray();

    for (ApplicationInfo appInfo : installedApps) {
      // Check if the app is launchable by the user
      Intent launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName);
      if (launchIntent == null) {
        continue; // Skip apps that can't be launched
      }

      // Create a JSON object for each app
      JSObject appObject = new JSObject();
      appObject.put("packageName", appInfo.packageName);
      appObject.put("appName", packageManager.getApplicationLabel(appInfo).toString());

      try {
        Drawable icon = packageManager.getApplicationIcon(appInfo);

        // Convert Drawable to Bitmap
        Bitmap bitmap = getBitmapFromDrawable(icon);

        // Convert Bitmap to Base64
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        String iconBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

        appObject.put("icon", "data:image/png;base64," + iconBase64);
      } catch (Exception e) {
        appObject.put("icon", ""); // Fallback if icon fetching fails
      }

      appsArray.put(appObject);
    }

    JSObject result = new JSObject();
    result.put("apps", appsArray);
    call.resolve(result);
  }

  // Helper method to convert any Drawable to Bitmap
  private Bitmap getBitmapFromDrawable(Drawable drawable) {
    if (drawable instanceof BitmapDrawable) {
      return ((BitmapDrawable) drawable).getBitmap();
    }

    // For non-bitmap drawables (e.g., vector), render onto a Canvas
    int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 100;
    int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 100;
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);
    return bitmap;
  }

  @PluginMethod
  public void showOverlay(PluginCall call) {
    Context context = getContext();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      if (!Settings.canDrawOverlays(context)) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
        call.reject("Overlay permission not granted");
        return;
      }
    }

    Intent serviceIntent = new Intent(context, OverlayService.class);
    context.startService(serviceIntent);

    showNotification("apps usage has been blocked for the next hour");

    call.resolve();
  }

  @PluginMethod
  public void liftBlockNotification(PluginCall call) {
    showNotification("apps usage blocking has been lifted, enjoy!");
    call.resolve();
  }

  private void showNotification(String content) {
    Context context = getContext();
    Notification notification = new NotificationCompat.Builder(context, "dummy_channel")
      .setContentTitle("Apps usage")
      .setContentText(content)
//      .setSmallIcon(R.drawable.your_icon)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build();

    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(2, notification); // Use a unique ID for cooldown notifications
  }

}
