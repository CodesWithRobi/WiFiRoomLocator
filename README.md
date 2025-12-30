# WiFi Room Locator

A social indoor positioning Android application built for a PEC project. It uses crowdsourced WiFi RSSI fingerprinting to identify rooms within a building and shares users' locations with their friends in real-time.

## About

This project moves beyond traditional GPS, which fails indoors. It leverages the unique signal strength signatures (RSSI) of surrounding WiFi access points to create a "fingerprint" for each room. 

The core innovation is its crowdsourced mapping system. When one user maps a room (e.g., "Lab 101"), that data is uploaded to a global Firebase Realtime Database. Subsequently, any other user of the app who enters that room will instantly see its correct name. The app is built around a social graph, allowing users to add friends and see their current indoor location update in real-time, creating a dynamic social map of the building.

## Features

- **One-Tap Google Sign-In:** Secure and easy authentication using the latest Android Credential Manager API.
- **Non-Destructive User Creation:** User profiles are created in Firebase only on their first login, preserving their friend list and other data on subsequent sessions.
- **Crowdsourced Room Mapping:** New rooms are fingerprinted and added to a global Firebase database. The system is robust against race conditions, preventing existing user-defined names from being overwritten by the auto-discovery mechanism.
- **Real-Time Location Detection:** The app continuously scans WiFi signals to identify the user's current room based on the global fingerprint database.
- **Real-Time Location Sharing:** A user's current detected location is instantly updated in Firebase, allowing friends to see where they are in real-time.
- **Proximity Awareness:** Displays the user's proximity ("At Center", "Near", "Far Away") to the center of a mapped room based on RSSI signal offset.
- **Complete & Robust Friend System:**
    - Find other users in a dedicated 'Find Friends' screen.
    - Send, Accept, and Decline friend requests.
    - Remove friends via a long-press confirmation dialog.
    - All social operations (accepting, deleting) use **atomic multi-path Firebase updates** to ensure data consistency and prevent partial states.
- **Offline Persistence:** Firebase offline capabilities ensure the app remains functional even during temporary network disruptions.
- **Targeted Scanning:** Includes a hardcoded filter to only recognize and map networks with a specific name (e.g., "sec"), perfect for a controlled demo environment.

## Requirements

*   Android Studio & Java
*   Firebase Realtime Database (asia-southeast1)
*   Firebase Authentication
*   Android Credential Manager API with Google Sign-In
*   Android SDK (especially `WifiManager`)
*   AndroidX Libraries (`AppCompat`, `RecyclerView`, `ViewPager`, `CoordinatorLayout`)
*   Google Material Components

## System Architecture

The application relies on a client-server architecture with the Android app as the client and Firebase as the backend.

### Firebase Realtime Database Schema

```json
{
  "mappings": {
    // Contains WiFi fingerprints (BSSID -> Room Name, RSSI, Creator)
    "BSSID_WITHOUT_COLONS": {
      "name": "Library",
      "rssi": -55,
      "creator": "UID_123"
    }
  },
  "users": {
    // Stores user profile information
    "UID_123": {
      "name": "John Doe",
      "email": "john@pec.edu",
      "currentLocation": "Library",
      "friends": {
        "UID_456": true
      }
    }
  },
  "friendRequests": {
    // Manages pending friend requests
    "TARGET_UID": {
      "SENDER_UID": "pending"
    }
  }
}
```

## Output

#### Output 1 - Real-Time Scanning and Location Detection

<img width="610" alt="HOME" src="https://github.com/user-attachments/assets/dde9885b-a554-4db0-8fb9-31f6ac3bfb3f" />

#### Output 2 - Live Friends List with Locations

<img width="610" alt="Friends" src="https://github.com/user-attachments/assets/802c18c9-1fcd-424c-baab-3c9e27bdb356" />

#### Output 3 - Finding and Adding New Friends

<img width="610" alt="Discover" src="https://github.com/user-attachments/assets/978d7795-4290-4b33-b921-f33612a677ef" />
<img width="610" alt="Requests" src="https://github.com/user-attachments/assets/3d03506d-5df0-4903-823d-487547ebbc73" />

## Results and Impact

The WiFi Room Locator (Social Edition) successfully demonstrates a practical and scalable solution for indoor positioning where GPS is unavailable. By crowdsourcing the fingerprinting process, the system becomes more accurate and useful as more people use it. The real-time social features create an engaging experience, perfect for a campus or office environment. This project serves as a strong foundation for future development in indoor navigation, location-based services, and social networking.
