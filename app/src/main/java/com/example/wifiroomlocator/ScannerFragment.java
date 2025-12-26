package com.example.wifiroomlocator;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.List;

public class ScannerFragment extends Fragment {

    private TextView roomStatus, rawDebugData;
    private EditText roomInput;
    private Button scanBtn, saveBtn;
    private ProgressBar distanceMeter;
    private WifiManager wifiManager;
    private HashMap<String, String> roomMap = new HashMap<>();

    private Handler handler = new Handler();
    private boolean isAutoScanning = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Load the fragment_scanner.xml layout
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        // 1. Initialize UI elements from the 'view'
        roomStatus = view.findViewById(R.id.roomStatus);
        rawDebugData = view.findViewById(R.id.rawDebugData);
        roomInput = view.findViewById(R.id.roomInput);
        scanBtn = view.findViewById(R.id.scanBtn);
        saveBtn = view.findViewById(R.id.saveBtn);
        distanceMeter = view.findViewById(R.id.distanceMeter);

        // 2. Initialize WiFi Manager
        wifiManager = (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 3. Listeners
        scanBtn.setOnClickListener(v -> toggleAutoScan());
        saveBtn.setOnClickListener(v -> saveCurrentLocation());

        initializeRoomData();

        // Auto-start discovery if permissions exist
        if (checkPermissions()) {
            toggleAutoScan();
        }

        return view;
    }

    private void startWifiScan() {
        if (!checkPermissions()) return;

        wifiManager.startScan();
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        List<ScanResult> results = wifiManager.getScanResults();

        StringBuilder sb = new StringBuilder();
        String detectedRoom = "Unknown Area";
        String proximityStatus = "Scanning..";
        int currentStrongestRssi = -100;
        ScanResult strongestUnknown = null;

        for (ScanResult network : results) {
            String ssid = network.SSID.isEmpty() ? "(Hidden)" : network.SSID;
            sb.append(ssid).append(" | ").append(network.level).append("dBm\n");

            String savedName = identifyRoom(network.BSSID);

            if (savedName != null) {
                if (network.level > currentStrongestRssi) {
                    currentStrongestRssi = network.level;
                    detectedRoom = savedName;
                    SharedPreferences sp = requireActivity().getSharedPreferences("RoomData", Context.MODE_PRIVATE);
                    int savedRssi = sp.getInt(network.BSSID + "_rssi", -60);
                    int offset = Math.abs(currentStrongestRssi - savedRssi);
                    if (offset <= 6) proximityStatus = "At Center";
                    else if (offset <= 15) proximityStatus = "Near";
                    else proximityStatus = "Far Away";
                }
            } else {
                if (strongestUnknown == null || network.level > strongestUnknown.level) {
                    strongestUnknown = network;
                }
            }
        }

        // AUTO-DISCOVERY LOGIC
        if (detectedRoom.equals("Unknown Area") && strongestUnknown != null) {
            String tempName = "Area " + strongestUnknown.BSSID.substring(12);
            autoFingerprint(strongestUnknown.BSSID, tempName, strongestUnknown.level);
            detectedRoom = tempName;
            proximityStatus = "New Discovery";
            saveBtn.setText("Rename " + tempName);
        } else if (!detectedRoom.equals("Unknown Area")) {
            saveBtn.setText("Update " + detectedRoom);
        }

        roomStatus.setText("LOC: " + detectedRoom + "\nSTATUS: " + proximityStatus);
        rawDebugData.setText(sb.toString());

        if (currentStrongestRssi > -100) {
            int progress = 100 - Math.abs(currentStrongestRssi + 30);
            distanceMeter.setProgress(Math.max(0, Math.min(100, progress)));
        }
    }

    private void autoFingerprint(String bssid, String name, int rssi) {
        SharedPreferences sharedPref = requireActivity().getSharedPreferences("RoomData", Context.MODE_PRIVATE);
        if (!sharedPref.contains(bssid)) {
            sharedPref.edit().putString(bssid, name).putInt(bssid + "_rssi", rssi).apply();
        }
    }

    private void saveCurrentLocation() {
        String nameText = roomInput.getText().toString().trim();
        if (nameText.isEmpty()) {
            Toast.makeText(getContext(), "Type a room name!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        List<ScanResult> results = wifiManager.getScanResults();
        if (results.isEmpty()) return;

        ScanResult strongest = results.get(0);
        for (ScanResult r : results) if (r.level > strongest.level) strongest = r;

        SharedPreferences sharedPref = requireActivity().getSharedPreferences("RoomData", Context.MODE_PRIVATE);
        sharedPref.edit().putString(strongest.BSSID, nameText).putInt(strongest.BSSID + "_rssi", strongest.level).apply();
        Toast.makeText(getContext(), "Updated to: " + nameText, Toast.LENGTH_SHORT).show();
        roomInput.setText("");
    }

    private String identifyRoom(String bssid) {
        SharedPreferences sp = requireActivity().getSharedPreferences("RoomData", Context.MODE_PRIVATE);
        String name = sp.getString(bssid, null);
        return (name == null) ? roomMap.get(bssid) : name;
    }

    private Runnable scanRunnable = new Runnable() {
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
            scanBtn.setText("Scanning..");
        } else {
            handler.removeCallbacks(scanRunnable);
            scanBtn.setText("Scan Now");
        }
    }

    private void initializeRoomData() {
        roomMap.put("00:11:22:33:44:55", "Computer Lab 1");
    }

    private static final int PERMISSIONS_REQUEST_CODE = 123;

    private void requestPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
        }, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleAutoScan();
            } else {
                Toast.makeText(getContext(), "Permissions required for this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
