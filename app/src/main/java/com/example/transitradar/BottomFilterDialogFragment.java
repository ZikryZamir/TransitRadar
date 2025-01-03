package com.example.transitradar;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomFilterDialogFragment extends BottomSheetDialogFragment {

    private RadioGroup radioGroup;
    private Button buttonClearFilters, buttonApplyFilters;
    private FilterViewModel filterViewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_filter_layout, container, false);

        filterViewModel = new ViewModelProvider(requireActivity()).get(FilterViewModel.class);

        radioGroup = view.findViewById(R.id.radioGroup);
        buttonClearFilters = view.findViewById(R.id.buttonClearFilters);
        buttonApplyFilters = view.findViewById(R.id.buttonApplyFilters);

        filterViewModel.getSelectedFilter().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String filter) {
                if (filter != null) {
                    switch (filter) {
                        case "to Port Klang":
                            radioGroup.check(R.id.radioButtonLine1Northbound);
                            break;
                        case "to Tg Malim":
                            radioGroup.check(R.id.radioButtonLine1Southbound);
                            break;
                        case "to P. Sebang":
                            radioGroup.check(R.id.radioButtonLine2Northbound);
                            break;
                        case "to Batu Caves":
                            radioGroup.check(R.id.radioButtonLine2Southbound);
                            break;
                    }
                } else {
                    radioGroup.clearCheck();
                }
            }
        });

        //Clear button functions
        buttonClearFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioGroup.clearCheck();
                filterViewModel.setSelectedFilter(null);

                Maps mapsActivity = (Maps) requireActivity();
                mapsActivity.applyFilter(null);

                dismiss();
            }
        });

        //Filter functions
        buttonApplyFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View rootView = getView();
                // Retrieve selected filters
                RadioButton radioButtonLine1Northbound = rootView.findViewById(R.id.radioButtonLine1Northbound);
                RadioButton radioButtonLine1Southbound = rootView.findViewById(R.id.radioButtonLine1Southbound);
                RadioButton radioButtonLine2Northbound = rootView.findViewById(R.id.radioButtonLine2Northbound);
                RadioButton radioButtonLine2Southbound = rootView.findViewById(R.id.radioButtonLine2Southbound);
                // Determine which filter is selected
                String selectedFilter = "";
                if (radioButtonLine1Northbound.isChecked()) {
                    selectedFilter = "to Port Klang";
                } else if (radioButtonLine1Southbound.isChecked()) {
                    selectedFilter = "to Tg Malim";
                } else if (radioButtonLine2Northbound.isChecked()) {
                    selectedFilter = "to P. Sebang";
                } else if (radioButtonLine2Southbound.isChecked()) {
                    selectedFilter = "to Batu Caves";
                }
                filterViewModel.setSelectedFilter(selectedFilter);
                Maps mapsActivity = (Maps) requireActivity();
                // Apply the filter logic
                mapsActivity.applyFilter(selectedFilter);
                // Dismiss the dialog
                dismiss();
            }
        });
        return view;
    }
}

