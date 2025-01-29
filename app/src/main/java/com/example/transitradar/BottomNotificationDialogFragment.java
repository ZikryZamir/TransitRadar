package com.example.transitradar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.transitradar.model.LocationModel;
import com.example.transitradar.NotificationReceiver;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomNotificationDialogFragment extends BottomSheetDialogFragment {
    private List<LocationModel> notificationList = Holder.getNotificationList();
    private List<LocationModel> locationModels = Holder.getLocationModels();
    private Location currentLocation = Holder.getCurrentLocation();
    private Handler handler = new Handler();
    private Runnable updateTask;

    public static BottomNotificationDialogFragment newInstance(List<LocationModel> notificationList) {
        BottomNotificationDialogFragment fragment = new BottomNotificationDialogFragment();
        //Bundle args = new Bundle();
        //args.putSerializable(ARG_NOTIFICATION_LIST, new ArrayList<>(notificationList));
        //fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        updateTask = new Runnable() {
            @Override
            public void run() {
                checkForUpdates();
                handler.postDelayed(this, 60000); // Check every minute
            }
        };

        // Start the task
        handler.post(updateTask);
        setRepeatingAlarm();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the task when the fragment is destroyed
        handler.removeCallbacks(updateTask);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_notification_layout, container, false);
        updateNotificationList(view);
        return view;
    }

    //Displaying a notification list
    private void updateNotificationList(View view) {
        LinearLayout notificationContainer = view.findViewById(R.id.notification_container);
        notificationContainer.removeAllViews(); // Clear previous notifications
        View headerView = getLayoutInflater().inflate(R.layout.notification_header, notificationContainer, false);
        notificationContainer.addView(headerView);
        //if notificationlist empty stop sending notification if there's any
        if (notificationList != null && !notificationList.isEmpty()) {
            //start sending notification here
            for (LocationModel notification : notificationList) {
                for (LocationModel location : locationModels) {
                    if (notification.getTripId().equals(location.getTripId())) {
                        // Update latitude and longitude if tripId matches
                        notification.setLatitude(location.getLatitude());
                        notification.setLongitude(location.getLongitude());
                        break;
                    }
                }
                double distance = calculateDistance(currentLocation.getLatitude(), currentLocation.getLongitude(),
                        notification.getLatitude(), notification.getLongitude());
                double eta = (distance / 41.13) * 60; // ETA in minutes
                View notificationView = getLayoutInflater().inflate(R.layout.notification_item, notificationContainer, false);
                TextView textTripId = notificationView.findViewById(R.id.text_trip_id);
                TextView textEta = notificationView.findViewById(R.id.text_eta);
                ImageButton removeButton = notificationView.findViewById(R.id.button_remove);
                String tripid = notification.getTripId();
                Pattern pattern = Pattern.compile("(\\d{2})(\\d{2})");
                Matcher matcher = pattern.matcher(tripid);
                if (matcher.find()) {
                    int firstTwoNumbersInt = Integer.parseInt(matcher.group(1));
                    int nextTwoNumbersInt = Integer.parseInt(matcher.group(2));
                    String routeText;
                    if (firstTwoNumbersInt % 2 == 0) {
                        if (nextTwoNumbersInt % 2 == 0) {
                            routeText = " (P. Sebang > Batu Caves)";
                        } else {
                            routeText = " (Batu Caves > P. Sebang)";
                        }
                    } else {
                        if (nextTwoNumbersInt % 2 == 0) {
                            if (nextTwoNumbersInt == 4 ||
                                    nextTwoNumbersInt == 10 ||
                                    nextTwoNumbersInt == 12 ||
                                    nextTwoNumbersInt == 18 ||
                                    nextTwoNumbersInt == 22 ||
                                    nextTwoNumbersInt == 26) {
                                routeText = " (Port Klang > Tg Malim)";
                            } else {
                                routeText = " (KL Sentral > Tg Malim)";
                            }
                        } else {
                            if (nextTwoNumbersInt == 63 ||
                                    nextTwoNumbersInt == 67 ||
                                    nextTwoNumbersInt == 71 ||
                                    nextTwoNumbersInt == 75 ||
                                    nextTwoNumbersInt == 79) {
                                routeText = " (Tg Malim > Port Klang)";
                            } else {
                                routeText = " (Tg Malim > KL Sentral)";
                            }
                        }
                    }
                    textTripId.setText("Trip No.: " + matcher.group() + routeText);
                }
                textEta.setText("ETA: " + (int) eta + " minutes");
                removeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        notificationList.remove(notification);
                        Holder.setNotificationList(notificationList);
                        updateNotificationList(view); // Refresh the list
                    }
                });
                notificationContainer.addView(notificationView);
            }
        } else {
            View noNotificationsView = getLayoutInflater().inflate(R.layout.no_notifications_layout, notificationContainer, false);
            notificationContainer.addView(noNotificationsView);
        }
    }

    private void setRepeatingAlarm() {
        Context context = getContext();
        if (context != null) {
            Intent intent = new Intent(context, NotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 60000, pendingIntent);
                Log.d("BottomNotificationDialogFragment", "Repeating alarm set");
            }
        }
    }

    private void checkForUpdates() {
        // Your logic to check for updates goes here
        // For example, you can fetch new data from a server or local storage

        // If there are updates, refresh the UI
        if (getView() != null) {
            updateNotificationList(getView());
        }
    }
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // convert meters to kilometers
    }
}