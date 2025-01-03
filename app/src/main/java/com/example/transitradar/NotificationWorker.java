package com.example.transitradar;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class NotificationWorker extends Worker {

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        /*List<String> notificationList = BottomMarkerDialogFragment.getNotificationList();
        for (String Tripid : notificationList) {
            // Fetch updated ETA information for each train and create a notification
            double updatedEta = fetchUpdatedEta(Tripid);

            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "ETA_CHANNEL")
                    .setContentTitle("Train ETA Update")
                    .setContentText("Trip ID: " + Tripid + " ETA: " + updatedEta + " minutes")
                    .setSmallIcon(R.drawable.baseline_notifications_24)
                    .build();

            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(Tripid.hashCode(), notification);
        }*/
        return Result.success();
    }

    private double fetchUpdatedEta(String Tripid) {
        // Implement the logic to fetch updated ETA
        return 0;
    }
}
