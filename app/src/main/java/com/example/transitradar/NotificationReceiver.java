package com.example.transitradar;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.transitradar.model.LocationModel;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";
    private static final double TRAIN_SPEED_KMH = 41.13;
    private TimerManager timerManager;
    private Runnable notificationTask;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Alarm triggered");
        timerManager = TimerManager.getInstance();

        notificationTask = new Runnable() {
            @Override
            public void run() {
                List<LocationModel> notificationList = Holder.getNotificationList();
                if (notificationList == null || notificationList.isEmpty()) {
                    handleEmptyNotificationList(context);
                    timerManager.unsubscribe(this);
                    return;
                }

                Location currentLocation = Holder.getCurrentLocation();
                NotificationCompat.Builder builder = createNotificationBuilder(context);
                NotificationCompat.InboxStyle inboxStyle = buildNotificationContent(notificationList, currentLocation);

                builder.setStyle(inboxStyle);
                showNotification(context, builder);
            }
        };

        List<LocationModel> notificationList = Holder.getNotificationList();
        if (notificationList == null || notificationList.isEmpty()) {
            timerManager.subscribe(notificationTask);
            notificationTask.run(); // Immediate update
        }

        Location currentLocation = Holder.getCurrentLocation();
        NotificationCompat.Builder builder = createNotificationBuilder(context);
        NotificationCompat.InboxStyle inboxStyle = buildNotificationContent(notificationList, currentLocation);

        builder.setStyle(inboxStyle);
        showNotification(context, builder);
    }

    private NotificationCompat.Builder createNotificationBuilder(Context context) {
        return new NotificationCompat.Builder(context, "TRAIN_ETA_CHANNEL")
                .setSmallIcon(R.drawable.baseline_subway_24)
                .setContentTitle("Train ETA")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
    }

    private NotificationCompat.InboxStyle buildNotificationContent(List<LocationModel> notificationList,
                                                                   Location currentLocation) {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        Pattern tripPattern = Pattern.compile("(\\d{2})(\\d{2})");

        for (LocationModel notification : notificationList) {
            Matcher matcher = tripPattern.matcher(notification.getTripId());
            if (!matcher.find()) continue;

            String closestStation = StationFinder.getClosestStationName(notification);
            if (closestStation.equals("Unknown")) continue;

            double distance = calculateDistance(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    notification.getLatitude(), notification.getLongitude()
            );
            int etaMinutes = (int) ((distance / TRAIN_SPEED_KMH) * 60);

            inboxStyle.addLine(String.format(
                    "Trip: %s | Stn: %s | ETA: %d m",
                    matcher.group(), closestStation, etaMinutes
            ));
        }
        return inboxStyle;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // Convert meters to kilometers
    }

    private void handleEmptyNotificationList(Context context) {
        Log.d(TAG, "onReceive: Notification list is empty or null, cancelling alarm");

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(1);

        Intent alarmIntent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void showNotification(Context context, NotificationCompat.Builder builder) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
        Log.d(TAG, "onReceive: Notification shown for multiple trains");
    }
}