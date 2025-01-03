package com.example.transitradar;

import static android.content.Intent.getIntent;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.transitradar.model.LocationModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BottomMarkerDialogFragment extends BottomSheetDialogFragment {
    private static final String ARG_LOCATION_MODEL = "location_model";
    private static final String ARG_CURRENT_LOCATION = "current_location";
    private static List<LocationModel> notificationList = new ArrayList<>();
    private LocationModel locationModel;
    private Location currentLocation;

    public static BottomMarkerDialogFragment newInstance(LocationModel locationModel, Location currentLocation) {
        BottomMarkerDialogFragment fragment = new BottomMarkerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LOCATION_MODEL, (Serializable) locationModel);
        args.putParcelable(ARG_CURRENT_LOCATION, currentLocation);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            locationModel = (LocationModel) getArguments().getSerializable(ARG_LOCATION_MODEL);
            currentLocation = getArguments().getParcelable(ARG_CURRENT_LOCATION);
        }
    }

    //Displaying Train Information
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_marker_layout, container, false);
        TextView RegTextView = view.findViewById(R.id.mReg);
        TextView tripIdTextView = view.findViewById(R.id.mTrip);
        TextView SpeedTextView = view.findViewById(R.id.mSpeed);
        TextView RouteTextView = view.findViewById(R.id.mRoute);
        TextView etaTextView = view.findViewById(R.id.mEta);
        Button notifyMeButton = view.findViewById(R.id.setNotificationButton);
        if (locationModel != null && currentLocation != null) {
            Pattern pattern = Pattern.compile("(\\d{2})(\\d{2})");
            Matcher matcher = pattern.matcher(locationModel.getTripId());
            RegTextView.setText(locationModel.getLabel());
            SpeedTextView.setText((int) locationModel.getSpeed() + " km/h");
            double distance = calculateDistance(currentLocation.getLatitude(), currentLocation.getLongitude(),
                    locationModel.getLatitude(), locationModel.getLongitude());
            double eta = (distance / 43.33) * 60; // ETA in minutes
            if (matcher.find()) {
                tripIdTextView.setText(matcher.group());
                int firstTwoNumbersInt = Integer.parseInt(matcher.group(1));
                int nextTwoNumbersInt = Integer.parseInt(matcher.group(2));
                String routeText;
                if (firstTwoNumbersInt % 2 == 0) {
                    if (nextTwoNumbersInt % 2 == 0) {
                        routeText = "P. Sebang - Batu Caves";
                    } else {
                        routeText = "Batu Caves - P. Sebang";
                    }
                } else {
                    if (nextTwoNumbersInt % 2 == 0) {
                        routeText = "Port Klang - Tg Malim";
                    } else {
                        routeText = "Tg Malim - Port Klang";
                    }
                }
                RouteTextView.setText(routeText);
            }
            etaTextView.setText((int) eta + " minutes");
        }

        //function for notify me button
        notifyMeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (locationModel != null) {
                    boolean tripIdExists = false;
                    for (LocationModel model : notificationList) {
                        if (model.getTripId().equals(locationModel.getTripId())) {
                            tripIdExists = true;
                            break;
                        }
                    }
                    if (!tripIdExists) {
                        notificationList.add(locationModel);
                        Holder.setNotificationList(notificationList);
                        Toast.makeText(getContext(), "Added to notification list", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Already in notification list", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                    List<LocationModel> currentNotificationList = Holder.getNotificationList();
                    BottomNotificationDialogFragment notificationFragment = BottomNotificationDialogFragment.newInstance(currentNotificationList);
                    notificationFragment.show(getParentFragmentManager(), notificationFragment.getTag());
                }
            }
        });
        return view;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // convert meters to kilometers
    }
}