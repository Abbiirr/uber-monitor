# Uber Monitor App - Complete System Documentation

## Table of Contents
1. [System Overview](#system-overview)
2. [Architecture & Data Flow](#architecture--data-flow)
3. [Component Breakdown](#component-breakdown)
4. [Data Persistence](#data-persistence)
5. [Permission System](#permission-system)
6. [Background Service Operation](#background-service-operation)
7. [UI Navigation Flow](#ui-navigation-flow)
8. [Common Issues & Solutions](#common-issues--solutions)
9. [Testing & Debugging Guide](#testing--debugging-guide)
10. [Future Integration Points](#future-integration-points)

---

## System Overview

### Purpose
The Uber Monitor app is an Android application that uses AccessibilityService to monitor ride-hailing apps (Uber, Pathao) and capture screenshots when specific UI elements are detected. It stores this data locally with a UI for users to review collected information.

### Key Components
- **AccessibilityService**: Background service monitoring app UI changes
- **MediaProjection**: Screen capture capability
- **Room Database**: Local storage for collected items
- **DataStore**: User profile persistence
- **Compose UI**: Modern Android UI for user interaction

### File Structure
```
app/src/main/java/com/example/uber_monitor/
├── MainActivity.kt                 # Entry point, navigation host
├── MediaProjectionSingleton.kt     # Global MediaProjection intent storage
├── UberAccessibilityService.kt     # Background monitoring service
├── data/
│   ├── Repository.kt              # Central data access layer
│   ├── UserPreferences.kt        # DataStore wrapper for user profile
│   └── db/
│       ├── AppDatabase.kt        # Room database instance
│       ├── CollectedItem.kt      # Data entity & constants
│       └── CollectedItemDao.kt   # Database operations
└── ui/
    ├── RegistrationScreen.kt     # User profile & permissions UI
    └── CollectedDataScreen.kt    # Data viewing & management UI

res/xml/
├── accessibility_service_config.xml  # Service configuration
├── file_paths.xml                   # FileProvider paths
└── data_extraction_rules.xml        # Backup rules
```

---

## Architecture & Data Flow

### Data Flow Diagram
```
[Uber/Pathao App UI Event]
           ↓
[UberAccessibilityService]
           ↓
    Detects target element
           ↓
[MediaProjection captures screen]
           ↓
    Saves PNG file
           ↓
[Repository.insertScreenshot()]
           ↓
[Room Database (CollectedItem)]
           ↓
[UI observes via Flow]
           ↓
[CollectedDataScreen displays]
```

### Component Relationships
1. **MainActivity** hosts Compose Navigation
2. **Navigation** decides initial screen based on `UserPreferences.onboardingCompleted`
3. **RegistrationScreen** writes to `UserPreferences` via `Repository`
4. **CollectedDataScreen** observes `CollectedItemDao` flows via `Repository`
5. **UberAccessibilityService** writes to database via `Repository.insertScreenshot()`
6. **MediaProjectionSingleton** stores permission across app lifecycle

---

## Component Breakdown

### 1. MainActivity.kt
**Purpose**: App entry point, hosts navigation, prompts for permissions

**Key Functions**:
- `onCreate()`: Sets up Compose UI with navigation
- `onResume()`: Checks and prompts for required permissions
- `MainNavigation()`: Determines start destination based on profile completion

**Dependencies**:
- Repository for checking user profile
- Navigation components for screen routing

### 2. UberAccessibilityService.kt
**Purpose**: Background service monitoring app events and capturing screenshots

**Lifecycle**:
- `onCreate()`: Initializes Repository and coroutine scope
- `onServiceConnected()`: Configures event types to monitor
- `onAccessibilityEvent()`: Processes UI events from monitored apps
- `onDestroy()`: Cleans up coroutines

**Key Methods**:
- `handleUberEvent()`: Processes Uber app events
- `findTargetNode()`: Searches for specific UI elements using XPath-like logic
- `triggerScreenshot()`: Uses MediaProjection to capture screen
- `saveBitmap()`: Saves image file and records in database

**Critical Variables**:
- `screenshotTaken`: Prevents duplicate captures for same UI state
- `serviceScope`: Coroutine scope for async database operations
- `repository`: Data access layer instance

### 3. Repository.kt
**Purpose**: Central data access layer, singleton pattern

**Key Functions**:
- `insertScreenshot()`: Records screenshot with timestamp and metadata
- `insertPayload()`: Records JSON payload (future feature)
- `getFilteredItems()`: Returns Flow based on filters
- `clearAllItems()`: Deletes all collected data

**Singleton Access**:
```kotlin
val repository = Repository.getInstance(context)
```

### 4. Room Database Components

#### AppDatabase.kt
- Database version: 1
- Tables: `collected_items`
- Singleton pattern with thread-safe initialization

#### CollectedItem.kt
**Entity Structure**:
```kotlin
id: Long (auto-generated)
timestamp: Long (milliseconds)
readableTimestamp: String ("yyyy-MM-dd HH:mm:ss")
sourceApp: String ("uber", "pathao", "unknown")
type: String ("screenshot", "payload")
filePath: String? (for screenshots)
payloadJson: String? (for payloads)
sendStatus: String ("pending", "sent", "failed")
notes: String?
```

**Indexes**:
- Single index on `timestamp`
- Composite index on `sourceApp, type`

#### CollectedItemDao.kt
- Insert operations (single/batch)
- Query operations with filters
- Delete operations (single/all)
- Returns Flow for reactive UI updates

### 5. UserPreferences.kt
**Purpose**: DataStore wrapper for user profile

**Stored Keys**:
- `user_name`: String
- `user_phone`: String
- `onboarding_completed`: Boolean

**Usage**:
```kotlin
// Save
userPreferences.saveUserProfile(name, phone)

// Observe
userPreferences.userProfile.collect { profile ->
    // React to changes
}
```

### 6. UI Screens

#### RegistrationScreen.kt
**Features**:
- Text fields with validation
- Permission status indicators
- MediaProjection permission request
- Navigation to data screen after saving

**Validation Rules**:
- Name: Non-empty, max 100 chars
- Phone: 8-15 digits

#### CollectedDataScreen.kt
**Features**:
- LazyColumn for item list
- Filter chips (source app, type)
- Item detail dialogs
- Share functionality via FileProvider
- Clear all with confirmation

**Item Actions**:
- Tap: Opens detail view
- Share: Exports via Intent.ACTION_SEND
- Delete: Removes from database
- Copy (payloads): Copies JSON to clipboard

---

## Data Persistence

### Storage Locations

1. **User Profile** (DataStore)
    - Location: `/data/data/com.example.uber_monitor/files/datastore/user_preferences.preferences_pb`

2. **Collected Items** (Room)
    - Database: `/data/data/com.example.uber_monitor/databases/uber_monitor_database`

3. **Screenshots** (External Files)
    - Path: `/storage/emulated/0/Android/data/com.example.uber_monitor/files/Pictures/Screenshots/`
    - Format: `screenshot_[sourceApp]_[timestamp].png`

### Data Lifecycle
- Screenshots persist until manually deleted
- Database entries persist across app restarts
- User profile persists until app data cleared

---

## Permission System

### Required Permissions

#### 1. Accessibility Service
**Check Method**:
```kotlin
Settings.Secure.getString(contentResolver, ENABLED_ACCESSIBILITY_SERVICES)
```
**Enable**: Settings → Accessibility → Uber Monitor

#### 2. Usage Access
**Check Method**:
```kotlin
AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, ...)
```
**Enable**: Settings → Apps & notifications → Special app access → Usage access

#### 3. MediaProjection (Screen Capture)
**Storage**: `MediaProjectionSingleton.projectionData`
**Request**: Via `MediaProjectionManager.createScreenCaptureIntent()`
**Persistence**: Only during app lifecycle (lost on app restart)

### Permission Flow
1. App checks permissions on resume
2. Shows prompts if missing
3. Registration screen shows status
4. Service only captures if MediaProjection available

---

## Background Service Operation

### Service Registration (AndroidManifest.xml)
```xml
<service android:name=".UberAccessibilityService"
         android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
```

### Event Processing Flow
1. System sends AccessibilityEvent to service
2. Service filters by package name (com.ubercab, com.pathao)
3. Searches UI tree for target elements
4. Triggers screenshot if found and not already captured
5. Saves file and database record asynchronously

### Target Element Detection
Two search strategies:
1. **XPath-like**: Find element with `resource-id="order_selection_order_cell"`, get 2nd child
2. **Instance-based**: Find 15th instance of `android.view.View`

### Coroutine Usage
- Scope: `SupervisorJob() + Dispatchers.IO`
- Database writes happen asynchronously
- Scope cancelled in `onDestroy()`

---

## UI Navigation Flow

### Navigation Graph
```
Start → Check UserProfile
         ↓
    [Not Complete] → RegistrationScreen
         ↓              ↓
    [Save Profile]    [View Data button if profile exists]
         ↓              ↓
    CollectedDataScreen ←
         ↑
    [Profile button]
```

### State Management
- Navigation state: NavController
- User profile: Flow from DataStore
- Collected items: Flow from Room
- Permissions: Checked on demand

---

## Common Issues & Solutions

### Issue 1: Screenshots Not Capturing
**Symptoms**: Service running but no screenshots saved

**Debug Steps**:
1. Check Logcat for "MediaProjection data unavailable!"
2. Verify `MediaProjectionSingleton.projectionData != null`
3. Re-request screen capture permission from Registration

**Solution**:
- Permission lost on app restart
- User must grant permission each session

### Issue 2: Service Not Detecting Elements
**Symptoms**: Uber app open but no screenshots

**Debug Steps**:
1. Check accessibility service enabled
2. Verify target app package name matches
3. Log UI tree structure in `onAccessibilityEvent`

**Solution**:
- UI may have changed; update element search logic
- Add more logging to identify new element IDs

### Issue 3: Database Not Updating
**Symptoms**: Screenshots saved but not showing in UI

**Debug Steps**:
1. Check coroutine scope active
2. Verify Repository singleton initialized
3. Check Room database file exists

**Solution**:
- Ensure service `onCreate()` initializes Repository
- Check for database migration issues

### Issue 4: Files Not Sharing
**Symptoms**: Share button crashes app

**Debug Steps**:
1. Verify FileProvider in manifest
2. Check file_paths.xml configuration
3. Confirm file exists at path

**Solution**:
- FileProvider authority must match: `${applicationId}.fileprovider`
- File must be in declared path

---

## Testing & Debugging Guide

### Manual Testing Checklist

#### Initial Setup
- [ ] Install app
- [ ] Grant all permissions
- [ ] Save user profile
- [ ] Verify navigation to data screen

#### Background Service
- [ ] Open Uber app
- [ ] Navigate to ride request screen
- [ ] Check screenshot captured
- [ ] Verify database entry created

#### Data Management
- [ ] Filter by source app
- [ ] Filter by type
- [ ] View item details
- [ ] Share screenshot
- [ ] Delete single item
- [ ] Clear all data

### ADB Commands
```bash
# Check accessibility service status
adb shell settings get secure enabled_accessibility_services

# Monitor service logs
adb logcat -s UberAccService:V

# Check database
adb shell run-as com.example.uber_monitor \
  sqlite3 databases/uber_monitor_database \
  "SELECT * FROM collected_items;"

# List screenshots
adb shell ls /storage/emulated/0/Android/data/com.example.uber_monitor/files/Pictures/Screenshots/
```

### Logcat Filters
```
UberAccService - Service events
TargetMatch - Element detection
Repository - Data operations
Navigation - Screen changes
```

---

## Future Integration Points

### 1. Backend Upload
**Current**: `sendStatus` field ready but unused

**Integration Point**:
```kotlin
// In Repository.kt
suspend fun uploadPendingItems() {
    val pending = collectedItemDao.getItemsBySendStatus("pending")
    pending.forEach { item ->
        try {
            // Upload to backend
            backendApi.upload(item)
            item.copy(sendStatus = "sent")
        } catch (e: Exception) {
            item.copy(sendStatus = "failed")
        }
    }
}
```

### 2. Payload Extraction
**Current**: Infrastructure ready for JSON payloads

**Integration Point**:
```kotlin
// In UberAccessibilityService
private fun extractPayloadFromNode(node: AccessibilityNodeInfo): String {
    // Extract text/data from UI
    val payload = buildJsonObject {
        put("trip_id", node.findText("trip_id"))
        put("fare", node.findText("fare"))
        // etc.
    }
    repository.insertPayload(payload.toString(), sourceApp)
}
```

### 3. Additional Apps
**Current**: Pathao partially implemented

**Integration Point**:
- Add package names to accessibility_service_config.xml
- Implement `handlePathaoEvent()` with specific element detection
- Add new SourceApp constants

### 4. Real-time Monitoring
**Current**: Local storage only

**Integration Point**:
- Add WebSocket/FCM for real-time updates
- Implement foreground service for continuous monitoring
- Add notification for capture events

### 5. Data Export
**Current**: Individual item sharing

**Integration Point**:
- Add bulk export to CSV/JSON
- Implement backup to Google Drive
- Add date range selection for export

---

## Maintenance Notes

### Adding New Fields to CollectedItem
1. Update entity in CollectedItem.kt
2. Increment database version in AppDatabase.kt
3. Add migration strategy
4. Update Repository insert methods
5. Update UI to display new fields

### Changing Target Elements
1. Update `findTargetNode()` in UberAccessibilityService
2. Test with actual app UI
3. Add fallback detection methods

### Performance Considerations
- LazyColumn handles large lists efficiently
- Flows provide reactive updates without polling
- Coroutines prevent blocking main thread
- Singleton pattern reduces object creation

### Security Considerations
- Screenshots contain sensitive information
- Files stored in app-specific directory
- No network transmission implemented yet
- User data never leaves device currently

---

## Contact Points for Issues

### Key Files to Check First
1. **Service not starting**: AndroidManifest.xml, accessibility_service_config.xml
2. **Data not saving**: Repository.kt, AppDatabase.kt
3. **UI not updating**: Flows in CollectedItemDao.kt
4. **Permissions failing**: MainActivity.kt, RegistrationScreen.kt
5. **Screenshots failing**: MediaProjectionSingleton.kt, UberAccessibilityService.kt

### Critical Log Tags
- `UberAccService`: Main service operations
- `Repository`: Data operations
- `MediaProjection`: Screen capture issues
- `Room`: Database operations
- `DataStore`: User preferences

This documentation should serve as a complete reference for understanding, debugging, and extending the Uber Monitor application.