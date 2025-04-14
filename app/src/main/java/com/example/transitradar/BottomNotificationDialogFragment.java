package com.example.transitradar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.transitradar.model.LocationModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BottomNotificationDialogFragment extends BottomSheetDialogFragment {
    private static final String TAG = "BottomNotificationDialogFragment";
    private static final double TRAIN_SPEED_KMH = 41.13;
    private final List<LocationModel> notificationList = Holder.getNotificationList();
    private final List<LocationModel> locationModels = Holder.getLocationModels();
    private final Location currentLocation = Holder.getCurrentLocation();
    private TimerManager timerManager;
    private Runnable updateTask;

    public static BottomNotificationDialogFragment newInstance() {
        return new BottomNotificationDialogFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        timerManager = TimerManager.getInstance();
        setupUpdateTask();
        setRepeatingAlarm();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        timerManager.unsubscribe(updateTask);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_notification_layout, container, false);
        updateNotificationList(view);
        return view;
    }

    private void setupUpdateTask() {
        updateTask = this::checkForUpdates;
        if (notificationList != null && !notificationList.isEmpty()) {
            timerManager.subscribe(updateTask); // Only subscribe if list is non-empty
        }
    }

    private void updateNotificationList(View view) {
        LinearLayout container = view.findViewById(R.id.notification_container);
        container.removeAllViews();
        addHeader(container);

        if (isNotificationListEmpty()) {
            addNoNotificationsView(container);
            timerManager.unsubscribe(updateTask); // Unsubscribe if list becomes empty
            return;
        }

        timerManager.subscribe(updateTask); // Re-subscribe if list becomes non-empty
        updateNotifications();
        displayNotifications(container);
    }

    private void addHeader(LinearLayout container) {
        View headerView = getLayoutInflater().inflate(R.layout.notification_header, container, false);
        container.addView(headerView);
    }

    private void addNoNotificationsView(LinearLayout container) {
        View noNotificationsView = getLayoutInflater().inflate(R.layout.no_notifications_layout, container, false);
        container.addView(noNotificationsView);
    }

    private void updateNotifications() {
        for (LocationModel notification : notificationList) {
            updateLocationData(notification);
        }
    }

    private void updateLocationData(LocationModel notification) {
        for (LocationModel location : locationModels) {
            if (notification.getTripId().equals(location.getTripId())) {
                notification.setLatitude(location.getLatitude());
                notification.setLongitude(location.getLongitude());
                break;
            }
        }
    }

    private void displayNotifications(LinearLayout container) {

        for (LocationModel notification : notificationList) {
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(notification.getTripId());
            if (!matcher.find()) return;
            View itemView = getLayoutInflater().inflate(R.layout.notification_item, container, false);

            TextView tripIdText = itemView.findViewById(R.id.text_trip_id);
            TextView etaText = itemView.findViewById(R.id.text_eta);
            ImageButton removeButton = itemView.findViewById(R.id.button_remove);

            String tripText = "Trip: "+ matcher.group() +" "+ RouteDeterminer.determineRouteText(notification.getTripId());
            double eta = calculateEta(notification);
            String closestStation = StationFinder.getClosestStationName(notification);

            tripIdText.setText(tripText);
            etaText.setText(String.format("Station: %s | ETA: %d minutes", closestStation, (int) eta));
            setupRemoveButton(removeButton, notification, container.getRootView());

            container.addView(itemView);
        }
    }

    private double calculateEta(LocationModel notification) {
        double distance = calculateDistance(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                notification.getLatitude(), notification.getLongitude()
        );
        return (distance / TRAIN_SPEED_KMH) * 60;
    }

    private void setupRemoveButton(ImageButton button, LocationModel notification, View view) {
        button.setOnClickListener(v -> {
            notificationList.remove(notification);
            Holder.setNotificationList(notificationList);
            updateNotificationList(view);
        });
    }

    private void setRepeatingAlarm() {
        Context context = getContext();
        if (context == null) return;

        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis(),
                    timerManager.getInterval(),
                    pendingIntent
            );
            Log.d(TAG, "Repeating alarm set");
        }
    }

    private void checkForUpdates() {
        if (getView() != null) {
            updateNotificationList(getView());
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0;
    }

    private boolean isNotificationListEmpty() {
        return notificationList == null || notificationList.isEmpty();
    }
}