package com.example.wifiroomlocator;

public class Mapping {
    public String name;
    public int rssi;
    public String creator;

    // Firebase requires a no-argument constructor
    public Mapping() {}

    public Mapping(String name, int rssi, String creator) {
        this.name = name;
        this.rssi = rssi;
        this.creator = creator;
    }
}
