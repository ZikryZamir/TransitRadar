package com.example.transitradar;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.transitradar.Holder;
import com.example.transitradar.R;
import com.example.transitradar.model.LocationModel;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";
    //Send notification to user phone
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Alarm triggered");
        List<LocationModel> notificationList = Holder.getNotificationList();
        if (notificationList != null && !notificationList.isEmpty()) {
            Location currentLocation = Holder.getCurrentLocation();
            // Create the notification
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "TRAIN_ETA_CHANNEL")
                    .setSmallIcon(R.drawable.baseline_subway_24)
                    .setContentTitle("Train ETA")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            for (LocationModel notification : notificationList) {
                // Calculate the ETA
                double distance = calculateDistance(currentLocation.getLatitude(), currentLocation.getLongitude(),
                        notification.getLatitude(), notification.getLongitude());
                double eta = (distance / 43.33) * 60; // ETA in minutes
                String tripid = notification.getTripId();
                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(tripid);
                if (matcher.find()) {
                    inboxStyle.addLine("Trip Number: " + matcher.group() + " | ETA: " + (int) eta + " minutes");
                }
            }

            builder.setStyle(inboxStyle);

            // Show the notification
            notificationManager.notify(1, builder.build());
            Log.d(TAG, "onReceive: Notification shown for multiple trains");
        } else {
            Log.d(TAG, "onReceive: Notification list is empty or null, cancelling alarm");
            // Cancel the notification if the list is empty
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(1);

            // Cancel the alarm
            Intent alarmIntent = new Intent(context, NotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // convert meters to kilometers
    }
}
