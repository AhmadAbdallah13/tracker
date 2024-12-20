package io.ionic.starter;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.app.AppOpsManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@CapacitorPlugin(name = "AppUsageStats")
public class AppUsageStats extends Plugin {

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
}
