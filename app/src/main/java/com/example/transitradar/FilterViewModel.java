package com.example.transitradar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class FilterViewModel extends ViewModel {
    private final MutableLiveData<String> selectedFilter = new MutableLiveData<>();

    public void setSelectedFilter(String filter) {
        selectedFilter.setValue(filter);
    }

    public LiveData<String> getSelectedFilter() {
        return selectedFilter;
    }
}