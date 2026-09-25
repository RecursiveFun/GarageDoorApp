# Geofence and Notification-Based Triggering System

This system enables automatic garage door triggering when approaching your garage using geofencing and location services, even when the app is closed.

## How It Works

1. **Geofence Setup**: The app creates a coarse geofence around your garage boundary
2. **Trigger Detection**: When you enter the geofence (approaching garage), it starts a foreground service
3. **Location Verification**: The foreground service checks GPS location for ~90 seconds to verify precise arrival
4. **Automatic Triggering**: If location is confirmed inside garage polygon, the door opens automatically
5. **Notification System**: While checking, a persistent notification shows progress

## Android 13 Compatibility Fixes

The system has been updated with specific fixes for Android 13's stricter foreground service requirements:

- Proper foreground service handling using `startForegroundService()` 
- Enhanced error handling for Android 13+ restrictions
- Improved wake lock management to prevent service termination
- Better location accuracy settings with `setWaitForAccurateLocation(true)`
- Robust notification system with proper channel configuration

## Key Features

### Reliable Background Operation
- Works even when app is closed
- Uses foreground services to prevent aggressive battery optimization
- Maintains location tracking for 90 seconds maximum

### User Experience
- Persistent notifications during checking process
- Clear status updates ("Checking arrival...", "Verifying departure...")
- Automatic door triggering without user interaction

### Security & Permissions
- Requires location permission (both coarse and fine)
- Proper foreground service type declaration
- Battery optimization handling

## Service Lifecycle

1. **Geofence ENTER** → `GeofenceReceiver` starts `LocationCheckService` with `startForegroundService()`
2. **Service Starts** → Shows persistent notification, acquires wake lock
3. **GPS Checks** → Continuously polls location for accuracy 
4. **Verification Complete** → Triggers garage door or stops service
5. **Service Stops** → Releases wake lock and removes notification

## Requirements

- Location permission (both coarse and fine)
- Foreground service permission  
- Battery optimization exclusion (recommended)