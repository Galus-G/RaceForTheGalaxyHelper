package com.example.raceforthegalaxyhelper

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Collections

data class ActionItem(
    val name: String,
    val targetPart: String
)

data class AppStrings(
    val appTitle: String,
    val startAsHost: String,
    val joinGame: String,
    val searchingForHost: String,
    val connectToHost: (String) -> String,
    val portLabel: String,
    val yourIp: (String) -> String,
    val backgroundFormat: (String) -> String,
    val backgroundTapFormat: (String) -> String,
    val turnFormat: (Int) -> String,
    val reveal: String,
    val clearAndNext: String,
    val waitingForHost: String,
    val playerFormat: (Int, String) -> String,
    val playerTapFormat: (Int, String) -> String,
    val colors: List<String>,
    val actions: List<ActionItem>
)

enum class Language(val code: String, val displayName: String) {
    EN("en", "English"),
    HU("hu", "Magyar");
    // Easily extensible for more languages in the future!

    val strings: AppStrings
        get() = when (this) {
            EN -> AppStrings(
                appTitle = "Race For The Galaxy Helper",
                startAsHost = "START AS HOST",
                joinGame = "JOIN GAME",
                searchingForHost = "Searching for Host...",
                connectToHost = { "CONNECT TO HOST ($it)" },
                portLabel = "Port",
                yourIp = { "Your IP: $it" },
                backgroundFormat = { "Player: $it" },
                backgroundTapFormat = { "Player: $it (Tap to change)" },
                turnFormat = { "Turn: $it" },
                reveal = "REVEAL",
                clearAndNext = "CLEAR & NEXT",
                waitingForHost = "Waiting for Host to clear...",
                playerFormat = { pIdx, bgName -> "Player $pIdx: $bgName" },
                playerTapFormat = { pIdx, bgName -> "Player $pIdx: $bgName (Tap to change)" },
                colors = listOf("Yellow", "Red", "Blue", "Green"),
                actions = listOf(
                    ActionItem("I.EXPLORE, Draw/Keep: 2/1, +1/+1", "+1/+1"),
                    ActionItem("I.EXPLORE, Draw/Keep: 2/1, +5/0", "+5/0"),
                    ActionItem("II.DEVELOP, Play development, Cost -1", "Cost -1"),
                    ActionItem("III.SETTLE, Play world, draw 1 card after", "draw 1 card after"),
                    ActionItem("IV.TRADE 1 card for product -> consume", "1 card for product"),
                    ActionItem("IV.CONSUME products on IV, 2xVP!", "2xVP!"),
                    ActionItem("V.PRODUCE, Produce on Windfall world", "Produce on Windfall world")
                )
            )
            HU -> AppStrings(
                appTitle = "Race For The Galaxy Segéd",
                startAsHost = "INDÍTÁS HOSTKÉNT",
                joinGame = "CSATLAKOZÁS JÁTÉKHOZ",
                searchingForHost = "Host keresése...",
                connectToHost = { "CSATLAKOZÁS A HOSTHOZ ($it)" },
                portLabel = "Port",
                yourIp = { "IP-címed: $it" },
                backgroundFormat = { "Játékos: $it" },
                backgroundTapFormat = { "Játékos: $it (Koppints a váltáshoz)" },
                turnFormat = { "Kör: $it" },
                reveal = "FELLEBBENTÉS",
                clearAndNext = "TÖRLÉS ÉS KÖVETKEZŐ",
                waitingForHost = "Várakozás a Host általi törlésre...",
                playerFormat = { pIdx, bgName -> "Játékos $pIdx: $bgName" },
                playerTapFormat = { pIdx, bgName -> "Játékos $pIdx: $bgName (Koppints a váltáshoz)" },
                colors = listOf("Sárga", "Piros", "Kék", "Zöld"),
                actions = listOf(
                    ActionItem("I. FELFEDEZÉS, Húzz/tarts meg: 2/1, +1/+1", "+1/+1"),
                    ActionItem("I. FELFEDEZÉS, Húzz/tarts meg: 2/1, +5/0", "+5/0"),
                    ActionItem("II. FEJLESZTÉS, Játssz ki egyet, ára -1", "ára -1"),
                    ActionItem("III. VILÁG, Játssz ki egyet, utána húzz 1 kártyát", "utána húzz 1 kártyát"),
                    ActionItem("IV. KERESKEDÉS, 1 termékért kártya -> FOGYASZTÁS", "1 termékért kártya"),
                    ActionItem("IV. FOGYASZTÁS, termékek elköltése IV-nél, 2xGYP!", "2xGYP!"),
                    ActionItem("V. TERMELÉS, Termelj potyavilágon.", "Termelj potyavilágon.")
                )
            )
        }
}

