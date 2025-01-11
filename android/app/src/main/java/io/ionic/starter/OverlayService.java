package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;


public class OverlayService extends Service {

  private WindowManager windowManager;
  private View overlayView;

  @Override
  public void onCreate() {
    super.onCreate();

    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

    // Inflate your custom overlay layout
    LayoutInflater inflater = LayoutInflater.from(this);
    overlayView = inflater.inflate(R.layout.overlay_layout, null);

    // Set layout parameters for the overlay
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    );

    params.gravity = Gravity.CENTER;
    windowManager.addView(overlayView, params);

    // Set up the close button or any other functionality
    TextView closeBtn = overlayView.findViewById(R.id.close_button);
    closeBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Redirect the user to the home screen
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplicationContext().startActivity(intent);
        stopSelf();

      }
    });
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (overlayView != null) {
      windowManager.removeView(overlayView);
    }
  }

//  @Override
//  public int onStartCommand(Intent intent, int flags, int startId) {
//    // Handle service tasks
//    return START_STICKY; // Ensures the service restarts automatically if terminated
//  }

  private void showNotification(String title, String content) {
    Notification notification = new NotificationCompat.Builder(this, "OverlayServiceChannel")
      .setContentTitle(title)
      .setContentText(content)
//      .setSmallIcon(R.drawable.ic_notification)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build();

    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(2, notification); // Use a unique ID for cooldown notifications
  }

}
