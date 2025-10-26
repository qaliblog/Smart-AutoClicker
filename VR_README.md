# VR Magnet Clicker

This app has been modified to work as a VR magnet reader clicker. When the magnet of a VR headset is pulled down, the app performs a click in any VR app running in the background.

## Features

- **Background Operation**: The app runs continuously in the background and doesn't stop
- **Magnetometer Detection**: Detects changes in the magnetometer when VR headset magnet is pulled down
- **Smart Gesture Detection**: Distinguishes between pull-down and pull-up gestures to prevent double clicks
- **Accessibility Integration**: Uses Android's accessibility service to perform clicks
- **VR Settings**: Dedicated settings interface for configuring VR functionality

## How It Works

1. **Magnetometer Monitoring**: The app continuously monitors the device's magnetometer sensor
2. **Gesture Detection**: When a significant change in magnetic field is detected (indicating magnet pull-down), it triggers a click
3. **Click Execution**: Uses Android's accessibility service to perform a tap gesture at the center of the screen
4. **Cooldown Protection**: Implements a cooldown period to prevent multiple clicks from rapid magnet movements

## Setup Instructions

1. **Install the App**: Install the debug APK on your Android device
2. **Enable Accessibility Service**: 
   - Go to Settings > Accessibility > Smart Auto Clicker
   - Enable the service
3. **Disable Battery Optimization**:
   - Go to Settings > Apps > Smart Auto Clicker > Battery
   - Disable battery optimization
4. **Configure VR Settings**:
   - Open the app
   - Tap the VR headset icon in the top bar
   - Enable VR functionality
   - Test the click functionality

## Technical Details

### Components

- **VrMagnetometerService**: Background service that monitors magnetometer readings
- **VrClickManager**: Handles click execution using accessibility gestures
- **VrSettingsActivity**: UI for configuring VR functionality
- **SmartAutoClickerService**: Main accessibility service that integrates VR functionality

### Permissions

- `ACCESS_FINE_LOCATION`: Required for magnetometer access
- `FOREGROUND_SERVICE`: For background operation
- `SYSTEM_ALERT_WINDOW`: For accessibility service
- `WAKE_LOCK`: To keep the app active

### Gesture Detection

The app uses a simple heuristic to detect magnet pull-down gestures:
- Monitors changes in the Z-axis (vertical) magnetic field
- Triggers click when change exceeds threshold (default: 15.0f)
- Implements cooldown period (default: 1000ms) to prevent double clicks

## Building

The app includes a GitHub Actions workflow that builds debug APKs automatically on push to main branches.

## Troubleshooting

1. **Clicks not working**: Ensure accessibility service is enabled
2. **App stops in background**: Disable battery optimization
3. **No magnet detection**: Check if device has magnetometer sensor
4. **Too sensitive/not sensitive enough**: Adjust gesture threshold in VR settings

## Customization

You can customize the following parameters in the VR settings:
- Gesture threshold (sensitivity)
- Cooldown period between clicks
- Click position (currently center of screen)

## License

This project is licensed under the GNU General Public License v3.0 - see the LICENSE file for details.