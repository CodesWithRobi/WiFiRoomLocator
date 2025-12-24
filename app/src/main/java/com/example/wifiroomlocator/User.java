package com.example.wifiroomlocator;

import java.util.HashMap;
import java.util.Map;

public class User {
    public String uid;
    public String name;
    public String currentLocation;
    public Map<String, Boolean> friends; // Stores Friend UIDs

    public User() {} // Required for Firebase

    public User(String uid, String name) {
        this.uid = uid;
        this.name = name;
        this.currentLocation = "Unknown Area";
        this.friends = new HashMap<>();
    }
}
