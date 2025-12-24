package com.example.wifiroomlocator;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView roomStatus;
    private TextView rawDebugData;
    private EditText roomInput;
    private Button scanBtn;
    private Button saveBtn;
    private WifiManager wifiManager;
    private android.widget.ProgressBar distanceMeter;
    private HashMap<String, String> roomMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize UI elements
        roomStatus = findViewById(R.id.roomStatus);
        rawDebugData = findViewById(R.id.rawDebugData);
        roomInput = findViewById(R.id.roomInput);
        scanBtn = findViewById(R.id.scanBtn);
        saveBtn = findViewById(R.id.saveBtn);
        distanceMeter = findViewById(R.id.distanceMeter);

        // 2. Initialize WiFi Manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 3. Scan Button Listener
        scanBtn.setOnClickListener(v -> {
            if (checkPermissions()) {
                toggleAutoScan(); // Switch to the repeating loop
            } else {
                requestPermissions();
            }
        });

        // 4. Save Button Listener
        saveBtn.setOnClickListener(v -> {
            saveCurrentLocation();
        });

        // Load your manual test data
        initializeRoomData();
    }

    private void initializeRoomData() {
        roomMap.put("00:11:22:33:44:55", "Computer Lab 1");
        roomMap.put("AA:BB:CC:DD:EE:FF", "College Library");
    }

    private void startWifiScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();

        StringBuilder sb = new StringBuilder();
        String detectedRoom = "Unknown Area";
        String proximityStatus = "N/A";

        int currentStrongestRssi = -100;

        // Track the strongest UNKNOWN network
        int strongestUnknownRssi = -100;
        String strongestUnknownSsid = "None";

        for (ScanResult network : results) {
            String ssid = network.SSID.isEmpty() ? "(Hidden)" : network.SSID;
            sb.append(ssid).append(" | ").append(network.level).append("dBm\n");

            String savedName = identifyRoom(network.BSSID);

            // CASE A: The router is already recognized (Saved)
            if (savedName != null) {
                if (network.level > currentStrongestRssi) {
                    currentStrongestRssi = network.level;
                    detectedRoom = savedName;

                    SharedPreferences sp = getSharedPreferences("RoomData", Context.MODE_PRIVATE);
                    int savedRssi = sp.getInt(network.BSSID + "_rssi", -60);
                    int offset = Math.abs(currentStrongestRssi - savedRssi);

                    if (offset <= 6) proximityStatus = "At Center";
                    else if (offset <= 15) proximityStatus = "Near";
                    else proximityStatus = "Far Away";
                }
            }
            // CASE B: The router is NOT recognized
            else {
                if (network.level > strongestUnknownRssi) {
                    strongestUnknownRssi = network.level;
                    strongestUnknownSsid = ssid;
                }
            }
        }

        // Logic for the Save Button Text
        if (detectedRoom.equals("Unknown Area")) {
            // If we don't know where we are, show the strongest nearby unknown router on the button
            saveBtn.setText("Map " + strongestUnknownSsid);
        } else {
            // If we are in a recognized room, just show "Save Location" or "Update"
            saveBtn.setText("Update " + detectedRoom);
        }

        // Update Minecraft HUD
        String hud = "LOC: " + detectedRoom.toUpperCase() + "\n" +
                "STATUS: " + proximityStatus + "\n" +
                "RSSI: " + currentStrongestRssi + " dBm";
        roomStatus.setText(hud);
        rawDebugData.setText(sb.toString());

        // Update Progress Bar
        if (currentStrongestRssi > -100) {
            int progress = 100 - Math.abs(currentStrongestRssi + 30);
            distanceMeter.setProgress(Math.max(0, Math.min(100, progress)));
        } else {
            distanceMeter.setProgress(0);
        }
    }
    private android.os.Handler handler = new android.os.Handler();
    private boolean isAutoScanning = false;

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoScanning) {
                startWifiScan(); // Your existing scan logic
                // Schedule the next scan in 4 seconds (4000 milliseconds)
                handler.postDelayed(this, 4000);
            }
        }
    };

    private void toggleAutoScan() {
        if (!isAutoScanning) {
            isAutoScanning = true;
            handler.post(scanRunnable);
            scanBtn.setText("Scanning..");
        } else {
            isAutoScanning = false;
            handler.removeCallbacks(scanRunnable);
            scanBtn.setText("Scan Now");
        }
    }
    private void saveCurrentLocation() {
        String nameText = roomInput.getText().toString().trim();
        if (nameText.isEmpty()) {
            Toast.makeText(this, "Enter room name!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        if (results.isEmpty()) return;

        // Find strongest network to "Fingerprint"
        ScanResult strongest = results.get(0);
        for (ScanResult r : results) {
            if (r.level > strongest.level) strongest = r;
        }

        // Save to SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("RoomData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(strongest.BSSID, nameText);
        // Store the signal strength AT THE MOMENT of saving
        editor.putInt(strongest.BSSID + "_rssi", strongest.level);
        editor.apply();

        Toast.makeText(this, "Marked " + nameText + " at " + strongest.level + "dBm", Toast.LENGTH_SHORT).show();
        roomInput.setText("");
    }

    private String identifyRoom(String bssid) {
        // 1. Check permanent storage first (SharedPreferences)
        SharedPreferences sharedPref = getSharedPreferences("RoomData", Context.MODE_PRIVATE);
        String name = sharedPref.getString(bssid, null);

        // 2. If not found in storage, check the manual HashMap
        if (name == null) {
            name = roomMap.get(bssid);
        }

        return name;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop scanning if the user minimizes the app
        if (isAutoScanning) toggleAutoScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanRunnable); // Clean up memory
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWifiScan();
        }
    }
}
