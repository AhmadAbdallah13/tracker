package io.ionic.starter;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.app.AppOpsManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Calendar;
import java.util.List;

@CapacitorPlugin(name = "AppUsageStats")
public class AppUsageStats extends Plugin {

  @PluginMethod
  public void getUsageStats(PluginCall call) {
    Context context = getContext();

    UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

    Calendar calendar = Calendar.getInstance();
    long endTime = calendar.getTimeInMillis();
    calendar.add(Calendar.DAY_OF_MONTH, -1);
    long startTime = calendar.getTimeInMillis();

    List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

    JSArray statsArray = new JSArray();
    for (UsageStats stats : usageStatsList) {
      JSObject statsObject = new JSObject();
      statsObject.put("packageName", stats.getPackageName());
      statsObject.put("totalTimeForeground", stats.getTotalTimeInForeground());
      statsArray.put(statsObject);
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
