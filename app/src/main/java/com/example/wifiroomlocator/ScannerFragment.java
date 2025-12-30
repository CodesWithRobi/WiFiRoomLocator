package com.example.wifiroomlocator;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class ScannerFragment extends Fragment {

    private TextView roomStatus, rawDebugData;
    private EditText roomInput, filterInput;
    private Button scanBtn, saveBtn, logoutBtn, addFilterBtn;
    private ProgressBar distanceMeter;
    private WifiManager wifiManager;
    private ChipGroup filterChipGroup;
    private final ArrayList<String> filters = new ArrayList<>();
    private boolean isServiceRunning = false;
    private String strongestBssid;
    private int strongestRssi;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean allGranted = true;
                for (boolean isGranted : permissions.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    checkWifiStateAndStartService();
                } else {
                    Toast.makeText(getContext(), R.string.permissions_required, Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver scanResultsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case WifiScanningService.ACTION_SCAN_RESULT:
                    roomStatus.setText(intent.getStringExtra(WifiScanningService.EXTRA_ROOM_STATUS));
                    rawDebugData.setText(intent.getStringExtra(WifiScanningService.EXTRA_RAW_DEBUG_DATA));
                    distanceMeter.setProgress(intent.getIntExtra(WifiScanningService.EXTRA_DISTANCE_METER, 0));
                    strongestBssid = intent.getStringExtra(WifiScanningService.EXTRA_STRONGEST_BSSID);
                    strongestRssi = intent.getIntExtra(WifiScanningService.EXTRA_STRONGEST_RSSI, -100);
                    break;
                case WifiScanningService.ACTION_SERVICE_STOPPED:
                    isServiceRunning = false;
                    scanBtn.setText("Start Scanning");
                    break;
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        roomStatus = view.findViewById(R.id.roomStatus);
        rawDebugData = view.findViewById(R.id.rawDebugData);
        roomInput = view.findViewById(R.id.roomInput);
        scanBtn = view.findViewById(R.id.scanBtn);
        saveBtn = view.findViewById(R.id.saveBtn);
        logoutBtn = view.findViewById(R.id.logoutBtn);
        distanceMeter = view.findViewById(R.id.distanceMeter);
        filterInput = view.findViewById(R.id.filterInput);
        addFilterBtn = view.findViewById(R.id.addFilterBtn);
        filterChipGroup = view.findViewById(R.id.filterChipGroup);

        wifiManager = (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        scanBtn.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopScanningService();
            } else {
                checkWifiStateAndStartService();
            }
        });
        saveBtn.setOnClickListener(v -> saveCurrentLocation());
        logoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        addFilterBtn.setOnClickListener(v -> {
            String filterText = filterInput.getText().toString().trim();
            if (!filterText.isEmpty() && !filters.contains(filterText)) {
                filters.add(filterText);
                updateFilterChips();
                filterInput.setText("");
                if (isServiceRunning) {
                    restartScanningService();
                }
            }
        });

        if (!isServiceRunning) {
            checkWifiStateAndStartService();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isServiceRunning = isMyServiceRunning(WifiScanningService.class);
        scanBtn.setText(isServiceRunning ? "Stop Scanning" : "Start Scanning");

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiScanningService.ACTION_SCAN_RESULT);
        filter.addAction(WifiScanningService.ACTION_SERVICE_STOPPED);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(scanResultsReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(scanResultsReceiver);
    }

    private void checkWifiStateAndStartService() {
        if (wifiManager == null) return;
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
            } else {
                wifiManager.setWifiEnabled(true);
                Toast.makeText(getContext(), "Turning on Wi-Fi...", Toast.LENGTH_SHORT).show();
            }
        } else {
            startScanningService();
        }
    }

    private void startScanningService() {
        if (!checkPermissions()) return;
        Intent serviceIntent = new Intent(getContext(), WifiScanningService.class);
        serviceIntent.putStringArrayListExtra("filters", filters);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
        isServiceRunning = true;
        scanBtn.setText("Stop Scanning");
    }

    private void stopScanningService() {
        Intent serviceIntent = new Intent(getContext(), WifiScanningService.class);
        requireContext().stopService(serviceIntent);
        isServiceRunning = false;
        scanBtn.setText("Start Scanning");
    }

    private void restartScanningService() {
        stopScanningService();
        // Give the service time to fully stop before restarting
        new Handler().postDelayed(this::startScanningService, 500);
    }

    private void updateFilterChips() {
        filterChipGroup.removeAllViews();
        for (String filter : filters) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, filterChipGroup, false);
            chip.setText(filter);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                filters.remove(filter);
                updateFilterChips();
                if (isServiceRunning) {
                    restartScanningService();
                }
            });
            filterChipGroup.addView(chip);
        }
    }

    private void saveCurrentLocation() {
        String nameText = roomInput.getText().toString().trim();
        if (nameText.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a room name.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (strongestBssid == null) {
            Toast.makeText(getContext(), "No network to map. Please scan first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String bssid = strongestBssid.replace(":", "");
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            Mapping mapping = new Mapping(nameText, strongestRssi, uid);
            FirebaseDatabase.getInstance("https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("mappings").child(bssid).setValue(mapping);
            Toast.makeText(getContext(), "Location mapped successfully!", Toast.LENGTH_SHORT).show();
            roomInput.setText("");
        } else {
            Toast.makeText(getContext(), "Not logged in.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
            return false;
        }
        return true;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) requireActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
