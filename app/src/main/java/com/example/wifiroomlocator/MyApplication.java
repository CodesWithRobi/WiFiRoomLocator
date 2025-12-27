package com.example.wifiroomlocator;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Enable Firebase offline persistence before any other Firebase calls
        FirebaseDatabase.getInstance("https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/").setPersistenceEnabled(true);
    }
}
