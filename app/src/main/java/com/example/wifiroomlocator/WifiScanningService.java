package com.example.wifiroomlocator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public class WifiScanningService extends Service {

    private static final String TAG = "WifiScanningService";
    public static final String ACTION_STOP_SERVICE = "com.example.wifiroomlocator.STOP_SERVICE";
    public static final String ACTION_SCAN_RESULT = "com.example.wifiroomlocator.SCAN_RESULT";
    public static final String EXTRA_ROOM_STATUS = "com.example.wifiroomlocator.EXTRA_ROOM_STATUS";
    public static final String EXTRA_RAW_DEBUG_DATA = "com.example.wifiroomlocator.EXTRA_RAW_DEBUG_DATA";
    public static final String EXTRA_DISTANCE_METER = "com.example.wifiroomlocator.EXTRA_DISTANCE_METER";
    public static final String EXTRA_STRONGEST_BSSID = "com.example.wifiroomlocator.EXTRA_STRONGEST_BSSID";
    public static final String EXTRA_STRONGEST_RSSI = "com.example.wifiroomlocator.EXTRA_STRONGEST_RSSI";

    private static final String CHANNEL_ID = "WifiScanningServiceChannel";
    private static final int SCAN_DELAY_MS = 4000;

    private WifiManager wifiManager;
    private final Handler handler = new Handler();
    private final HashMap<String, Mapping> mappings = new HashMap<>();
    private ArrayList<String> filters = new ArrayList<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private boolean isScanning = false;
    private BroadcastReceiver wifiScanReceiver;

    private final BroadcastReceiver stopServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        createNotificationChannel();

        ContextCompat.registerReceiver(this, stopServiceReceiver, new IntentFilter(ACTION_STOP_SERVICE), ContextCompat.RECEIVER_NOT_EXPORTED);

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    processScanResults();
                }
            }
        };
        ContextCompat.registerReceiver(this, wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);

        initializeRoomData();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("filters")) {
            filters = intent.getStringArrayListExtra("filters");
        }

        startForeground(1, createNotification("Scanning for location..."));
        startScanning();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScanning();
        unregisterReceiver(stopServiceReceiver);
        unregisterReceiver(wifiScanReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi Scanning Service Channel",
                    NotificationManager.IMPORTANCE_LOW // Silent notification
            );
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }

    private void initializeRoomData() {
        FirebaseDatabase.getInstance(dbUrl).getReference("mappings").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mappings.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Mapping mapping = ds.getValue(Mapping.class);
                    if (mapping != null) {
                        mappings.put(ds.getKey(), mapping);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load mappings: " + error.getMessage());
            }
        });
    }

    private void startScanning() {
        if (!isScanning) {
            isScanning = true;
            handler.post(scanRunnable);
        }
    }

    private void stopScanning() {
        if (isScanning) {
            isScanning = false;
            handler.removeCallbacks(scanRunnable);
        }
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) {
                wifiManager.startScan();
            }
        }
    };

    private void processScanResults() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Toast.makeText(this, "Scan finished.", Toast.LENGTH_SHORT).show();
        List<ScanResult> results = wifiManager.getScanResults();

        StringBuilder sb = new StringBuilder();
        String detectedRoom = "Unknown Area";
        String proximityStatus = "Scanning";
        ScanResult strongestMappedNetwork = null;
        ScanResult strongestOverallNetwork = null;

        for (ScanResult network : results) {
            String ssid = network.SSID.isEmpty() ? "Hidden SSID" : network.SSID;

            boolean isMatch = filters.isEmpty() || filters.stream().anyMatch(filter -> {
                try {
                    return ssid.matches(filter);
                } catch (PatternSyntaxException e) {
                    return false;
                }
            });

            if (isMatch) {
                sb.append(ssid).append(" | ").append(network.level).append("dBm\n");

                if (strongestOverallNetwork == null || network.level > strongestOverallNetwork.level) {
                    strongestOverallNetwork = network;
                }

                Mapping mapping = mappings.get(network.BSSID.replace(":", ""));
                if (mapping != null) {
                    if (strongestMappedNetwork == null || network.level > strongestMappedNetwork.level) {
                        strongestMappedNetwork = network;
                    }
                }
            }
        }

        if (strongestMappedNetwork != null) {
            detectedRoom = mappings.get(strongestMappedNetwork.BSSID.replace(":", "")).name;
            int offset = Math.abs(strongestMappedNetwork.level - mappings.get(strongestMappedNetwork.BSSID.replace(":", "")).rssi);
            if (offset <= 6) proximityStatus = "At Center";
            else if (offset <= 15) proximityStatus = "Near";
            else proximityStatus = "Far Away";
        }

        updateUserLocation(detectedRoom);

        String roomStatusText = "LOC: " + detectedRoom + " | STATUS: " + proximityStatus;
        int distanceMeterValue = (strongestMappedNetwork != null) ? Math.max(0, Math.min(100, 100 - Math.abs(strongestMappedNetwork.level + 30))) : 0;

        Intent intent = new Intent(ACTION_SCAN_RESULT);
        intent.putExtra(EXTRA_ROOM_STATUS, roomStatusText);
        intent.putExtra(EXTRA_RAW_DEBUG_DATA, sb.length() > 0 ? sb.toString() : "No matching Wi-Fi networks found.");
        intent.putExtra(EXTRA_DISTANCE_METER, distanceMeterValue);

        if (strongestOverallNetwork != null) {
            intent.putExtra(EXTRA_STRONGEST_BSSID, strongestOverallNetwork.BSSID);
            intent.putExtra(EXTRA_STRONGEST_RSSI, strongestOverallNetwork.level);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        updateNotification(roomStatusText);

        // Schedule the next scan after processing results
        if (isScanning) {
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS);
        }
    }

    private void updateUserLocation(String location) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance(dbUrl).getReference("users").child(uid).child("currentLocation").setValue(location);
        }
    }

    private void updateNotification(String contentText) {
        Notification notification = createNotification(contentText);
        getSystemService(NotificationManager.class).notify(1, notification);
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopSelfIntent = new Intent(ACTION_STOP_SERVICE);
        PendingIntent pStopSelf = PendingIntent.getBroadcast(this, 0, stopSelfIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wi-Fi Room Locator")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStopSelf)
                .build();
    }
}
