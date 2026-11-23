# Code Analysis: MainActivity.kt

## 🚀 High-Level Overview
This code is the main entry point for an Android application named **Race For The Galaxy Helper** (`[MainActivity](class://com.example.raceforthegalaxyhelper.MainActivity)`). It serves as a multiplayer, peer-to-peer helper utility designed to assist players in the board game *Race for the Galaxy*. Its primary function is to allow players to coordinate actions, track turns, and manage game state across different devices (Host/Master and Client) over a local network.

The app functions as a real-time multiplayer controller:
1. **Mode Selection (`ModeSelectorScreen`):** Users first choose whether to start as the **Master/Host** or **Join** an existing game.
2. **Networking:** It uses **UDP broadcasts and services** (`[NetworkService](class://com.example.raceforthegalaxyhelper.NetworkService)`) to discover other players (the Master IP) and synchronize the game state (actions, turns, colors).
3. **Game Play (`GameScreen`):** Players can select a predefined game action (e.g., "I.EXPLORE, Draw/Keep: 2/1, +1/+1") which is then broadcasted. The Master aggregates these actions and signals when it's time to clear actions and advance the turn.
4. **Localization & UI:** It supports multiple languages (`English` and `Hungarian`) and includes visual indicators for player roles, turns, and chosen actions.

## 🧩 Key Components and Data Structures

### 🌍 Localization and Game Actions
The `[AppStrings](class://com.example.raceforthegalaxyhelper.AppStrings)` data class provides a robust localization layer.

*   **`ActionItem`**: Defines a specific game action and the critical target part for the UI.
*   **`Language` Enum**: Provides `AppStrings` instances for `EN` and `HU`, allowing the UI to dynamically render text based on the selected language.

### 🚦 State Variables
The `MainActivity` holds extensive mutable state, driving the UI:
*   `currentLanguage`: Tracks the currently active language.
*   `appMode`: Determines the app's role in the network session (`"SELECT"`, `"MASTER"`, or `"CLIENT"`).
*   `currentTurn`: Tracks the current game turn.
*   `playerActions`: A list storing the chosen action index for each player.
*   `connectionStatus`: Displays the network connection state.
*   `discoveredMasterIp`: Stores the IP address of the host found via UDP broadcast discovery.

## ⚙️ Core Workflow Functions

### 1. Initialization (`[onCreate](method://com.example.raceforthegalaxyhelper.MainActivity#onCreate)`)
The activity initializes settings, obtains the device's local IP address (`[getLocalIpAddress](method://com.example.raceforthegalaxyhelper.MainActivity#getLocalIpAddress)`), and begins listening for network events via `[NetworkEventBus](class://com.example.raceforthegalaxyhelper.NetworkEventBus)`.

### 2. Mode Selection (`[ModeSelectorScreen](Composable function in MainActivity.kt)`)
The starting screen allows users to choose between Master/Host and Client/Join.
*   **Start as Host:** Calls `[startNativeMasterServer](method://com.example.raceforthegalaxyhelper.MainActivity#startNativeMasterServer)` and starts UDP broadcasts (`[startUdpBroadcast](method://com.example.raceforthegalaxyhelper.MainActivity#startUdpBroadcast)`).
*   **Join Game:** Triggers UDP discovery (`[startUdpDiscovery](method://com.example.raceforthegalaxyhelper.MainActivity#startUdpDiscovery)`). Once a Master IP is found, it calls `[startNativeClient](method://com.example.raceforthegalaxyhelper.MainActivity#startNativeClient)` to connect.

### 3. Game Play (`[GameScreen](Composable function in MainActivity.kt)`)
This is the active gameplay UI.
*   **Action Selection:** Players use a `LazyVerticalGrid` to select one of the predefined game actions. Calling `[selectAction](method://com.example.raceforthegalaxyhelper.MainActivity#selectAction)` updates the local state and broadcasts the selection.
*   **Master Logic:** The Master reveals actions when all players are ready, handled by a timed delay.
*   **Turn Advancement:** After actions are revealed, the Master clears the actions and increments `currentTurn`.

## 📡 Networking and Synchronization
The networking logic uses both foreground services and UDP:
*   **State Synchronization:** Synchronization is handled by sending messages via a `ForegroundService`.
*   **UDP Discovery:** The Master broadcasts and Clients listen on UDP port 8888 to find the Master.
*   **State Handling (`[handleIncomingMessage](method://com.example.raceforthegalaxyhelper.MainActivity#handleIncomingMessage)`):** This is the core state machine that parses structured, comma-separated strings sent from peers to update the local game state (e.g., `currentTurn`, `revealed` status, player actions, and background color indices).

## ✨ User Experience (UX) Features
*   **Audio Feedback:** The app implements `[playClickSound](method://com.example.raceforthegalaxyhelper.MainActivity#playClickSound)` for UI interactions and `[playSynthesizedSound](method://com.example.raceforthegalaxyhelper.MainActivity#playSynthesizedSound)` for signaling significant game state changes.
*   **Haptics:** It integrates `[LocalHapticFeedback]` for tactile confirmation.
*   **Visuals:** It uses a custom composable `[getAnnotatedActionString](method://com.example.raceforthegalaxyhelper.MainActivity#getAnnotatedActionString)` to highlight specific action names and targets within the UI.