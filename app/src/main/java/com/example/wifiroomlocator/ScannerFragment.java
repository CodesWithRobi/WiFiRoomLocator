package com.example.wifiroomlocator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ScannerFragment extends Fragment {

    private static final String TAG = "ScannerFragment";
    private TextView roomStatus, rawDebugData;
    private EditText roomInput;
    private Button scanBtn, saveBtn, logoutBtn;
    private ProgressBar distanceMeter;
    private WifiManager wifiManager;
    private final HashMap<String, Mapping> mappings = new HashMap<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final Handler handler = new Handler();
    private boolean isAutoScanning = false;
    private boolean firebaseDataLoaded = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
                    // If permissions are granted, attempt to start scanning, but only if data is loaded
                    if(firebaseDataLoaded && !isAutoScanning) {
                        toggleAutoScan();
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

        wifiManager = (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        scanBtn.setOnClickListener(v -> toggleAutoScan());
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

        initializeRoomData();

        return view;
    }

    private void startWifiScan() {
        if (!checkPermissions()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

            // Filter for SSIDs containing "sec" case-insensitively
            if (!ssid.toLowerCase().contains("sec")) {
                continue;
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
        rawDebugData.setText(sb.toString());

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
        List<ScanResult> results = wifiManager.getScanResults();
        if (results.isEmpty()) {
            Toast.makeText(getContext(), "No WiFi networks found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Filter for "sec" networks
        List<ScanResult> secNetworks = new ArrayList<>();
        for (ScanResult network : results) {
            if (network.SSID != null && !network.SSID.isEmpty() && network.SSID.toLowerCase().contains("sec")) {
                secNetworks.add(network);
            }
        }

        if (secNetworks.isEmpty()) {
            Toast.makeText(getContext(), "No 'sec' network in range to map.", Toast.LENGTH_SHORT).show();
            return;
        }

        ScanResult strongest = secNetworks.get(0);
        for (ScanResult r : secNetworks) {
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
                    return; // Fragment not attached to a context, so do nothing
                }
                mappings.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Mapping mapping = ds.getValue(Mapping.class);
                    if (mapping != null) {
                        mappings.put(ds.getKey(), mapping);
                    }
                }
                // CRITICAL FIX: Only trigger the first scan AFTER the initial data is loaded.
                if (!firebaseDataLoaded) {
                    firebaseDataLoaded = true;
                    if (checkPermissions() && !isAutoScanning) {
                        toggleAutoScan();
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
    public void onPause() {
        super.onPause();
        if (isAutoScanning) toggleAutoScan();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanRunnable);
    }
}
