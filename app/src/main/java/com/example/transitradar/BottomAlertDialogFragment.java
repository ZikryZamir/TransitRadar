package com.example.transitradar;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.transitradar.model.LocationModel;
import com.example.transitradar.model.LocationModel2;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class BottomAlertDialogFragment extends BottomSheetDialogFragment {
    private static final String ARG_LOCATION_LIST = "arg_location_list";
    private List<LocationModel2> locationList;
    private List<LocationModel> locationModels = Holder.getLocationModels();
    int only = 0;

    public static BottomAlertDialogFragment newInstance(List<LocationModel2> locationList) {
        BottomAlertDialogFragment fragment = new BottomAlertDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LOCATION_LIST, (Serializable) locationList);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            locationList = (List<LocationModel2>) getArguments().getSerializable(ARG_LOCATION_LIST);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_alert_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout lineContainer = view.findViewById(R.id.line_container);

//        addKtm(lineContainer);
//        addKtm1(lineContainer);

        if (locationList != null && !locationList.isEmpty()) {
            for (LocationModel2 location : locationList) {
                addLineToContainer(lineContainer, location);
            }
        }
    }

//    private void addKtm1(LinearLayout container) {
//        View lineView = getLayoutInflater().inflate(R.layout.line_item_layout, container, false);
//
//        TextView lineNameTextView = lineView.findViewById(R.id.line_name);
//        TextView lineStatusTextView = lineView.findViewById(R.id.line_status);
//        TextView lineRemarkTextView = lineView.findViewById(R.id.line_remark);
//        View light = lineView.findViewById(R.id.light);
//
//        lineNameTextView.setText("KTM Batu Caves - P. Sebang Line");
//        if (locationModels != null && !locationModels.isEmpty()) {
//            lineStatusTextView.setText("Status: Normal Service");
//        } else {
//            lineStatusTextView.setText("Status: Down");
//            lineRemarkTextView.setText("Remark: API returned 0 data");
//            light.setBackgroundResource(R.drawable.circle_red);
//        }
//        container.addView(lineView);
//    }
//
//    private void addKtm(LinearLayout container) {
//        View lineView = getLayoutInflater().inflate(R.layout.line_item_layout, container, false);
//
//        TextView lineNameTextView = lineView.findViewById(R.id.line_name);
//        TextView lineStatusTextView = lineView.findViewById(R.id.line_status);
//        TextView lineRemarkTextView = lineView.findViewById(R.id.line_remark);
//        View light = lineView.findViewById(R.id.light);
//
//        lineNameTextView.setText("KTM Tg Malim - Port Klang Line");
//        if (locationModels != null && !locationModels.isEmpty()) {
//            lineStatusTextView.setText("Status: Normal Service");
//        } else {
//            lineStatusTextView.setText("Status: Down");
//            lineRemarkTextView.setText("Remark: API returned 0 data");
//            light.setBackgroundResource(R.drawable.circle_red);
//        }
//        container.addView(lineView);
//    }

    //display the service status for each line
    private void addLineToContainer(LinearLayout container, LocationModel2 location) {
        View lineView = getLayoutInflater().inflate(R.layout.line_item_layout, container, false);
        TextView lineNameTextView = lineView.findViewById(R.id.line_name);
        TextView lineStatusTextView = lineView.findViewById(R.id.line_status);
        TextView lineRemarkTextView = lineView.findViewById(R.id.line_remark);
        View light = lineView.findViewById(R.id.light);
        if (!Objects.equals(location.getLineId(), "ERL")) {
            lineNameTextView.setText(location.getLine());
            lineStatusTextView.setText("Status: " + location.getStatus());
            if (!Objects.equals(location.getRemark(), "")) {
                lineRemarkTextView.setText("Remark: " + location.getRemark());
                light.setBackgroundResource(R.drawable.circle_yellow);
            }

            if ("KA_Komuter".equals(location.getLineId())) {
                // Add to the top of the container
                container.addView(lineView, 1);
            } else if ("KC_Komuter".equals(location.getLineId())) {
                // Add to the top of the container
                container.addView(lineView, 2);
            } else {
                // Add to the bottom of the container
                container.addView(lineView);
            }
        }
    }
}