class MainActivity : ComponentActivity() {

    // Multi-Language State
    private var currentLanguage by mutableStateOf(Language.EN)

    // Game State
    private var currentTurn by mutableIntStateOf(1)
    private var revealed by mutableStateOf(false)
    private val playerActions = mutableStateListOf<Int?>(null, null, null, null) // 0 is Master, 1-3 Clients

    // Network State
    private var appMode by mutableStateOf("SELECT") // SELECT, MASTER, CLIENT
    private var isNetworkModeEnabled by mutableStateOf(true)
    private var connectionStatus by mutableStateOf("Disconnected")
    private var ipAddress by mutableStateOf("")
    private var networkPort by mutableIntStateOf(8080)
    
    private val slotToIp = mutableStateListOf<String?>(null, null, null, null)
    private var myPlayerIndex by mutableIntStateOf(0)

    private var udpSocket: DatagramSocket? = null
    private var isUdpRunning = false
    private var discoveredMasterIp by mutableStateOf<String?>(null)

    // Background Color Selector State
    private val backgroundColors = listOf(
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFF44336), // Red
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50)  // Green
    )
    private val backgroundNames = listOf("Yellow", "Red", "Blue", "Green")
    private var backgroundIndex by mutableIntStateOf(0)
    private val playerBgIndices = mutableStateListOf(0, 0, 0, 0)

    private fun savePort(port: Int) {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("network_port", port)
            .apply()
    }

    private fun saveBgIndex(index: Int) {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("bg_color_index", index)
            .apply()
    }

    private fun saveLanguage(lang: Language) {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("language_code", lang.code)
            .apply()
    }

    private fun changeBackgroundColor() {
        playClickSound()
        backgroundIndex = (backgroundIndex + 1) % backgroundColors.size
        saveBgIndex(backgroundIndex)
        playerBgIndices[myPlayerIndex] = backgroundIndex
        if (appMode == "CLIENT") {
            sendNetworkMessage("CMD_BG_INDEX:$backgroundIndex")
        } else if (appMode == "MASTER") {
            broadcastState()
        }
    }

    private fun playSynthesizedSound(frequency: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val durationSeconds = 1.8
                val numSamples = (sampleRate * durationSeconds).toInt()
                val samples = ShortArray(numSamples)
                
                val decayRate = 5.0
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = Math.exp(-decayRate * t)
                    val sine1 = Math.sin(1.0 * Math.PI * frequency * t)
                    val sine2 = 0.3 * Math.sin(2.0 * Math.PI * (frequency * 1.5) * t)
                    val echo = if (i > sampleRate * 0.15) 0.2 * Math.sin(2.0 * Math.PI * frequency * (t - 0.15)) else 0.0
                    
                    val sample = (32767.0 * envelope * (sine1 + sine2 + echo) / 1.5).toInt().coerceIn(-32768, 32767)
                    samples[i] = sample.toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                
                delay(900)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e("Sound", "Error playing sound: ${e.message}")
            }
        }
    }

    private fun playClickSound() {
        try {
            val sampleRate = 22050
            val durationSeconds = 0.04
            val numSamples = (sampleRate * durationSeconds).toInt()
            val samples = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = Math.exp(-80.0 * t)
                val noise = (Math.random() * 2.0 - 1.0)
                val sine = Math.sin(1.0 * Math.PI * 600.0 * t)
                val sample = (16384.0 * envelope * (0.5 * sine)).toInt().coerceIn(-32768, 32767)
                samples[i] = sample.toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
        } catch (e: Exception) {
            Log.e("Sound", "Error playing click sound: ${e.message}")
        }
    }

    private fun getAnnotatedActionString(actionItem: ActionItem, isSelected: Boolean, isRevealed: Boolean): AnnotatedString {
        val targetPart = actionItem.targetPart
        val actionName = actionItem.name

        val baseColor = if (isSelected) Color.White else Color.LightGray
        val redColor = Color(0xFFFF5252) // Vibrant red
        val fontStyle = if (isSelected && !isRevealed) FontStyle.Italic else FontStyle.Normal

        return buildAnnotatedString {
            val startIndex = actionName.indexOf(targetPart)
            if (startIndex != -1) {
                withStyle(SpanStyle(color = baseColor, fontStyle = fontStyle)) {
                    append(actionName.substring(0, startIndex))
                }
                withStyle(SpanStyle(color = redColor, fontWeight = FontWeight.Bold, fontStyle = fontStyle)) {
                    append(targetPart)
                }
                withStyle(SpanStyle(color = baseColor, fontStyle = fontStyle)) {
                    append(actionName.substring(startIndex + targetPart.length))
                }
            } else {
                withStyle(SpanStyle(color = baseColor, fontStyle = fontStyle)) {
                    append(actionName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        networkPort = prefs.getInt("network_port", 8080)
        backgroundIndex = prefs.getInt("bg_color_index", 0)
        playerBgIndices[0] = backgroundIndex

        val savedLangCode = prefs.getString("language_code", "en")
        currentLanguage = Language.entries.find { it.code == savedLangCode } ?: Language.EN

        ipAddress = getLocalIpAddress(this)

        lifecycleScope.launch {
            NetworkEventBus.events.collect { event ->
                handleNetworkEvent(event.event, event.remoteId, event.message)
            }
        }

        setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 1f 
                )
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                        when (appMode) {
                            "SELECT" -> ModeSelectorScreen()
                            "MASTER" -> GameScreen(isMaster = true)
                            "CLIENT" -> GameScreen(isMaster = false)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LanguageSelector() {
        val haptic = LocalHapticFeedback.current
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Language.entries.forEach { lang ->
                val isSelected = currentLanguage == lang
                Button(
                    onClick = {
                        playClickSound()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentLanguage = lang
                        saveLanguage(lang)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF00D2FF) else Color(0xFF1E293B)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = lang.displayName.uppercase(),
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    @Composable
    fun ModeSelectorScreen() {
        val haptic = LocalHapticFeedback.current
        var isSearching by remember { mutableStateOf(false) }
        val strings = currentLanguage.strings

        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.End
            ) {
                LanguageSelector()
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    strings.appTitle,
                    fontSize = 28.sp, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        playClickSound()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        startNativeMasterServer()
                        startUdpBroadcast()
                        appMode = "MASTER"
                        myPlayerIndex = 0
                        slotToIp[0] = "MASTER_IP"
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.startAsHost, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isSearching) {
                    Button(
                        onClick = {
                            playClickSound()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isSearching = true
                            startUdpDiscovery()
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D2FF),
                            disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = strings.joinGame,
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.White
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (discoveredMasterIp == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF00D2FF))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(strings.searchingForHost, color = Color.White, fontSize = 16.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    playClickSound()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startNativeClient(discoveredMasterIp!!)
                                    appMode = "CLIENT"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(strings.connectToHost(discoveredMasterIp!!), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                var portText by remember { mutableStateOf(networkPort.toString()) }

                OutlinedTextField(
                    value = portText,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() } && it.length <= 5) {
                            portText = it
                            it.toIntOrNull()?.let { p -> 
                                if (p in 1..65535) {
                                    networkPort = p
                                    savePort(p)
                                }
                            }
                        }
                    },
                    label = { Text(strings.portLabel, color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        disabledBorderColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                Text(strings.yourIp(ipAddress), color = Color.Gray)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 74.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("v1.1.0", color = Color.Gray, fontSize = 12.sp)
                Text("by Galus", color = Color.Gray, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(backgroundColors[backgroundIndex])
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        changeBackgroundColor()
                    },
                contentAlignment = Alignment.Center
            ) {
                val colorName = strings.colors.getOrElse(backgroundIndex) { backgroundNames[backgroundIndex] }
                Text(
                    text = strings.backgroundTapFormat(colorName),
                    color = if (backgroundColors[backgroundIndex] == Color(0xFFFFEB3B)) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    fun GameScreen(isMaster: Boolean) {
        val haptic = LocalHapticFeedback.current
        val strings = currentLanguage.strings

        var previousRevealed by remember { mutableStateOf(revealed) }

        LaunchedEffect(revealed) {
            if (revealed && !previousRevealed) {
                //playSynthesizedSound(880.0)
            } else if (!revealed && previousRevealed) {
                playSynthesizedSound(440.0)
            }
            previousRevealed = revealed
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    playClickSound()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    resetToMainMenu()
                }) {
                    Text("X", fontSize = 24.sp, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                }
                Text(strings.turnFormat(currentTurn), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageSelector()
                    Text(connectionStatus, color = if (connectionStatus == "Connected") Color.Green else Color.Red, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Player Status
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                playerActions.forEachIndexed { index, action ->
                    val isConnected = if (index == 0) true else slotToIp[index] != null
                    val isMe = index == myPlayerIndex
                    
                    val lampColor = when {
                        !isConnected -> Color.DarkGray
                        action != null -> Color(0xFF22C55E) // Vibrant green up a notch
                        isMe -> Color(0xFF00D2FF) // Light Blue
                        else -> Color.Gray
                    }

                    val playerBgColor = if (isConnected) backgroundColors[playerBgIndices.getOrElse(index) { 0 }] else Color.Transparent

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(lampColor, RoundedCornerShape(20.dp))
                                .border(9.dp, playerBgColor, RoundedCornerShape(30.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Selection
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(strings.actions) { index, actionItem ->
                    val isSelectedByMe = playerActions[myPlayerIndex] == index
                    val isSelectedByOthers = revealed && playerActions.indices.any { it != myPlayerIndex && playerActions[it] == index }
                    
                    val bgColor = Color(0xFF1E293B)
                    
                    val borderColor = when {
                        revealed && isSelectedByMe -> backgroundColors[playerBgIndices.getOrElse(myPlayerIndex) { 0 }]
                        revealed && isSelectedByOthers -> Color.White
                        else -> Color.Transparent
                    }
                    
                    val borderWidth = if (revealed && (isSelectedByMe || isSelectedByOthers)) 3.dp else 0.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = !revealed) {
                                selectAction(index)
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = getAnnotatedActionString(actionItem, isSelectedByMe, revealed),
                                fontWeight = if (revealed && isSelectedByMe) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (revealed) {
                                val selectorPlayerIndices = mutableListOf<Int>()
                                playerActions.forEachIndexed { pIdx, aIdx ->
                                    if (aIdx == index) selectorPlayerIndices.add(pIdx)
                                }
                                if (selectorPlayerIndices.isNotEmpty()) {
                                    Text(
                                        text = buildAnnotatedString {
                                            selectorPlayerIndices.forEachIndexed { sIdx, pIdx ->
                                                if (sIdx > 0) append(", ")
                                                val playerColor = backgroundColors[playerBgIndices.getOrElse(pIdx) { 0 }]
                                                withStyle(SpanStyle(color = playerColor, fontWeight = FontWeight.Bold)) {
                                                    append("${pIdx + 1}")
                                                }
                                            }
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isMaster) {
                val allConnectedSelected = playerActions.indices.all { i ->
                    val isConnected = if (i == 0) true else slotToIp[i] != null
                    if (isConnected) playerActions[i] != null else true
                }

                var lastRevealClickTime by remember { mutableLongStateOf(0L) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (!revealed) {
                        Button(
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastRevealClickTime > 1000L) {
                                    lastRevealClickTime = currentTime
                                    revealed = true
                                    broadcastState()
                                }
                            },

                            enabled = allConnectedSelected,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(strings.reveal)
                            if (allConnectedSelected) {
                            playSynthesizedSound(880.0)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                clearActions()
                                currentTurn++
                                broadcastState()
                            },
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                        ) {
                            Text(strings.clearAndNext, color = Color.White)
                        }
                    }
                }
            } else {
                if (revealed) {
                    Text(strings.waitingForHost, color = Color.Yellow)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isTurnOne = currentTurn == 1
            val playerColorName = strings.colors.getOrElse(backgroundIndex) { backgroundNames[backgroundIndex] }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(backgroundColors[backgroundIndex], RoundedCornerShape(12.dp))
                    .then(
                        if (isTurnOne) {
                            Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                changeBackgroundColor()
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bannerText = if (isTurnOne) {
                    strings.playerTapFormat(myPlayerIndex + 1, playerColorName)
                } else {
                    strings.playerFormat(myPlayerIndex + 1, playerColorName)
                }
                Text(
                    text = bannerText,
                    color = if (backgroundColors[backgroundIndex] == Color(0xFFFFEB3B)) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }

    private fun selectAction(index: Int) {
        if (revealed) return
        playClickSound()
        val newAction = if (playerActions[myPlayerIndex] == index) null else index
        playerActions[myPlayerIndex] = newAction
        
        if (appMode == "CLIENT") {
            sendNetworkMessage("CMD_SELECT_ACTION:$index")
        } else {
            broadcastState()
        }
    }

    private fun clearActions() {
        revealed = false
        for (i in playerActions.indices) {
            playerActions[i] = null
        }
    }

    private fun resetToMainMenu() {
        stopService(Intent(this, NetworkService::class.java))
        isUdpRunning = false
        udpSocket?.close()
        udpSocket = null
        
        currentTurn = 1
        revealed = false
        for (i in playerActions.indices) {
            playerActions[i] = null
        }
        for (i in slotToIp.indices) {
            slotToIp[i] = null
        }
        appMode = "SELECT"
        connectionStatus = "Disconnected"
        discoveredMasterIp = null
    }

    // Networking
    private fun handleNetworkEvent(event: String, remoteId: String, message: String?) {
        when (event) {
            NetworkService.EVENT_OPEN -> {
                if (appMode == "MASTER") {
                    handleNewClient(remoteId)
                } else {
                    connectionStatus = "Connected"
                }
            }
            NetworkService.EVENT_CLOSE -> {
                if (appMode == "MASTER") {
                    handleClientDisconnected(remoteId)
                } else {
                    connectionStatus = "Disconnected"
                }
            }
            NetworkService.EVENT_MESSAGE -> {
                if (message != null) {
                    handleIncomingMessage(remoteId, message)
                }
            }
        }
    }

    private fun handleNewClient(remoteId: String) {
        val normalizedRemoteId = normalizeIp(remoteId)
        val existingSlot = slotToIp.indexOf(normalizedRemoteId)
        if (existingSlot == -1) {
            val emptySlot = slotToIp.indexOfFirst { it == null }
            if (emptySlot != -1) {
                slotToIp[emptySlot] = normalizedRemoteId
                broadcastState()
            }
        }
    }

    private fun handleClientDisconnected(remoteId: String) {
        val normalizedRemoteId = normalizeIp(remoteId)
        val slot = slotToIp.indexOf(normalizedRemoteId)
        if (slot != -1) {
            slotToIp[slot] = null
            playerActions[slot] = null
            playerBgIndices[slot] = 0
            broadcastState()
        }
    }

    private fun handleIncomingMessage(clientId: String, line: String) {
        if (appMode == "CLIENT") {
            try {
                val parts = line.split(",")
                if (parts.size >= 10) {
                    myPlayerIndex = parts[0].toInt()
                    currentTurn = parts[1].toInt()
                    revealed = parts[2] == "1"
                    for (i in 0..3) {
                        val aIdx = parts[i + 3].toIntOrNull()
                        playerActions[i] = aIdx
                    }
                    for (i in 0..3) {
                        val conn = parts[i + 7] == "1"
                        if (i != 0) {
                            if (conn) {
                                if (slotToIp[i] == null) slotToIp[i] = "REMOTE"
                            } else {
                                slotToIp[i] = null
                            }
                        }
                    }
                    if (parts.size >= 15) {
                        for (i in 0..3) {
                            val bgIdx = parts[i + 11].toIntOrNull()
                            if (bgIdx != null && bgIdx in backgroundColors.indices) {
                                playerBgIndices[i] = bgIdx
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NETWORK", "Error parsing state: ${e.message}")
            }
            return
        }

        // Master handling
        val normalizedClientId = normalizeIp(clientId)
        val slot = slotToIp.indexOf(normalizedClientId)
        if (slot == -1) return

        if (line.startsWith("CMD_SELECT_ACTION:")) {
            val actionIdx = line.substringAfter("CMD_SELECT_ACTION:").toIntOrNull()
            if (actionIdx != null) {
                val current = playerActions[slot]
                playerActions[slot] = if (current == actionIdx) null else actionIdx
                broadcastState()
            }
        } else if (line.startsWith("CMD_BG_INDEX:")) {
            val bgIdx = line.substringAfter("CMD_BG_INDEX:").toIntOrNull()
            if (bgIdx != null && bgIdx in backgroundColors.indices) {
                playerBgIndices[slot] = bgIdx
                broadcastState()
            }
        }
    }

    private fun startNativeMasterServer() {
        val intent = Intent(this, NetworkService::class.java).apply {
            action = NetworkService.ACTION_START_SERVER
            putExtra(NetworkService.EXTRA_PORT, networkPort)
        }
        ContextCompat.startForegroundService(this, intent)
        connectionStatus = "Connected"
    }

    private fun startNativeClient(hostIp: String) {
        isUdpRunning = false
        udpSocket?.close()
        val intent = Intent(this, NetworkService::class.java).apply {
            action = NetworkService.ACTION_START_CLIENT
            putExtra(NetworkService.EXTRA_IP, hostIp)
            putExtra(NetworkService.EXTRA_PORT, networkPort)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendNetworkMessage(message: String) {
        val intent = Intent(this, NetworkService::class.java).apply {
            action = NetworkService.ACTION_SEND_MESSAGE
            putExtra(NetworkService.EXTRA_MESSAGE, message)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun broadcastState() {
        if (appMode != "MASTER") return
        
        val revStr = if (revealed) "1" else "0"
        val actionsStr = playerActions.joinToString(",") { it?.toString() ?: "N" }
        val connStr = slotToIp.map { if (it != null) "1" else "0" }.joinToString(",")
        val bgStr = playerBgIndices.joinToString(",")
        val commonData = "$currentTurn,$revStr,$actionsStr,$connStr,$bgStr"

        for (i in 1..3) {
            val targetIp = slotToIp[i]
            if (targetIp != null) {
                val intent = Intent(this, NetworkService::class.java).apply {
                    action = NetworkService.ACTION_SEND_MESSAGE
                    putExtra(NetworkService.EXTRA_IP, targetIp)
                    putExtra(NetworkService.EXTRA_MESSAGE, "$i,$commonData")
                }
                ContextCompat.startForegroundService(this, intent)
            }
        }
    }

    private fun startUdpBroadcast() {
        isUdpRunning = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val messageStr = "RACE_MASTER_PING:$networkPort"
                val sendData = messageStr.toByteArray()
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                while (isUdpRunning) {
                    val packet = DatagramPacket(sendData, sendData.size, broadcastAddress, 8888)
                    socket.send(packet)
                    delay(1000)
                }
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun startUdpDiscovery() {
        isUdpRunning = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                udpSocket?.close()
                udpSocket = DatagramSocket(8888)
                val receiveBuffer = ByteArray(1024)
                while (isUdpRunning) {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    udpSocket?.receive(packet)
                    val receivedMessage = String(packet.data, 0, packet.length)
                    if (receivedMessage.startsWith("RACE_MASTER_PING:")) {
                        val senderIp = packet.address.hostAddress
                        withContext(Dispatchers.Main) {
                            discoveredMasterIp = senderIp
                            connectionStatus = "Found Host: $senderIp"
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun getLocalIpAddress(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return String.format("%d.%d.%d.%d", (ip and 0xff), (ip shr 8 and 0xff), (ip shr 16 and 0xff), (ip shr 24 and 0xff))
    }

    private fun normalizeIp(ip: String): String {
        return when {
            ip.contains(":") -> if (ip.contains(".")) ip.substringAfterLast(":") else ip
            else -> ip
        }.trim().removePrefix("/")
    }
}
