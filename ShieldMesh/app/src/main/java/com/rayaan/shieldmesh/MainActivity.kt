package com.rayaan.shieldmesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.rayaan.shieldmesh.ui.theme.ShieldMeshTheme
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Audio permission needed for safety features", Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioRecorder = AudioRecorder(this)
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        Firebase.analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN) {
            param(FirebaseAnalytics.Param.SOURCE, "MainActivity")
        }

        setContent {
            ShieldMeshTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        audioRecorder = audioRecorder
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, audioRecorder: AudioRecorder) {
    var isFakeCallActive by remember { mutableStateOf(false) }
    var panicMessage by remember { mutableStateOf("") }
    var isLongPress by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    var cooldownUntil by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    var hasAudioPermission by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    if (isFakeCallActive) {
        FakeCallScreen(onDismiss = { isFakeCallActive = false })
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Shield Mesh",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location: Not available",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {},
                enabled = hasAudioPermission,
                modifier = Modifier
                    .size(200.dp, 60.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressStartTime = System.currentTimeMillis()
                                isLongPress = false
                            },
                            onTap = {
                                if (System.currentTimeMillis() < cooldownUntil) {
                                    Toast.makeText(context, "Please wait 5 minutes", Toast.LENGTH_SHORT).show()
                                    return@detectTapGestures
                                }
                                if (System.currentTimeMillis() - pressStartTime < 3000L) {
                                    audioRecorder.startBuffering()
                                    panicMessage = "Audio buffered! Distress detection pending..."
                                    cooldownUntil = System.currentTimeMillis() + (5 * 60 * 1000L)
                                }
                            },
                            onLongPress = {
                                isLongPress = true
                                audioRecorder.startBuffering()
                            }
                        )
                    }
            ) {
                Text("Panic Button")
            }
            if (!hasAudioPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Audio permission required for Panic Button",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { isFakeCallActive = true }) {
                Text("Fake Call")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(panicMessage)
            if (isLongPress) {
                AlertDialog(
                    onDismissRequest = {
                        isLongPress = false
                        audioRecorder.stopBuffering()
                    },
                    title = { Text("Recording… Speak now if you have a message") },
                    confirmButton = {
                        Button(onClick = {
                            panicMessage = "Audio recorded! Distress detection pending..."
                            isLongPress = false
                            audioRecorder.stopBuffering()
                            cooldownUntil = System.currentTimeMillis() + (5 * 60 * 1000L)
                        }) {
                            Text("Send")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            isLongPress = false
                            audioRecorder.stopBuffering()
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FakeCallScreen(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Incoming Call",
                color = Color.White,
                fontSize = 24.sp,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mom",
                color = Color.Green,
                fontSize = 36.sp,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Decline")
                }
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

class AudioRecorder(private val context: Context) {
    private val sampleRate = 8000
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private val buffer = mutableListOf<Short>()
    private var lastTemperatureCheck = 0L

    fun startBuffering() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Audio permission required", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(context, "Failed to initialize audio recording", Toast.LENGTH_SHORT).show()
                return
            }
            audioRecord?.startRecording()
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Audio recording error: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        val bufferThread = Thread {
            val tempBuffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                synchronized(buffer) {
                    buffer.addAll(tempBuffer.take(read))
                    if (buffer.size > sampleRate * 15) {
                        buffer.subList(0, buffer.size - sampleRate * 15).clear()
                    }
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTemperatureCheck >= 5000) {
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val temperature = try {
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10
                    } catch (e: Exception) {
                        0
                    }
                    if (temperature > 45 && temperature != 0) {
                        stopBuffering()
                        break
                    }
                    lastTemperatureCheck = currentTime
                }
            }
        }
        bufferThread.start()

        Handler(Looper.getMainLooper()).postDelayed({
            stopBuffering()
        }, 5 * 60 * 1000)
    }

    fun stopBuffering() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun getBufferedAudio(): ByteArray {
        synchronized(buffer) {
            val shortArray = buffer.toShortArray()
            return shortArray.toByteArray()
        }
    }
}

fun ShortArray.toByteArray(): ByteArray {
    val byteBuffer = ByteBuffer.allocate(this.size * 2)
    for (sample in this) {
        byteBuffer.putShort(sample)
    }
    return byteBuffer.array()
}