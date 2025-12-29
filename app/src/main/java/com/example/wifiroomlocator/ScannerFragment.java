package com.example.wifiroomlocator;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public class ScannerFragment extends Fragment {

    private static final String TAG = "ScannerFragment";
    private TextView roomStatus, rawDebugData;
    private EditText roomInput, filterInput;
    private Button scanBtn, saveBtn, logoutBtn, addFilterBtn;
    private ProgressBar distanceMeter;
    private WifiManager wifiManager;
    private ChipGroup filterChipGroup;
    private final HashMap<String, Mapping> mappings = new HashMap<>();
    private final ArrayList<String> filters = new ArrayList<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final Handler handler = new Handler();
    private boolean isAutoScanning = false;
    private boolean firebaseDataLoaded = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
                    if(firebaseDataLoaded && !isAutoScanning) {
                        checkWifiStateAndScan();
                    }
                } else {
                    Toast.makeText(getContext(), R.string.permissions_required, Toast.LENGTH_SHORT).show();
                }
            });

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

        scanBtn.setOnClickListener(v -> checkWifiStateAndScan());
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
            }
        });

        initializeRoomData();

        return view;
    }

    private void checkWifiStateAndScan() {
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
            } else {
                wifiManager.setWifiEnabled(true);
                Toast.makeText(getContext(), "Turning on Wi-Fi...", Toast.LENGTH_SHORT).show();
                // Add a delay to allow Wi-Fi to enable before scanning
                handler.postDelayed(this::toggleAutoScan, 2000);
            }
        } else {
            toggleAutoScan();
        }
    }

    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                if (isAutoScanning) {
                    toggleAutoScan(); // Stop scanning if Wi-Fi is turned off
                }
            } else if (wifiState == WifiManager.WIFI_STATE_ENABLED && !isAutoScanning && firebaseDataLoaded) {
                // If Wi-Fi is re-enabled, resume scanning if it was active before
                checkWifiStateAndScan();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().registerReceiver(wifiStateReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(wifiStateReceiver);
        if (isAutoScanning) {
            toggleAutoScan(); // Stop scanning when the app is paused
        }
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
            });
            filterChipGroup.addView(chip);
        }
    }

    private void startWifiScan() {
        if (!checkPermissions()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            rawDebugData.setText("Wi-Fi is turned off. Please enable it to scan.");
            if(isAutoScanning) toggleAutoScan();
            return;
        }

        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();

        StringBuilder sb = new StringBuilder();
        String detectedRoom = getString(R.string.unknown_area);
        String proximityStatus = getString(R.string.scanning);
        int currentStrongestRssi = -100;
        ScanResult strongestUnknown = null;

        for (ScanResult network : results) {
            String ssid = network.SSID.isEmpty() ? getString(R.string.hidden_ssid) : network.SSID;

            if (!filters.isEmpty()) {
                boolean match = false;
                for (String filter : filters) {
                    try {
                        if (ssid.matches(filter)) {
                            match = true;
                            break;
                        }
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "Invalid regex pattern: " + filter, e);
                    }
                }
                if (!match) {
                    continue;
                }
            } else {
                if (!ssid.toLowerCase().contains("sec")) {
                    continue;
                }
            }

            sb.append(ssid).append(" | ").append(network.level).append("dBm\n");

            Mapping mapping = mappings.get(network.BSSID.replace(":", ""));

            if (mapping != null) {
                if (network.level > currentStrongestRssi) {
                    currentStrongestRssi = network.level;
                    detectedRoom = mapping.name;
                    int offset = Math.abs(currentStrongestRssi - mapping.rssi);
                    if (offset <= 6) proximityStatus = getString(R.string.at_center);
                    else if (offset <= 15) proximityStatus = getString(R.string.near);
                    else proximityStatus = getString(R.string.far_away);
                }
            } else {
                if (strongestUnknown == null || network.level > strongestUnknown.level) {
                    strongestUnknown = network;
                }
            }
        }

        if (detectedRoom.equals(getString(R.string.unknown_area)) && strongestUnknown != null) {
            String bssidKey = strongestUnknown.BSSID.replace(":", "");
            if (firebaseDataLoaded && !mappings.containsKey(bssidKey)) {
                String tempName = "RM " + strongestUnknown.BSSID.substring(12);
                autoFingerprint(strongestUnknown.BSSID, tempName, strongestUnknown.level);
                detectedRoom = tempName;
                proximityStatus = getString(R.string.new_discovery);
                saveBtn.setText(getString(R.string.rename_s, tempName));
            }
        } else if (!detectedRoom.equals(getString(R.string.unknown_area))) {
            saveBtn.setText(getString(R.string.update_s, detectedRoom));
        }

        updateUserLocation(detectedRoom);

        roomStatus.setText(getString(R.string.location_status, detectedRoom, proximityStatus));
        if (sb.length() == 0) {
            rawDebugData.setText("No matching Wi-Fi networks found.");
        } else {
            rawDebugData.setText(sb.toString());
        }

        if (currentStrongestRssi > -100) {
            int progress = 100 - Math.abs(currentStrongestRssi + 30);
            distanceMeter.setProgress(Math.max(0, Math.min(100, progress)));
        }
    }

    private void updateUserLocation(String location) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance(dbUrl).getReference("users").child(uid).child("currentLocation").setValue(location);
        }
    }

    private void autoFingerprint(String bssid, String name, int rssi) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Mapping mapping = new Mapping(name, rssi, uid);
        FirebaseDatabase.getInstance(dbUrl).getReference("mappings").child(bssid.replace(":", "")).setValue(mapping);
    }

    private void saveCurrentLocation() {
        String nameText = roomInput.getText().toString().trim();
        if (nameText.isEmpty()) {
            Toast.makeText(getContext(), R.string.type_room_name, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(getContext(), "Wi-Fi is turned off. Please enable it to map.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        if (results.isEmpty()) {
            Toast.makeText(getContext(), "No WiFi networks found.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ScanResult> filteredNetworks = new ArrayList<>();
        if (!filters.isEmpty()) {
            for (ScanResult network : results) {
                if (network.SSID != null && !network.SSID.isEmpty()) {
                    for (String filter : filters) {
                        try {
                            if (network.SSID.matches(filter)) {
                                filteredNetworks.add(network);
                                break;
                            }
                        } catch (PatternSyntaxException e) {
                            Log.e(TAG, "Invalid regex pattern: " + filter, e);
                        }
                    }
                }
            }
        } else {
            for (ScanResult network : results) {
                if (network.SSID != null && !network.SSID.isEmpty() && network.SSID.toLowerCase().contains("sec")) {
                    filteredNetworks.add(network);
                }
            }
        }

        if (filteredNetworks.isEmpty()) {
            Toast.makeText(getContext(), "No matching network in range to map.", Toast.LENGTH_SHORT).show();
            return;
        }

        ScanResult strongest = filteredNetworks.get(0);
        for (ScanResult r : filteredNetworks) {
            if (r.level > strongest.level) {
                strongest = r;
            }
        }

        autoFingerprint(strongest.BSSID, nameText, strongest.level);
        Toast.makeText(getContext(), getString(R.string.updated_to_s, nameText), Toast.LENGTH_SHORT).show();
        roomInput.setText("");
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoScanning) {
                startWifiScan();
                handler.postDelayed(this, 4000);
            }
        }
    };

    private void toggleAutoScan() {
        isAutoScanning = !isAutoScanning;
        if (isAutoScanning) {
            handler.post(scanRunnable);
            scanBtn.setText(R.string.scanning);
        } else {
            handler.removeCallbacks(scanRunnable);
            scanBtn.setText(R.string.scan_now);
        }
    }

    private void initializeRoomData() {
        FirebaseDatabase.getInstance(dbUrl).getReference("mappings").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    return;
                }
                mappings.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Mapping mapping = ds.getValue(Mapping.class);
                    if (mapping != null) {
                        mappings.put(ds.getKey(), mapping);
                    }
                }
                if (!firebaseDataLoaded) {
                    firebaseDataLoaded = true;
                    if (checkPermissions() && !isAutoScanning) {
                        checkWifiStateAndScan();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load mappings: " + error.getMessage());
            }
        });
    }

    private void requestPermissions() {
        requestPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
        });
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanRunnable);
    }
}
