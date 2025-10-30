package com.example.transitradar;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import org.osmdroid.config.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.transitradar.databinding.ActivityMapsBinding;
import com.example.transitradar.model.ListLocationModel;
import com.example.transitradar.model.ListLocationModel2;
import com.example.transitradar.model.LocationModel;
import com.example.transitradar.model.LocationModel2;
import com.example.transitradar.network.ApiClient;
import com.example.transitradar.network.ApiClient2;
import com.example.transitradar.network.ApiService;
import com.example.transitradar.network.ApiService2;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Maps extends FragmentActivity {
    private static final int FINE_LOCATION_PERMISSION_CODE = 1;
    private static final String TAG = "Maps";
    private static final String NOTIFICATION_CHANNEL_ID = "TRAIN_ETA_CHANNEL";
    private MapView mapView;
    private IMapController mapController;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location currentLocation;
    private Runnable updateRunnable;
    private Runnable filterRunnable;
    private Polyline currentPolyline;
    private List<LocationModel> cleanedData = new ArrayList<>();
    private List<LocationModel2> fetchedLocationData;
    private int initialZoomFlag = 0;
    private TimerManager timerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        setupBinding();
        initializeLocationClient();
        setupBottomNavigation();
        schedulePeriodicNotifications();
        createNotificationChannel();
        timerManager = TimerManager.getInstance();
        getLastLocation();
    }

    private void setupBinding() {
        com.example.transitradar.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    private void initializeLocationClient() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.filter) {
                showFilterDialog();
                return true;
            } else if (id == R.id.status) {
                showStatusDialog();
                return true;
            } else if (id == R.id.notification) {
                showNotificationDialog();
                return true;
            }
            return false;
        });
    }

    private void schedulePeriodicNotifications() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(NotificationWorker.class, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(this).enqueue(request);
    }

    private void configureMapUiSettings() {
        mapView.setMultiTouchControls(true); // Enable pinch zoom and multi-touch
        mapView.setBuiltInZoomControls(false); // Hide default zoom buttons
        mapView.getController().setZoom(14.5); // Optional default zoom
        mapView.setTilesScaledToDpi(true); // Makes map look sharper on HD screens

        // Disable rotation (OsmDroid allows disabling gestures differently)
        mapView.setMapOrientation(0.0f); // Lock to north-up orientation
    }

    private void applyMapStyle() {
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);

        if (isDarkTheme) {
            // Use a dark-themed tile source (CartoDB Dark Matter)
            mapView.setTileSource(new OnlineTileSourceBase(
                    "CartoDB Dark Matter", 0, 19, 256, ".png",
                    new String[]{"https://basemaps.cartocdn.com/dark_all/"},
                    "© OpenStreetMap contributors, © CARTO") {
                @Override
                public String getTileURLString(long pMapTileIndex) {
                    return getBaseUrl()
                            + MapTileIndex.getZoom(pMapTileIndex)
                            + "/" + MapTileIndex.getX(pMapTileIndex)
                            + "/" + MapTileIndex.getY(pMapTileIndex)
                            + mImageFilenameEnding;
                }
            });
        } else {
            // Use the default light map (OpenStreetMap standard)
            mapView.setTileSource(TileSourceFactory.MAPNIK);
        }
    }

    private void setInitialCameraPosition() {
        // Replace LatLng with GeoPoint (used by OsmDroid)
        GeoPoint initialLocation = new GeoPoint(3.039411, 101.615959);
        if (mapController != null) {
            mapController.setCenter(initialLocation);
            mapController.setZoom(10.0);
        }
    }

    private void setupMapListeners() {
        // Remove the old Google Maps style listeners, since OsmDroid uses marker-based callbacks.
        Log.d(TAG, "showBottomSheetDialog about to be called");
        // Instead, we’ll loop through markers and attach listeners as they’re added.
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker) {
                Marker marker = (Marker) overlay;
                Log.d(TAG, "showBottomSheetDialog about to be called");
                // When the train icon is clicked:
                marker.setOnMarkerClickListener((clickedMarker, mapView) -> {
                    Log.d(TAG, "showBottomSheetDialog will be called");
                    showBottomSheetDialog(clickedMarker); // ✅ same behavior
                    HighlightTrainLine(clickedMarker);
                    return true; // prevent default info window
                });
            }
        }

        // Handle map taps (to clear any drawn polyline)
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                clearCurrentPolyline();
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
    }



    private void initializeDataFetching() {
        updateRunnable = () -> {
            getAllDataLocation();
            fetchData();
        };
        timerManager.subscribe(updateRunnable); // Subscribe to timer updates
    }

    private void getLastLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                Holder.setCurrentLocation(currentLocation);
                initializeMap();
            }
        });
    }

    private void initializeMap() {
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        mapController = mapView.getController();
        mapController.setZoom(14.5);

        if (currentLocation != null) {
            GeoPoint startPoint = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            mapController.setCenter(startPoint);
            Marker marker = new Marker(mapView);
            marker.setPosition(startPoint);
            marker.setTitle("You are here");
            mapView.getOverlays().add(marker);
        }
        Log.d(TAG, "setupMapListeners about to be called");
        setupMapListeners();
        initializeDataFetching();
    }

    private void getAllDataLocation() {
        ApiService apiService = ApiClient.getRetrofit();
        apiService.getAllLocation("ktmb").enqueue(new Callback<ListLocationModel>() {
            @Override
            public void onResponse(@NonNull Call<ListLocationModel> call, @NonNull Response<ListLocationModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cleanedData = cleanData(response.body().getmData());
                    Holder.setLocationModels(cleanedData);
                    initMarker(cleanedData);
                } else {
                    showToast("Failed to retrieve location data");
                }
            }

            @Override
            public void onFailure(Call<ListLocationModel> call, Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void fetchData() {
        ApiService2 apiService = ApiClient2.getRetrofit();
        apiService.getAllLocation().enqueue(new Callback<ListLocationModel2>() {
            @Override
            public void onResponse(Call<ListLocationModel2> call, Response<ListLocationModel2> response) {
                if (response.isSuccessful() && response.body() != null) {
                    fetchedLocationData = response.body().getmData();
                    if (fetchedLocationData == null || fetchedLocationData.isEmpty()) {
                        showToast("No data retrieved");
                    }
                } else {
                    showToast("Status API is down, retry in 60 seconds");
                }
            }

            @Override
            public void onFailure(Call<ListLocationModel2> call, Throwable t) {
                showToast("Request failed");
            }
        });
    }

    private List<LocationModel> cleanData(List<LocationModel> rawData) {
        List<LocationModel> filteredData = new ArrayList<>();
        for (LocationModel location : rawData) {
            if (isValidLocation(location)) {
                filteredData.add(location);
            }
        }
        return filteredData;
    }

    private boolean isValidLocation(LocationModel location) {
        return location != null &&
                !location.getTripId().contains("29") &&
                location.getLatitude() != 0.0 &&
                location.getLongitude() != 0.0 &&
                location.getTimestamp() != null &&
                location.getId() != null &&
                !location.getLabel().contains("ETS");
    }

    private void initMarker(List<LocationModel> data) {
        // Clear all overlays (removes existing markers, lines, etc.)
        mapView.getOverlays().clear();
        addUserLocationMarker();

        if (data == null || data.isEmpty()) {
            showToast("No location data");
            return;
        }

        addTrainMarkers(data);
        drawTrainLines();

        // Refresh the map to show new overlays
        mapView.invalidate();
    }

    private void addUserLocationMarker() {
        if (currentLocation == null) return;

        GeoPoint userLocation = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
        Marker userMarker = new Marker(mapView);
        userMarker.setPosition(userLocation);
        userMarker.setTitle("My Location");

        // Set custom icon
        Drawable icon = ContextCompat.getDrawable(this, R.drawable.self30);
        userMarker.setIcon(icon);

        mapView.getOverlays().add(userMarker);

        // Set initial camera zoom if not already done
        if (initialZoomFlag == 0 && mapController != null) {
            mapController.setCenter(userLocation);
            mapController.setZoom(11.0);
            initialZoomFlag++;
        }
    }

    private void addTrainMarkers(List<LocationModel> data) {
        mapView.getOverlays().clear(); // optional: clear old markers
        Log.d(TAG, "addTrainMarkers is called");
        for (LocationModel model : data) {
            GeoPoint position = new GeoPoint(model.getLatitude(), model.getLongitude());

            Marker marker = new Marker(mapView);
            marker.setPosition(position);
            marker.setTitle(model.getLabel());

            // 👇 Choose which icon to use
            if (model.getLabel().startsWith("S")) {
                Drawable trainIcon = createScaledIcon(R.drawable.scs);
                marker.setIcon(trainIcon);
            } else {
                Drawable trainIcon = createScaledIcon(R.drawable.emu);
                marker.setIcon(trainIcon);
            }

            marker.setRelatedObject(model);

            marker.setOnMarkerClickListener((m, mapView) -> {
                showBottomSheetDialog(m);
                return true; // consume the click (no default popup)
            });

            mapView.getOverlays().add(marker);
        }
        mapView.invalidate(); // refresh map to show markers
    }

    private Drawable createScaledIcon(int resourceId) {
        Bitmap original = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap scaled = Bitmap.createScaledBitmap(original, 68, 95, false);
        return new BitmapDrawable(getResources(), scaled);
    }

    private void HighlightTrainLine(Marker marker) {
        LocationModel info = (LocationModel) marker.getRelatedObject(); // OsmDroid version of setTag/getTag
        if (info == null) return;

        clearCurrentPolyline();

        List<GeoPoint> route = getTrainRoute(info); // Make sure this returns GeoPoints, not LatLngs

        // Extract line number from tripId
        Pattern pattern = Pattern.compile("(\\d{2})(\\d{2})");
        Matcher matcher = pattern.matcher(info.getTripId());

        if (matcher.find()) {
            int lineNumber = Integer.parseInt(matcher.group(1));
            String colorHex = (lineNumber == 21 || lineNumber == 23) ? "#FF0000" : "#0000FF";

            Polyline polyline = new Polyline(mapView);
            polyline.setPoints(route);
            polyline.setWidth(10f);
            polyline.setColor(Color.parseColor(colorHex));
            polyline.setOnClickListener((polyline1, mapView1, eventPos) -> {
                // Optional: handle clicks on train line
                return true;
            });

            mapView.getOverlays().add(polyline);
            currentPolyline = polyline;
        }

        mapView.invalidate();
    }

    private void drawTrainLines() {
        // Draw Line 1 (Red)
        List<GeoPoint> line1Route = parseLineString(getLine1String());
        Polyline line1 = new Polyline();
        line1.setPoints(line1Route);
        line1.getOutlinePaint().setColor(Color.parseColor("#EC6A5C")); // Red
        line1.getOutlinePaint().setStrokeWidth(5f);
        line1.setTitle("Line 1");

        // Draw Line 2 (Blue)
        List<GeoPoint> line2Route = parseLineString(getLine2String());
        Polyline line2 = new Polyline();
        line2.setPoints(line2Route);
        line2.getOutlinePaint().setColor(Color.parseColor("#4A90E2")); // Blue
        line2.getOutlinePaint().setStrokeWidth(5f);
        line2.setTitle("Line 2");

        // Add both lines to the map
        mapView.getOverlays().add(line1);
        mapView.getOverlays().add(line2);

        // Refresh map
        mapView.invalidate();
    }


    private List<GeoPoint> parseLineString(String lineString) {
        List<GeoPoint> route = new ArrayList<>();
        String cleanedLineString = lineString
                .replace("LINESTRING (", "")
                .replace(")", "")
                .trim();

        String[] points = cleanedLineString.split(",\\s*");
        for (String point : points) {
            String[] latLng = point.trim().split(" ");
            // WKT format: "longitude latitude"
            double lon = Double.parseDouble(latLng[0]);
            double lat = Double.parseDouble(latLng[1]);
            route.add(new GeoPoint(lat, lon));
        }
        return route;
    }


    private List<GeoPoint> getTrainRoute(LocationModel info) {
        List<GeoPoint> route = new ArrayList<>();
        Pattern pattern = Pattern.compile("(\\d{2})(\\d{2})");
        Matcher matcher = pattern.matcher(info.getTripId());

        if (matcher.find()) {
            int lineNumber = Integer.parseInt(matcher.group(1));
            String lineString = (lineNumber == 21 || lineNumber == 23) ? getLine1String() : getLine2String();
            String[] points = lineString.replace("LINESTRING (", "").replace(")", "").split(", ");
            for (String point : points) {
                String[] latLng = point.split(" ");
                route.add(new GeoPoint(Double.parseDouble(latLng[1]), Double.parseDouble(latLng[0])));
            }
        }
        return route;
    }

    private String getLine1String() {
        return "LINESTRING (101.3913367 2.9997035, 101.3909559 3.0003677, 101.3908519 3.0006517, 101.3908057 3.0009463, 101.3907991 3.0012653, 101.3908448 3.0015816, 101.3909607 3.0019055, 101.3911155 3.0022186, 101.3912996 3.0025109, 101.3914919 3.0027777, 101.3917388 3.003066, 101.3920207 3.0033396, 101.3923127 3.0036199, 101.3926141 3.0039003, 101.3929788 3.0042357, 101.3933678 3.0045418, 101.399035 3.009919, 101.407214 3.017524, 101.418507 3.028099, 101.4321462 3.0408277, 101.43672 3.0450491, 101.437712 3.045786, 101.438634 3.046264, 101.4394183 3.0465543, 101.4401532 3.046699, 101.4410222 3.0468222, 101.4417518 3.0468275, 101.442594 3.0467311, 101.4433236 3.0465597, 101.4439726 3.0463722, 101.4446057 3.0461204, 101.4451904 3.0457508, 101.4456893 3.0454348, 101.4461345 3.0450973, 101.446671 3.0446687, 101.447044 3.044372, 101.4475936 3.0440795, 101.4481676 3.0438384, 101.4489884 3.0435438, 101.449675 3.0432599, 101.4503617 3.042976, 101.45122 3.0425635, 101.4518852 3.0419796, 101.4532263 3.0401368, 101.4537091 3.0394244, 101.4547927 3.0378441, 101.455363 3.037127, 101.4557315 3.036687, 101.4561821 3.0362906, 101.4566702 3.0358888, 101.4571798 3.0355888, 101.458012 3.035065, 101.4589233 3.0347692, 101.4598406 3.0345657, 101.4616591 3.0342121, 101.4633382 3.0339068, 101.4654571 3.0334782, 101.4660526 3.0334836, 101.4668036 3.0335264, 101.4674044 3.0337086, 101.4681715 3.0339871, 101.4688796 3.0344157, 101.4694751 3.0349299, 101.469948 3.035518, 101.4704031 3.0362799, 101.4717121 3.0387869, 101.4728708 3.0409779, 101.4738364 3.0429438, 101.4740939 3.0435438, 101.4742226 3.044208, 101.474247 3.044727, 101.4741743 3.0454723, 101.47389 3.0464686, 101.4736701 3.0471811, 101.4735413 3.0476578, 101.4735842 3.0483596, 101.473789 3.048916, 101.4740617 3.0493881, 101.474427 3.049726, 101.4750273 3.0501434, 101.4773769 3.0516755, 101.4780152 3.0519969, 101.4786322 3.0522219, 101.479358 3.052436, 101.480102 3.0526236, 101.4808423 3.0526611, 101.4814109 3.0526665, 101.4833314 3.0526129, 101.4871133 3.052479, 101.4881862 3.0524576, 101.491647 3.05238, 101.4934594 3.0523076, 101.4968014 3.0521844, 101.4985771 3.0521361, 101.4999825 3.0519594, 101.502831 3.051579, 101.5042419 3.0514719, 101.5056849 3.051429, 101.5073532 3.0514719, 101.5088231 3.0516808, 101.5102393 3.051954, 101.5116126 3.0522701, 101.5129537 3.0526825, 101.5159041 3.0536146, 101.5184522 3.0544021, 101.5233499 3.0559341, 101.5278721 3.0573162, 101.5331776 3.0590678, 101.5381665 3.0605892, 101.539647 3.0609695, 101.540933 3.06118, 101.54212 3.0613605, 101.5428603 3.0615266, 101.54337 3.0617087, 101.544003 3.0620515, 101.544948 3.062787, 101.5461916 3.0637604, 101.5475274 3.0647621, 101.548054 3.065296, 101.548554 3.065788, 101.5490509 3.0665834, 101.5503812 3.0690528, 101.5510411 3.0703009, 101.5516365 3.0711687, 101.5523554 3.071849, 101.5532298 3.0725615, 101.5540076 3.0730757, 101.5564967 3.0743131, 101.5592915 3.0757005, 101.560612 3.076332, 101.561604 3.076633, 101.5625638 3.0767611, 101.5635026 3.0767343, 101.564597 3.0765951, 101.5674079 3.0761397, 101.5685452 3.0760272, 101.569323 3.0761504, 101.5700794 3.0764558, 101.570735 3.076919, 101.571186 3.077457, 101.5716619 3.0782663, 101.5720213 3.0791877, 101.5724558 3.0800287, 101.5729494 3.080559, 101.5735609 3.0809286, 101.574276 3.081141, 101.5751219 3.0812661, 101.5784801 3.0816518, 101.5803522 3.0818874, 101.5811676 3.0820214, 101.5818972 3.082241, 101.582487 3.082515, 101.583514 3.083093, 101.5854699 3.0843569, 101.586225 3.084596, 101.5869827 3.0847318, 101.5878034 3.0847104, 101.5903569 3.0845122, 101.5918857 3.0843836, 101.5928889 3.0842497, 101.5937257 3.0840783, 101.5951151 3.0837623, 101.5989829 3.0829373, 101.5998412 3.0827927, 101.6008068 3.0827284, 101.6037518 3.0827659, 101.608612 3.0828624, 101.6100014 3.0829266, 101.611449 3.083159, 101.614022 3.083526, 101.6159988 3.0838266, 101.6170556 3.0839819, 101.6191155 3.0842069, 101.6207249 3.0843729, 101.6246087 3.0848229, 101.6266204 3.0850318, 101.627434 3.085025, 101.6285462 3.085005, 101.629501 3.0848979, 101.6331167 3.0843247, 101.6361315 3.0838319, 101.6378374 3.0835962, 101.6403586 3.0831891, 101.6436846 3.0826695, 101.64779 3.08203, 101.6492153 3.0818874, 101.6507334 3.0819249, 101.6520906 3.082091, 101.6532493 3.0823535, 101.6549069 3.0828516, 101.6575248 3.0837998, 101.6615266 3.0852407, 101.6647345 3.0863763, 101.6681946 3.0876405, 101.6687471 3.087903, 101.6692353 3.0882833, 101.669467 3.088619, 101.6696966 3.089151, 101.6697717 3.0896492, 101.6697181 3.0901688, 101.6695947 3.090817, 101.6694177 3.0916151, 101.6693801 3.0921936, 101.6694123 3.0926918, 101.6695035 3.0933292, 101.6696537 3.0939506, 101.6701633 3.0962968, 101.6705603 3.0978234, 101.671086 3.0994839, 101.6715313 3.1010159, 101.6723091 3.103721, 101.6727383 3.1051297, 101.6731781 3.1061153, 101.6736717 3.1069885, 101.6741276 3.1077812, 101.6743529 3.1082794, 101.6745085 3.108965, 101.67453 3.109484, 101.6744817 3.110122, 101.6742671 3.1108934, 101.6738541 3.1118897, 101.6730548 3.1139091, 101.6725559 3.1151678, 101.672388 3.115891, 101.6723842 3.1164641, 101.672524 3.117229, 101.6728509 3.1182103, 101.6737843 3.1199137, 101.6743261 3.1207279, 101.6749484 3.121376, 101.676452 3.122803, 101.6770995 3.1234007, 101.6776038 3.1239525, 101.6779149 3.1244667, 101.6787196 3.1263736, 101.6791702 3.1271181, 101.6797388 3.1278948, 101.6816968 3.130407, 101.6838211 3.1329673, 101.6854519 3.1339797, 101.68719 3.1348849, 101.6897649 3.1362883, 101.6914386 3.137006, 101.692009 3.137286, 101.6930158 3.1380505, 101.6934503 3.1386986, 101.6939974 3.1404395, 101.6941047 3.1409537, 101.694174 3.141582, 101.6941101 3.1423088, 101.693869 3.143234, 101.6932518 3.1440871, 101.6921467 3.1453887, 101.6913957 3.1463047, 101.6911597 3.1467064, 101.6909987 3.1472206, 101.6906822 3.14909, 101.6905964 3.1498506, 101.6906232 3.1504076, 101.690811 3.1511575, 101.6911114 3.1518806, 101.691504 3.152486, 101.6920555 3.1532143, 101.6929728 3.1545105, 101.69342 3.155124, 101.6936541 3.1556461, 101.6937614 3.1560746, 101.6937936 3.1565459, 101.6937078 3.1572208, 101.693297 3.158272, 101.6923184 3.1607827, 101.692136 3.1615273, 101.6920502 3.1621861, 101.6920126 3.1630324, 101.6919482 3.1636269, 101.6917176 3.1643768, 101.6914279 3.1648749, 101.6909344 3.1654534, 101.6901136 3.1662086, 101.6892714 3.1669049, 101.6886062 3.1673227, 101.6879035 3.1675959, 101.6873724 3.1678155, 101.6868574 3.1681743, 101.6863746 3.1685439, 101.6859776 3.1690099, 101.6856665 3.1695723, 101.685112 3.170801, 101.6845722 3.1721004, 101.684124 3.172755, 101.6836334 3.1733591, 101.6819919 3.1749553, 101.6809834 3.1760051, 101.6795672 3.1774138, 101.6787196 3.1781315, 101.6778076 3.1788171, 101.6748357 3.1808631, 101.6736717 3.1816719, 101.6729475 3.1820361, 101.6719926 3.1824218, 101.67063 3.182786, 101.6692299 3.1831716, 101.668436 3.1833752, 101.6677279 3.1837126, 101.667213 3.183979, 101.6665584 3.1844785, 101.665477 3.185466, 101.6646273 3.1861229, 101.662163 3.18792, 101.6594881 3.1898347, 101.6581792 3.1906488, 101.6570688 3.1911362, 101.653201 3.1928394, 101.6489041 3.1947462, 101.6442854 3.1967387, 101.6425098 3.1975474, 101.641866 3.1979598, 101.6413779 3.1984151, 101.6404391 3.1994113, 101.6379661 3.2020037, 101.6364533 3.2035783, 101.6339213 3.2061599, 101.6331435 3.206958, 101.6323335 3.207515, 101.631653 3.207907, 101.6310084 3.2081524, 101.630236 3.2083559, 101.6293777 3.2085059, 101.6285945 3.2086398, 101.6277683 3.2087041, 101.6262609 3.2086237, 101.6213579 3.2083452, 101.619736 3.208254, 101.6190136 3.2081845, 101.618522 3.208085, 101.6179622 3.2078578, 101.6175706 3.2076168, 101.617166 3.207332, 101.6168196 3.207049, 101.6164977 3.2067277, 101.6141588 3.2042961, 101.611423 3.2014895, 101.6106988 3.2008682, 101.6099156 3.2003808, 101.6090573 3.1999684, 101.6081346 3.1996899, 101.6073889 3.1995506, 101.6064555 3.1994863, 101.6058386 3.1995292, 101.6052271 3.1996095, 101.6046209 3.1997488, 101.6040147 3.1999309, 101.6033281 3.2001933, 101.6025556 3.2006111, 101.6019869 3.2008253, 101.6013325 3.200986, 101.600619 3.2010931, 101.599943 3.201115, 101.5990526 3.2009753, 101.5964509 3.2005147, 101.5959037 3.2003969, 101.5953029 3.2003969, 101.5945733 3.2004665, 101.5937955 3.20067, 101.5892465 3.202009, 101.587444 3.2025446, 101.5865106 3.2029517, 101.5855289 3.2035194, 101.5832759 3.2048852, 101.582354 3.205435, 101.5815592 3.2057636, 101.580048 3.206243, 101.5789951 3.2065402, 101.5775091 3.2069741, 101.5767742 3.2073811, 101.5763021 3.2077453, 101.575814 3.2082863, 101.575273 3.209145, 101.574902 3.2095717, 101.573298 3.2112749, 101.5708787 3.2139101, 101.5686954 3.2162881, 101.5681106 3.2169791, 101.566555 3.2189822, 101.5651173 3.2208568, 101.563288 3.2232509, 101.5625156 3.2242364, 101.5618826 3.2251897, 101.5614641 3.2259342, 101.56105 3.22684, 101.5607668 3.2275838, 101.5603644 3.2287889, 101.5597475 3.2308884, 101.5595598 3.2317025, 101.5594525 3.2325487, 101.5594525 3.2332503, 101.5595866 3.2343965, 101.559769 3.2357569, 101.5600694 3.2377439, 101.560225 3.238783, 101.5603322 3.2396292, 101.5603752 3.2403361, 101.5604717 3.2417662, 101.5605307 3.2429069, 101.5605039 3.2436461, 101.5603322 3.2443423, 101.5600318 3.2448993, 101.559549 3.2456009, 101.55811 3.247418, 101.5562821 3.2500355, 101.5557564 3.2509728, 101.5553862 3.2517762, 101.5549249 3.2530562, 101.5547157 3.2540042, 101.5545709 3.2552253, 101.5545709 3.2562429, 101.55468 3.257516, 101.5548659 3.2583155, 101.5552307 3.2595152, 101.5556545 3.2605435, 101.5561909 3.2615665, 101.5568025 3.2625198, 101.5582401 3.2647585, 101.5585352 3.2653583, 101.558975 3.2661992, 101.5592755 3.2673239, 101.5597797 3.2697232, 101.5600587 3.2709765, 101.5603269 3.2715656, 101.5607024 3.2720744, 101.5611798 3.2726367, 101.5616143 3.2732794, 101.561839 3.273922, 101.5619469 3.2746504, 101.5619738 3.2757591, 101.5621079 3.2764285, 101.562412 3.277106, 101.562981 3.27786, 101.5634221 3.2782387, 101.564259 3.2788171, 101.564755 3.279298, 101.5651602 3.279824, 101.5655786 3.2805095, 101.5664423 3.281977, 101.5669144 3.2827428, 101.5673865 3.283273, 101.56788 3.2836693, 101.5683628 3.2841246, 101.56876 3.284759, 101.5690709 3.2853617, 101.570015 3.2870326, 101.5705515 3.2878521, 101.5710074 3.2883769, 101.572091 3.2896997, 101.572586 3.290472, 101.5735341 3.2920883, 101.5738452 3.2926399, 101.5742905 3.2936896, 101.5746445 3.2949375, 101.5748698 3.2957354, 101.5750254 3.2964692, 101.5750844 3.2970797, 101.57502 3.2978562, 101.5748752 3.2986274, 101.5747893 3.299329, 101.5747357 3.3000467, 101.5748215 3.3010749, 101.574961 3.3022317, 101.575039 3.303264, 101.5750147 3.3044703, 101.5749717 3.3072819, 101.5749503 3.3097347, 101.5749074 3.3126481, 101.5748752 3.3152455, 101.5748269 3.3178161, 101.5747893 3.3194174, 101.5746767 3.3199904, 101.5744514 3.3205313, 101.574033 3.3212436, 101.5729601 3.3227003, 101.5720857 3.3239213, 101.570235 3.3264276, 101.5696932 3.3271506, 101.569264 3.3279325, 101.569063 3.32863, 101.5689421 3.3292606, 101.5689421 3.3297051, 101.569028 3.330337, 101.5692533 3.3311296, 101.5697253 3.3319651, 101.5703315 3.3327041, 101.5709913 3.3334806, 101.5714473 3.3340429, 101.571943 3.334835, 101.572286 3.335623, 101.5725041 3.3365332, 101.5726865 3.3373418, 101.5727294 3.3383647, 101.5726168 3.3401426, 101.5724505 3.3430613, 101.5723217 3.3464458, 101.572134 3.3496268, 101.572007 3.351736, 101.571943 3.352537, 101.5716565 3.3545697, 101.571442 3.3560263, 101.5713883 3.3568189, 101.571418 3.357485, 101.5716673 3.3586343, 101.571979 3.359425, 101.5722734 3.3600374, 101.5726972 3.3606747, 101.5731747 3.361253, 101.5736896 3.3617403, 101.5743924 3.3622705, 101.575138 3.3626721, 101.5759427 3.3629774, 101.5766025 3.3631488, 101.5774233 3.3632559, 101.5780992 3.3632612, 101.5787805 3.363213, 101.5796549 3.3630417, 101.58135 3.3627793, 101.582616 3.3625758, 101.5839357 3.362624, 101.586237 3.3630202, 101.5893645 3.3635397, 101.5926904 3.3640913, 101.5939081 3.3644126, 101.5964884 3.3652641, 101.5998251 3.3663458, 101.6041005 3.3677435, 101.6054094 3.3682201, 101.6064019 3.3686967, 101.6074587 3.3693393, 101.6082526 3.3699391, 101.6091967 3.3708066, 101.6099907 3.371626, 101.6112728 3.3728951, 101.6124905 3.3741215, 101.6140408 3.3756744, 101.6165782 3.3782074, 101.6191263 3.3807511, 101.6211433 3.3827485, 101.6236538 3.3852815, 101.6248501 3.3865078, 101.6255421 3.3873485, 101.6262824 3.3884356, 101.6267545 3.3892549, 101.6271192 3.3901224, 101.6274787 3.3910756, 101.6276932 3.3918521, 101.627927 3.392873, 101.6281009 3.3938013, 101.6282082 3.3944814, 101.628302 3.395441, 101.6282994 3.3961897, 101.6282458 3.397191, 101.6281224 3.3983477, 101.6280366 3.3990599, 101.6279025 3.4005379, 101.6277898 3.4016732, 101.627798 3.40294, 101.6278488 3.4040186, 101.6280044 3.4052021, 101.628048 3.406, 101.6280527 3.4067764, 101.6279829 3.4075904, 101.6277898 3.4088863, 101.6274679 3.4108569, 101.6271032 3.4130149, 101.6268564 3.4142037, 101.6264916 3.4153014, 101.6259766 3.4163885, 101.6253061 3.4177219, 101.6249681 3.4186375, 101.6247482 3.4196282, 101.6247053 3.4202708, 101.6247106 3.420999, 101.6248769 3.4220807, 101.6254026 3.4234248, 101.6258747 3.4241637, 101.6266794 3.4252294, 101.627822 3.4266645, 101.6296459 3.4289617, 101.6313464 3.4311304, 101.6336638 3.4340273, 101.6353912 3.4362495, 101.635908 3.436904, 101.6364104 3.4376418, 101.6368449 3.4384557, 101.637258 3.4393874, 101.637589 3.440363, 101.6378856 3.4414758, 101.6380358 3.4423647, 101.6384596 3.4446404, 101.6389478 3.447366, 101.639495 3.4503057, 101.6397203 3.4516176, 101.6398115 3.4525226, 101.639849 3.4534489, 101.6398222 3.4541825, 101.6397417 3.4549322, 101.639562 3.455979, 101.6393233 3.456817, 101.6386957 3.4588678, 101.6378856 3.4615452, 101.6375906 3.462766, 101.6373975 3.4639869, 101.6373438 3.4650739, 101.6374028 3.4662197, 101.6376067 3.4674567, 101.6379607 3.4696306, 101.6384382 3.4723668, 101.6388781 3.4748674, 101.6392965 3.4772823, 101.6395432 3.4787655, 101.6396666 3.4797079, 101.6396881 3.4806503, 101.6396666 3.4815659, 101.639554 3.4824922, 101.6393394 3.4834507, 101.6390551 3.4843449, 101.6387493 3.4851267, 101.638288 3.4861708, 101.6371936 3.4881091, 101.6364265 3.4895869, 101.6359652 3.490524, 101.6356379 3.4914075, 101.635356 3.492514, 101.6352088 3.4932601, 101.6350747 3.4940686, 101.6347904 3.4960498, 101.634388 3.4986895, 101.634071 3.50104, 101.6336209 3.5038565, 101.6332347 3.5063891, 101.6328002 3.5093715, 101.632623 3.510447, 101.6323496 3.5113366, 101.6318775 3.5123699, 101.6312338 3.5132534, 101.6304184 3.5141262, 101.629826 3.514856, 101.6293991 3.5155451, 101.6289968 3.516541, 101.6287232 3.5176814, 101.6286803 3.5185114, 101.6287017 3.5193573, 101.6288251 3.5205032, 101.6289431 3.521392, 101.6291363 3.5223504, 101.6293508 3.5239942, 101.6298175 3.5270193, 101.6301072 3.5281972, 101.6305417 3.5290968, 101.6311265 3.5300712, 101.6318453 3.5310082, 101.632591 3.5320362, 101.6331328 3.5328822, 101.6335673 3.5336479, 101.6342056 3.5353719, 101.6345221 3.5363946, 101.6348601 3.5376421, 101.6353858 3.5396231, 101.6357935 3.5408814, 101.6361744 3.5423538, 101.6363997 3.5431408, 101.6367537 3.5439225, 101.6371507 3.5445758, 101.6378588 3.5455823, 101.6385991 3.5465782, 101.6390712 3.5472903, 101.6394467 3.5481737, 101.639669 3.549072, 101.6397417 3.5497693, 101.6397203 3.550733, 101.6395969 3.5517342, 101.6393823 3.55356, 101.6391677 3.5550324, 101.6389639 3.5558997, 101.6386045 3.5566814, 101.6381431 3.5575274, 101.6374994 3.5582984, 101.6368396 3.5588605, 101.6359383 3.5594709, 101.6347957 3.5599902, 101.6330952 3.5606702, 101.629324 3.5622015, 101.626497 3.5633205, 101.6252149 3.5639683, 101.6237128 3.5649106, 101.6222859 3.5659975, 101.6202689 3.567427, 101.6192926 3.5681177, 101.6184021 3.5688672, 101.6175599 3.569756, 101.6164601 3.5709339, 101.6154838 3.5720208, 101.614824 3.5727864, 101.6144056 3.5734342, 101.6140247 3.5741195, 101.6137672 3.5749066, 101.6135258 3.5758328, 101.61344 3.5764806, 101.6134185 3.5779369, 101.6133327 3.5795377, 101.6131986 3.5805603, 101.6130162 3.5813634, 101.6124958 3.5828143, 101.6120184 3.5838423, 101.6112406 3.5851861, 101.6104735 3.5862355, 101.6088159 3.588618, 101.6074157 3.5906685, 101.605844 3.5929493, 101.6044063 3.594957, 101.6031135 3.5968469, 101.6024215 3.5976125, 101.6014827 3.5982978, 101.600304 3.598899, 101.5991438 3.5992936, 101.5972716 3.5995131, 101.5961451 3.5996898, 101.594952 3.599955, 101.5937257 3.6004447, 101.5924651 3.6011996, 101.5915907 3.6019116, 101.5907056 3.6028432, 101.590196 3.6035285, 101.5894718 3.6047384, 101.5888978 3.6057771, 101.5882058 3.6070192, 101.5876532 3.6078543, 101.5868486 3.6088609, 101.5860278 3.6096907, 101.5849871 3.6105741, 101.5834207 3.6120303, 101.5817631 3.6135026, 101.5793706 3.6157351, 101.5775788 3.6173037, 101.5766079 3.6179248, 101.5746928 3.619033, 101.5736253 3.6197664, 101.5727509 3.6204678, 101.5719891 3.6212601, 101.5708411 3.6226414, 101.5695483 3.6241779, 101.5685934 3.6252593, 101.5665442 3.6274865, 101.5654016 3.6287874, 101.5641839 3.6302918, 101.5628535 3.6319354, 101.5622688 3.6326688, 101.5616197 3.633611, 101.5610725 3.6344569, 101.5605683 3.6354206, 101.5599245 3.636759, 101.5591682 3.6382312, 101.558562 3.6392859, 101.5574086 3.6408759, 101.55581 3.64283, 101.5548927 3.643981, 101.5534604 3.6457316, 101.552806 3.6464757, 101.5520174 3.6473002, 101.5503705 3.6486386, 101.5470338 3.6513635, 101.5446467 3.6533122, 101.5424204 3.6553894, 101.5403712 3.6573648, 101.5395076 3.6583606, 101.5385152 3.6598328, 101.5366591 3.6624827, 101.5352429 3.6645278, 101.5336818 3.6667976, 101.5324426 3.6685589, 101.5309299 3.6707645, 101.5295566 3.6727292, 101.5281189 3.6747635, 101.5269066 3.6764766, 101.5262038 3.6772153, 101.5250934 3.6782914, 101.5241814 3.6791051, 101.5226418 3.6805719, 101.5209145 3.6822154, 101.5193695 3.6836501, 101.5181357 3.6848385)";
    }

    private String getLine2String() {
        return  "LINESTRING (102.2262459 2.4634873, 102.2169172 2.4687289, 102.2157477 2.4693506, 102.2146641 2.4698008, 102.2134947 2.4700794, 102.212454 2.4701759, 102.2114026 2.4701866, 102.2103404 2.470058, 102.2093212 2.46979, 102.208259 2.469372, 102.2074758 2.468954, 102.2067248 2.4684287, 102.2060918 2.4679035, 102.2054266 2.4672389, 102.2049438 2.4666065, 102.2043966 2.4657704, 102.203946 2.4649022, 102.2035169 2.4638303, 102.2019397 2.4596178, 102.2015535 2.4586209, 102.201178 2.4576991, 102.2007703 2.4568844, 102.2004162 2.4563378, 102.1999764 2.4557161, 102.1994828 2.4551373, 102.1989142 2.4545799, 102.1982919 2.4540439, 102.197734 2.4536473, 102.1969508 2.4531435, 102.1957921 2.4526504, 102.1946548 2.452286, 102.1937429 2.4521145, 102.1929597 2.4520609, 102.1918761 2.452018, 102.1908247 2.4521467, 102.1899234 2.4523289, 102.1890544 2.4526397, 102.1883034 2.452972, 102.1875524 2.4533472, 102.186737 2.4538617, 102.185943 2.4545048, 102.1851384 2.4553302, 102.1845376 2.4560484, 102.1840655 2.4568201, 102.1837973 2.4573668, 102.1834432 2.458085, 102.1831643 2.4589318, 102.1829819 2.4595963, 102.1827351 2.4612899, 102.1823703 2.4638625, 102.1818017 2.4682036, 102.1811365 2.4729735, 102.1806537 2.4766394, 102.1799993 2.4817201, 102.1791302 2.4878619, 102.1787547 2.4910454, 102.1785723 2.4920315, 102.1781646 2.4933606, 102.1776818 2.4945075, 102.1763407 2.4978517, 102.1746885 2.501957, 102.1732616 2.5054191, 102.1724462 2.5074878, 102.1701931 2.5129435, 102.1685194 2.5170808, 102.1668672 2.5213254, 102.165569 2.524423, 102.1649467 2.5258914, 102.1646248 2.526363, 102.1635627 2.5278529, 102.1630477 2.5284209, 102.1622967 2.5290962, 102.1592497 2.5315721, 102.1550762 2.5348841, 102.1496259 2.5392893, 102.1441006 2.5436946, 102.1382641 2.5483141, 102.1344339 2.5515296, 102.1314942 2.5543056, 102.1285116 2.5570816, 102.1275889 2.5578212, 102.1263551 2.5585714, 102.1231472 2.5605221, 102.1187591 2.5632231, 102.1157335 2.5650666, 102.1120321 2.5673281, 102.1107446 2.5681105, 102.1094893 2.5690537, 102.1087169 2.5698361, 102.1080624 2.5706293, 102.1074294 2.5715939, 102.1063887 2.5733517, 102.1040069 2.5773066, 102.1018397 2.58094, 102.0995866 2.5847341, 102.0975374 2.5882282, 102.0946299 2.5930298, 102.0919692 2.5974777, 102.0892977 2.602022, 102.086079 2.6073488, 102.08418 2.6105319, 102.0830428 2.6123539, 102.0818733 2.6139723, 102.0790409 2.6177985, 102.0761334 2.621614, 102.073934 2.6244756, 102.0726894 2.6256545, 102.0713698 2.6265869, 102.070372 2.6271121, 102.0685803 2.6279159, 102.0656728 2.6291699, 102.0643102 2.6299094, 102.0634626 2.6304667, 102.0620035 2.6316456, 102.0602869 2.6330067, 102.0588063 2.6339392, 102.056961 2.634743, 102.0542144 2.6353646, 102.050234 2.6361148, 102.0491933 2.6362327, 102.0476483 2.6362327, 102.0450305 2.6361898, 102.0438396 2.636372, 102.0422839 2.6367471, 102.0407926 2.6373473, 102.0391189 2.6383548, 102.038164 2.6391479, 102.0338618 2.6430597, 102.0327674 2.6441315, 102.0317911 2.645364, 102.0293449 2.6485042, 102.0272099 2.6511406, 102.0261478 2.6522445, 102.0249461 2.6531233, 102.0239162 2.6537342, 102.0225858 2.654313, 102.021191 2.6547095, 102.0194637 2.6549346, 102.0174681 2.6551811, 102.0145821 2.6554811, 102.0134877 2.6555133, 102.0124578 2.6553097, 102.0099258 2.6546238, 102.008928 2.6545487, 102.0079516 2.6546559, 102.0069968 2.6549774, 102.0061814 2.6554061, 102.0054733 2.655942, 102.0046042 2.6567136, 102.0037459 2.6573031, 102.002952 2.6576353, 102.002083 2.6579033, 102.0008813 2.6580533, 101.9994651 2.6582676, 101.998242 2.6587499, 101.9973194 2.6593501, 101.9954526 2.6613114, 101.9935321 2.6632833, 101.9929098 2.6642907, 101.9910215 2.6677953, 101.9904422 2.668867, 101.9899272 2.6696386, 101.9893157 2.6703567, 101.9885861 2.670839, 101.9874918 2.6713963, 101.9850456 2.6723072, 101.9842087 2.6727788, 101.9835972 2.6733146, 101.9831251 2.6738826, 101.9823205 2.6750722, 101.981784 2.675801, 101.9809686 2.6765083, 101.980121 2.6769692, 101.9792091 2.6774622, 101.9777392 2.6782659, 101.976999 2.6787697, 101.9762908 2.6794663, 101.9758724 2.6802057, 101.9755291 2.6809131, 101.9752609 2.6822313, 101.9748854 2.6833994, 101.9743811 2.6844283, 101.973437 2.6862716, 101.9724177 2.6888973, 101.9709908 2.6925518, 101.9704544 2.6938378, 101.9699394 2.694738, 101.9692849 2.6955097, 101.9685875 2.6961527, 101.9676219 2.6967207, 101.9656586 2.697578, 101.96406 2.6983925, 101.961764 2.6997536, 101.9566678 2.7027543, 101.9553052 2.7036117, 101.9543827 2.7043144, 101.9536316 2.7051717, 101.9528913 2.706447, 101.9523442 2.7077009, 101.9513464 2.7104444, 101.9507992 2.7114196, 101.9501233 2.7122234, 101.9491792 2.7129629, 101.9438899 2.7164887, 101.9399738 2.7191679, 101.9392014 2.7195644, 101.9362295 2.7206789, 101.9341266 2.7213005, 101.9281399 2.7228759, 101.9274855 2.7231224, 101.9260263 2.7240118, 101.9252002 2.724269, 101.9242024 2.7241404, 101.9231939 2.7237225, 101.9223464 2.7233474, 101.9216382 2.7232081, 101.9208336 2.7232295, 101.9199645 2.723476, 101.9191813 2.7238189, 101.9183445 2.7243655, 101.9168746 2.7252443, 101.91607 2.7255336, 101.9154048 2.7255872, 101.9145465 2.7255658, 101.9074011 2.7252443, 101.9064355 2.7252228, 101.9054913 2.7253407, 101.9045901 2.7256193, 101.9039035 2.7258444, 101.902734 2.7265303, 101.9017792 2.7273769, 101.9010818 2.7281806, 101.8969083 2.7335282, 101.8959856 2.7347285, 101.8955886 2.7353715, 101.8951595 2.7362395, 101.8943334 2.7381364, 101.8938935 2.7391545, 101.8934858 2.7398403, 101.8930137 2.7403226, 101.8924451 2.7407512, 101.8917692 2.7410513, 101.8911362 2.741212, 101.8904066 2.7412442, 101.8895483 2.7410942, 101.8887329 2.7407298, 101.8866622 2.7395188, 101.8858147 2.7390366, 101.8848705 2.7387472, 101.88398 2.738565, 101.882832 2.7385115, 101.881963 2.7385758, 101.8810081 2.7388115, 101.879195 2.7393581, 101.8718886 2.7412763, 101.8653977 2.7432053, 101.8580055 2.7452843, 101.8489826 2.747792, 101.8400562 2.7502996, 101.830765 2.7529145, 101.8212056 2.7555614, 101.815809 2.7571046, 101.814779 2.7574582, 101.8136632 2.7580262, 101.8127298 2.7586906, 101.8119895 2.7592586, 101.8111205 2.7601802, 101.8087172 2.7630736, 101.8060136 2.7664064, 101.805563 2.7670601, 101.8051338 2.7679924, 101.8037927 2.7718074, 101.8020117 2.7768334, 101.8002093 2.7819236, 101.798203 2.7875174, 101.796422 2.7926504, 101.7961538 2.7935291, 101.796025 2.7947936, 101.7960572 2.7958974, 101.7962182 2.7969368, 101.7965078 2.797762, 101.7967975 2.7985121, 101.7971837 2.7992194, 101.7976773 2.7999159, 101.7983317 2.8006982, 101.7992759 2.8017376, 101.8040287 2.8068599, 101.8081915 2.8114034, 101.8099618 2.8131394, 101.8106377 2.8137288, 101.8113565 2.8140395, 101.8125367 2.814361, 101.8132555 2.8146182, 101.8140388 2.8150897, 101.8146825 2.8156898, 101.8176544 2.8191403, 101.8207979 2.8227623, 101.8213129 2.8233195, 101.82289 2.8244768, 101.8249714 2.8259234, 101.8255722 2.8265128, 101.8259692 2.8271022, 101.8262696 2.8277665, 101.8264413 2.8284845, 101.8270957 2.8318064, 101.8277931 2.8355569, 101.8282545 2.8382036, 101.8282652 2.8389859, 101.8280935 2.8398003, 101.8277287 2.8405504, 101.8271923 2.8411719, 101.8261945 2.842147, 101.8257117 2.8426614, 101.8253469 2.8431864, 101.8250895 2.8437222, 101.8247998 2.8444937, 101.8236411 2.8486514, 101.8222892 2.8534948, 101.8205726 2.8593991, 101.8193066 2.8636209, 101.8191349 2.8644675, 101.8191349 2.8651533, 101.819253 2.8658498, 101.8193817 2.8665891, 101.8193495 2.8672106, 101.8191779 2.8678857, 101.8189418 2.8686036, 101.8175578 2.8730183, 101.8164742 2.8762865, 101.8160772 2.8774223, 101.8156373 2.8781938, 101.8150794 2.8790189, 101.8143928 2.8797476, 101.8120861 2.8816549, 101.8092644 2.884023, 101.8085885 2.884473, 101.8078911 2.8847516, 101.8070221 2.8849873, 101.8057883 2.8850945, 101.8035459 2.8853409, 101.8028378 2.8854481, 101.8020225 2.8857267, 101.801368 2.8860589, 101.8008316 2.8864767, 101.7995656 2.8875804, 101.7942655 2.8923058, 101.7933965 2.8929809, 101.7925274 2.8935702, 101.7911005 2.8943417, 101.7881608 2.8959597, 101.7864978 2.8968383, 101.7857146 2.8975134, 101.7852533 2.8981134, 101.7849314 2.8988099, 101.7847705 2.8996135, 101.7847598 2.9004815, 101.7849851 2.9012422, 101.7852747 2.9019173, 101.7864013 2.9042532, 101.7868197 2.9049282, 101.7873239 2.9054211, 101.7881072 2.905914, 101.7889225 2.9062247, 101.7896628 2.9063855, 101.7921627 2.9068355, 101.7928278 2.9070069, 101.7934501 2.9073712, 101.7940295 2.9078641, 101.7945123 2.9084535, 101.7948234 2.9091499, 101.7962825 2.9138538, 101.7966795 2.9150968, 101.7968404 2.9158361, 101.7968297 2.9166611, 101.7961431 2.9214614, 101.7958856 2.9232401, 101.7955422 2.9242366, 101.7946517 2.9254902, 101.7940831 2.9263045, 101.7938364 2.9271832, 101.7937183 2.9281689, 101.7939329 2.9292083, 101.794523 2.9318655, 101.7946088 2.9325727, 101.7945552 2.933387, 101.794287 2.9342335, 101.7939007 2.9348764, 101.7933428 2.9354335, 101.7927098 2.9359907, 101.7895019 2.9384015, 101.7874956 2.9399016, 101.7868304 2.940598, 101.7863369 2.9414445, 101.7860901 2.9423017, 101.7860365 2.9430089, 101.7861438 2.9436517, 101.786412 2.9444232, 101.7882359 2.9480662, 101.7908108 2.9529842, 101.792227 2.9557593, 101.7925703 2.9566164, 101.7928386 2.9575272, 101.7929351 2.9582772, 101.7928815 2.9593915, 101.7926884 2.9602808, 101.7921948 2.9619094, 101.7907894 2.9660666, 101.7903602 2.9675131, 101.7902636 2.9682417, 101.7902851 2.9691524, 101.7903602 2.973931, 101.7904246 2.9786775, 101.7905426 2.9831025, 101.7905533 2.9867989, 101.7903709 2.9877739, 101.7900062 2.9885882, 101.7895448 2.9891882, 101.7889225 2.9897132, 101.7880964 2.990131, 101.7857361 2.9910953, 101.7816377 2.992831, 101.7755759 2.9953917, 101.7747068 2.9956917, 101.7739129 2.9958846, 101.7732477 2.9959274, 101.7722392 2.9959167, 101.7712629 2.995756, 101.7704153 2.9955417, 101.7697287 2.9952631, 101.7692029 2.9949953, 101.7682052 2.9943524, 101.7657375 2.9921775, 101.7649007 2.9916203, 101.7640638 2.9911703, 101.7631412 2.9908703, 101.7620361 2.990656, 101.7608345 2.9906346, 101.7557383 2.991256, 101.7486894 2.9921346, 101.747359 2.9924132, 101.7461359 2.9927775, 101.7449558 2.9932382, 101.7440009 2.9937096, 101.7431533 2.9941917, 101.7421341 2.9949096, 101.7412114 2.9956381, 101.7384648 2.9984345, 101.7345488 3.0024202, 101.733712 3.0032559, 101.7329824 3.0036845, 101.7320597 3.0040059, 101.7309332 3.0042844, 101.7291307 3.0047344, 101.728369 3.0051309, 101.7277145 3.0056237, 101.7271566 3.0063951, 101.7267275 3.0073487, 101.7260838 3.0089344, 101.7236698 3.0134985, 101.7231011 3.0144628, 101.7225218 3.0151592, 101.72176 3.0159199, 101.72014 3.0172698, 101.7189705 3.0182984, 101.7182839 3.0190698, 101.7176616 3.0200019, 101.7157519 3.0235482, 101.7134559 3.0278766, 101.7129409 3.0286587, 101.7122328 3.0293551, 101.7114282 3.0299337, 101.7105162 3.0305337, 101.7096579 3.0312836, 101.7090893 3.03213, 101.7087245 3.0327728, 101.7085099 3.0335121, 101.708349 3.0343906, 101.7083061 3.0350977, 101.7084885 3.0360941, 101.7087245 3.0374976, 101.7087996 3.0384726, 101.708746 3.039169, 101.7085743 3.0398654, 101.708231 3.0411296, 101.708113 3.0421688, 101.70807 3.0433045, 101.7079198 3.0440973, 101.7076087 3.0450401, 101.7070508 3.0461972, 101.7067718 3.0469685, 101.7066431 3.0476542, 101.7065787 3.0486827, 101.7066753 3.0504933, 101.7068684 3.0549931, 101.7069757 3.0570822, 101.7073297 3.0601891, 101.7079198 3.0645817, 101.7085421 3.0695099, 101.7087781 3.0715776, 101.7090142 3.0726596, 101.709379 3.0737952, 101.7098081 3.0746523, 101.7104626 3.0756594, 101.7117178 3.076945, 101.7126942 3.0776628, 101.7136061 3.0782734, 101.7142177 3.0787341, 101.7148077 3.0793769, 101.7152476 3.0801483, 101.7154407 3.0808018, 101.715827 3.0827195, 101.715827 3.0834373, 101.7156661 3.0840908, 101.7153335 3.08468, 101.7147541 3.0852907, 101.7140245 3.0856335, 101.7132306 3.0858049, 101.7123187 3.0860513, 101.7116964 3.0864477, 101.7112136 3.0869512, 101.7109132 3.0874654, 101.7107415 3.0880547, 101.7104411 3.0912044, 101.7103124 3.0923185, 101.7100978 3.0933577, 101.7096579 3.0943862, 101.7091107 3.0951468, 101.7083704 3.0958539, 101.7046368 3.0988, 101.7029309 3.1002249, 101.6990256 3.1033317, 101.6977596 3.1043708, 101.6917193 3.1092239, 101.6886294 3.1117093, 101.6879642 3.1120842, 101.6872454 3.1124056, 101.6865158 3.1125878, 101.6858828 3.112652, 101.6843057 3.1125985, 101.6835117 3.1126842, 101.6827714 3.1128556, 101.6819131 3.1132198, 101.6813552 3.1135948, 101.6808724 3.1140447, 101.6803467 3.1146339, 101.6799176 3.1153089, 101.679602 3.115998, 101.6793704 3.1167123, 101.6787198 3.1196801, 101.678311 3.1217023, 101.678117 3.123028, 101.678092 3.12391, 101.678208 3.124894, 101.6787196 3.1263736, 101.679252 3.12729, 101.6801885 3.1284621, 101.681903 3.130658, 101.6831497 3.1321045, 101.684412 3.133324, 101.6879347 3.1351898, 101.689965 3.136383, 101.6911641 3.1368396, 101.691925 3.137102, 101.692675 3.137727, 101.693136 3.138157, 101.693503 3.1388857, 101.6939974 3.1404395, 101.694143 3.141012, 101.6941575 3.1417996, 101.694154 3.142449, 101.6939107 3.1431172, 101.6934064 3.1439207, 101.692654 3.144736, 101.6915933 3.1460257, 101.6912499 3.1465185, 101.6910354 3.1470434, 101.6909173 3.1475094, 101.6906491 3.1491377, 101.6905794 3.1497644, 101.690621 3.150329, 101.690735 3.1509053, 101.6908905 3.1514088, 101.691135 3.15192, 101.6914806 3.1524372, 101.6919956 3.1531335, 101.692414 3.1536906, 101.6932831 3.154944, 101.693503 3.1553082, 101.6936371 3.1557474, 101.6937712 3.1562884, 101.6937766 3.1567651, 101.6936747 3.1572579, 101.6935352 3.1576382, 101.6930577 3.1588594, 101.692332 3.160732, 101.6921726 3.161259, 101.6920653 3.1618803, 101.692028 3.162539, 101.692 3.163132, 101.691967 3.163568, 101.6918239 3.1640925, 101.691588 3.164654, 101.691199 3.165176, 101.6897211 3.1665778, 101.6891525 3.1669902, 101.6886062 3.1673227, 101.6880045 3.1675526, 101.6873724 3.1678155, 101.686861 3.168105, 101.686445 3.16847, 101.6860786 3.1688863, 101.6857031 3.1694969, 101.6850594 3.1709324, 101.6848663 3.171634, 101.6847912 3.1720947, 101.684761 3.172737, 101.684795 3.1735, 101.6849843 3.1742853, 101.6851935 3.1748584, 101.6855744 3.1756405, 101.6859767 3.1764653, 101.6870818 3.178656, 101.6879884 3.1803699, 101.6882512 3.1809752, 101.6884604 3.1814251, 101.6890237 3.1830909, 101.689426 3.184446, 101.6898015 3.1852065, 101.6899732 3.1855922, 101.6900483 3.1859725, 101.6900429 3.1863795, 101.6899625 3.1867652, 101.6897855 3.1871722, 101.689485 3.1876114, 101.6891095 3.1881631, 101.6888306 3.1887041, 101.6882512 3.1899521, 101.6878811 3.1904984, 101.687291 3.1911411, 101.6856763 3.1930693, 101.6833213 3.1958223, 101.6807571 3.1988325, 101.6769216 3.203294, 101.674808 3.2057632, 101.6732577 3.207611, 101.6726944 3.208559, 101.6724155 3.2092821, 101.6721312 3.2103211, 101.67204 3.2115102, 101.6721687 3.2142257, 101.6723135 3.2171179, 101.6724691 3.2202993, 101.6726086 3.223304, 101.6727534 3.2266514, 101.6728285 3.2276048, 101.6729251 3.2280975, 101.6730646 3.2286171, 101.6733006 3.229083, 101.67366 3.2296615, 101.6740194 3.2300899, 101.6744057 3.2304756, 101.6749153 3.2308665, 101.6787455 3.2337962, 101.6803119 3.2350816, 101.6808108 3.2355369, 101.6810844 3.2359975, 101.6812453 3.2364795, 101.6811756 3.2377917)";

    }

    private void clearCurrentPolyline() {
        if (currentPolyline != null && mapView != null) {
            mapView.getOverlays().remove(currentPolyline);
            mapView.invalidate(); // refresh the map
            currentPolyline = null;
        }
    }


    public void applyFilter(String filter) {
        timerManager.unsubscribe(filterRunnable);

        if (filter == null) {
            timerManager.subscribe(updateRunnable); // Ensure all trains are shown immediately
            updateRunnable.run(); // Immediate update
            return;
        }

        filterRunnable = new Runnable() {
            @Override
            public void run() {
                    List<LocationModel> filteredList = filterLocations(Holder.getLocationModels(), filter);
                initMarker(filteredList);
            }
        };
        timerManager.subscribe(filterRunnable); // Subscribe filtered updates
        filterRunnable.run(); // Immediate update
    }

    private List<LocationModel> filterLocations(List<LocationModel> models, String filter) {
        List<LocationModel> filtered = new ArrayList<>();
        Pattern pattern = Pattern.compile("(\\d{2})(\\d{2})");

        for (LocationModel model : models) {
            Matcher matcher = pattern.matcher(model.getTripId());
            if (matcher.find()) {
                int lineNumber = Integer.parseInt(matcher.group(1));
                int tripNumber = Integer.parseInt(matcher.group(2));
                if (matchesFilter(filter, lineNumber, tripNumber)) {
                    filtered.add(model);
                }
            }
        }
        return filtered;
    }

    //filter
    private boolean matchesFilter(String filter, int lineNumber, int tripNumber) {
        return (filter.equals("to Port Klang") && (lineNumber == 21 || lineNumber == 23) && tripNumber % 2 != 0) ||
                (filter.equals("to Tg Malim") && (lineNumber == 21 || lineNumber == 23) && tripNumber % 2 == 0) ||
                (filter.equals("to P. Sebang") && (lineNumber == 20 || lineNumber == 22) && tripNumber % 2 != 0) ||
                (filter.equals("to Batu Caves") && (lineNumber == 20 || lineNumber == 22) && tripNumber % 2 == 0);
    }

    //dropdown menu
    public void showDropdownMenu(View view) {
        cleanedData.sort(Comparator.comparing(LocationModel::getTripId));
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Search by Trip Number:").setEnabled(false);

        for (int i = 0; i < cleanedData.size(); i++) {
            String tripText = formatTripText(cleanedData.get(i).getTripId());
            popup.getMenu().add(Menu.NONE, i, Menu.NONE, tripText);
        }

        popup.setOnMenuItemClickListener(item -> {
            moveCameraToMarker(cleanedData.get(item.getItemId()));
            return true;
        });
        popup.show();
    }

    //name on dropdown menu
    private String formatTripText(String tripId) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(tripId);
        if (!matcher.find()) return tripId;

        return matcher.group() +" "+RouteDeterminer.determineRouteText(tripId);
    }

    //camera animation on dropdown menu
    private void moveCameraToMarker(LocationModel marker) {
        if (mapController != null && marker != null) {
            GeoPoint point = new GeoPoint(marker.getLatitude(), marker.getLongitude());
            mapController.animateTo(point);  // Smooth camera movement
            mapController.setZoom(15.0);     // Zoom level (similar to Google Maps zoom)
        }
    }


    //filter popup menu
    private void showFilterDialog() {
        new BottomFilterDialogFragment().show(getSupportFragmentManager(), "filter");
    }

    //down message
    private void showStatusDialog() {
        if (fetchedLocationData != null && !fetchedLocationData.isEmpty()) {
            BottomAlertDialogFragment.newInstance(fetchedLocationData)
                    .show(getSupportFragmentManager(), "BottomAlertDialogFragment");
        } else {
            showToast("Status API is down");
        }
    }

    //notification popup
    private void showNotificationDialog() {
        BottomNotificationDialogFragment.newInstance()
                .show(getSupportFragmentManager(), "BottomNotificationDialogFragment");
    }

    //marker popup
    private void showBottomSheetDialog(Marker marker) {
        Log.d(TAG, "showBottomSheetDialog IS CALLED!!!");
        Object relatedObject = marker.getRelatedObject(); // ✅ instead of getTag()
        if (relatedObject instanceof LocationModel) {
            LocationModel info = (LocationModel) relatedObject; // ✅ explicit cast
            BottomMarkerDialogFragment.newInstance(info, currentLocation)
                    .show(getSupportFragmentManager(), "BottomMarkerDialogFragment");
        }
    }

    //notification function
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Train ETA Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Channel for train ETA notifications");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    //location permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FINE_LOCATION_PERMISSION_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        } else {
            showToast("Location permission denied, please allow it");
        }
    }

    //location permission granted
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    //location request
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                FINE_LOCATION_PERMISSION_CODE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}