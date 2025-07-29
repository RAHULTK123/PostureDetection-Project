# Posture Detection Project

This repository contains both desktop and mobile applications for posture detection and monitoring.

## Project Structure

```
posture_detection_project/
├── desktop-app/          # Python desktop application
│   ├── main_2.py         # Main application file
│   ├── requirements.txt  # Python dependencies
│   └── README.md         # Desktop app documentation
│
└── mobile-app/           # Android mobile application
    ├── app/src/          # Android source code
    ├── build.gradle.kts  # Android build configuration
    └── .gitignore        # Android-specific gitignore
```

## Applications

### Desktop Application
- **Technology**: Python with Tkinter/GUI framework
- **Purpose**: Desktop posture monitoring application
- **Location**: `desktop-app/`
- **Documentation**: See `desktop-app/README.md`

### Mobile Application
- **Technology**: Android (Java)
- **Purpose**: Mobile companion app for posture tracking
- **Location**: `mobile-app/`
- **Features**: 
  - User authentication (Login/Register)
  - Posture data visualization
  - History tracking
  - Good posture image gallery

## Getting Started

### Desktop App
1. Navigate to `desktop-app/`
2. Install dependencies: `pip install -r requirements.txt`
3. Run the application: `python main_2.py`

### Mobile App
1. Navigate to `mobile-app/`
2. Open in Android Studio
3. Build and run on an Android device or emulator

## Firebase Integration
Both applications can integrate with Firebase for data synchronization and user authentication.

## Development
Each application maintains its own development environment and dependencies. See the respective README files in each directory for detailed setup instructions.
