# 🛸 Autonomous Line Follower UAV using DJI MSDK and OpenCV

### Android application for line identification using computer vision

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![MSDK](https://img.shields.io/badge/DJI_MSDK-V5.17.0-blue.svg)](https://developer.dji.com/)
[![OpenCV](https://img.shields.io/badge/OpenCV-4.12.0-orange.svg)](https://opencv.org/)

This Android application identifies lines using the integrated camera of DJI drones. It uses image processing with the OpenCV library implemented in Kotlin. The scope is limited to path identification in controlled conditions.

[Overview](#-overview) • [Features](#-features) • [Supported Drones](#-supported-drones) • [Installation](#-installation) • [Requirements](#-requirements) • [Permissions](#-permissions-androidmanifestxml) • [Usage Flow](#-application-usage-flow) • [Technical Setup](#-technical-configuration--troubleshooting)

---

## 📋 Overview

This project was created to help academic development in implementing **OpenCV** and **DJI Mobile SDK V5** in Android Studio. It is useful for implementing flight control functions based on image processing algorithms locally, without needing an external computer.

This project is a mix of:
- Research from the thesis: *"Cuatrirrotor seguidor de caminos basado en visión computacional y red neuronal convolucional"*.
- Example codes from GitHub.
- Personal experience using OpenCV and Kotlin.

## ✨ Features

- **Real-time Detection**: Identifies lines using the drone's camera.
- **Local Processing**: All calculations are done on the mobile device.
- **Academic Focus**: Designed for students and researchers.

## 🚁 Supported Drones

* `Matrice 350 RTK`
* `Matrice 300 RTK`
* `DJI Mini 4 Pro`
* `DJI Mini 3`
* `DJI Mini 3 Pro`
* `DJI Mavic 3 Enterprise Series`
* `DJI Mavic 3M`
* `DJI Mavic 3TA`
* `Matrice 30 Series`
* `Matrice 4E/4T`
* `Matrice 4D/4TD`
* `Matrice 400`

## 🚀 Installation

All library requirements are implemented in the `\app\build.gradle` file, including OpenCV and DJI MSDK via Maven. You do not need to download external files.

### 1. Android Studio
Install Android Studio (latest recommended). Follow the installation guide and try to compile a blank app first.
[Download Android Studio](https://developer.android.com/studio/install)

### 2. Clone the Repository
```bash
git clone https://github.com/FenixTags/dji-msdk-artificial-vision.git
```

### 3. Register as DJI Developer
You need a Developer Key to use the DJI SDK:
1. Register at: [https://account.dji.com/register](https://account.dji.com/register)
2. Go to the [Developer Center](https://developer.dji.com/user) to apply for an App Key.
3. Use the following parameters for the App Key:
    - **App Type**: `Mobile SDK`
    - **App Name**: `sampleV5aircraft`
    - **Software Platform**: `Android`
    - **Package Name**: `com.dji.sampleV5.aircraft`

### 4. Create gradle.properties
In the root of the project (`\`), create a file named `gradle.properties` and paste this content:

```properties
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official

# Android Operating System Versions
ANDROID_MIN_SDK_VERSION=24
ANDROID_TARGET_SDK_VERSION=34
ANDROID_COMPILE_SDK_VERSION=34

# Programming Language Version
KOTLIN_VERSION=1.8.10

# DJI Mobile SDK V5 Version
SDK_VERSION=5.17.0

# Your DJI App Key
AIRCRAFT_API_KEY=your_api_key_here
```

### 5. Project Setup
Open Android Studio, click on **Open**, and select the `\sampleV5aircraft` folder. Wait for the Gradle sync to complete.

## 🏗️ Project Structure

```
sampleV5aircraft/
├── app/
│    ├── manifest/
│    ├── kotlin+java/
│    ├── jniLibs/
│    └── res/
└── Gradle Scripts/
    ├── build.gradle (Project: sampleV5aircraft)
    ├── build.gradle (Module: app)
    ├── gradle.properties
    └── settings.gradle
```

## 📋 Requirements

### Prerequisites
- **Android Studio**: Ladybug | 2024.2.1 or newer.
- **Android SDK**: API 34 (Android 14).
- **Java Runtime**: 1.8.
- **Kotlin**: 1.8.10.
- **MSDK Version**: 5.17.0.
- **Android Gradle Plugin**: 8.7.0.

### Physical Devices
- **Android Hardware**: USB-C data connection port.
- **Compatible Drone**: (See the supported list above).
- **Controller**: DJI RC-N1 or newer.
- **Minimum RAM**: 6GB.
- **Storage**: 4GB free space.
- **CPU**: Snapdragon 8 Gen, Dimensity 9500 or superior.

## 🔒 Permissions (AndroidManifest.xml)
The app declares several permissions necessary for the flight ecosystem:
- **RC Communication**: `BLUETOOTH` and `BLUETOOTH_ADMIN`.
- **SDK Activation**: `INTERNET` and `ACCESS_NETWORK_STATE` are needed to validate your API Key with DJI servers.
- **Drone Indexing**: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. DJI SDK uses these to find and link the aircraft.
- **Data Handling**: `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (Legacy) and `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (Android 13+) for logs and media.
- **Auto-Launch**: `android.hardware.usb.accessory`. This allows the app to open automatically the moment you plug in the Remote Controller.



## 🔄 Application Usage Flow

When you deploy the application, it follows a specific sequence of states defined in `MainActivity.kt` and `VisionProcessor.kt`:

1. **Physical Handshake**:
    - You connect the Remote Controller (RC) to the smartphone via USB-C.
    - The phone detects the hardware and opens the app via the `USB_ACCESSORY_ATTACHED` intent.
2. **Permission Validation**:
    - `MainActivity` checks the `vitalPermissions` array.
    - You must accept all pop-ups. If any are denied, the status monitor will show: *"Error: Permisos insuficientes."*
3. **SDK Initialization**:
    - The app calls `SDKManager.getInstance().init()`.
    - Status: *"Iniciando motor DJI SDK..."*
4. **License Registration**:
    - The app sends your `AIRCRAFT_API_KEY` to DJI.
    - Once the license is active, `onRegisterSuccess()` triggers.
    - Status: *"SDK Registrado. Esperando conexión..."*
5. **Hardware Link**:
    - You turn on the drone. When the RC and Drone link, `onProductConnect()` fires.
    - Status: *"Hardware conectado."*
6. **Camera Synchronization**:
    - `hardwareCameraListener` waits for a camera stream index.
    - Once detected, Status: *"Módulo: Cámara Raw (Activo)"*.
7. **Computer Vision Execution**:
    - When switching to **Filtered View**:
    - `visionProcessor.startListening()` attaches a frame listener to the `MediaDataCenter`.
    - **Step A**: `onFrame` receives raw YUV data from the drone.
    - **Step B**: OpenCV (`Imgproc.cvtColor`) converts data to RGBA.
    - **Step C**: `colorDetectionCMSS` identifies the line and calculates the **Center of Mass (CMSS)**.
    - **Step D**: The `mainThreadHandler` pushes the final image to the screen.


## 🏗️ Technical Configuration & Troubleshooting

### 1. IDE and Language Versions
To avoid compilation errors, the project must be configured with specific versions:
- **Kotlin Compiler**: This project is built with **Kotlin 1.8.10**. Even if Android Studio prompts you to upgrade to Kotlin 2.x or 1.9, please keep **1.8.10** in your `Settings > Languages & Frameworks > Kotlin` to ensure compatibility with DJI's native metadata.
- **Gradle DSL (Groovy)**: We use **Groovy DSL** (`build.gradle`) instead of Kotlin DSL (`.kts`). This is intentional to maintain full compatibility with the DJI MSDK V5 build system and avoid issues with native library indexing.
- **Gradle Version**: The project uses **Gradle 8.7** and **AGP 8.7.0**.


<div align="center">

**Built with ❤️ for DJI Academic Development**

</div>
