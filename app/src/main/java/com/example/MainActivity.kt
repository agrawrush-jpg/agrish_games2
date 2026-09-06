package com.example

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlayerColors
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import androidx.compose.ui.graphics.asImageBitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.shape.CircleShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundPlayer.init(this)
        enableEdgeToEdge()
        setContent {
            val gameViewModel: GameViewModel = viewModel()
            val state by gameViewModel.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            LaunchedEffect(state.toastMessage) {
                state.toastMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    gameViewModel.clearToastMessage()
                }
            }

            MyApplicationTheme(darkTheme = state.isDarkTheme) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) { innerPadding ->
                    GameApp(
                        viewModel = gameViewModel,
                        state = state,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GameApp(
    viewModel: GameViewModel,
    state: GameState,
    modifier: Modifier = Modifier
) {
    val gameViewModel = viewModel
    val focusManager = LocalFocusManager.current
    var startMusicEnabled by remember { mutableStateOf(SoundPlayer.startMusicEnabled) }

    BackHandler(enabled = state.selectedGameMode != "NONE") {
        viewModel.exitGameSelection()
    }

    val isStartingPage = (state.screen is GameScreen.Welcome) || (state.selectedGameMode == "NONE")

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isStartingPage, state.soundEnabled, startMusicEnabled) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                SoundPlayer.stopStartMusic()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                if (isStartingPage && state.soundEnabled && startMusicEnabled) {
                    SoundPlayer.startStartMusic()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isStartingPage, state.soundEnabled, startMusicEnabled) {
        if (isStartingPage && state.soundEnabled && startMusicEnabled) {
            SoundPlayer.startStartMusic()
        } else {
            SoundPlayer.stopStartMusic()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDown() }) {
                            SoundPlayer.stopWinLose()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        when (state.selectedGameMode) {
            "NONE" -> {
                GameSelectionScreen(viewModel = gameViewModel, state = state)
            }
            "BANK" -> {
                when (state.screen) {
                    is GameScreen.Welcome -> {
                        WelcomeScreen(
                            viewModel = gameViewModel,
                            state = state,
                            startMusicEnabled = startMusicEnabled,
                            onToggleStartMusic = {
                                SoundPlayer.toggleStartMusic(it)
                                startMusicEnabled = it
                            }
                        )
                    }
                    is GameScreen.Setup -> {
                        SetupScreen(viewModel = gameViewModel, state = state)
                    }
                    is GameScreen.Lobby -> {
                        LobbyScreen(viewModel = gameViewModel, state = state)
                    }
                    is GameScreen.OrderWheel -> {
                        OrderWheelScreen(viewModel = gameViewModel, state = state)
                    }
                    is GameScreen.Play -> {
                        PlayDashboard(
                            viewModel = gameViewModel,
                            state = state,
                            startMusicEnabled = startMusicEnabled,
                            onToggleStartMusic = {
                                SoundPlayer.toggleStartMusic(it)
                                startMusicEnabled = it
                            }
                        )
                    }
                    else -> {
                        WelcomeScreen(
                            viewModel = gameViewModel,
                            state = state,
                            startMusicEnabled = startMusicEnabled,
                            onToggleStartMusic = {
                                SoundPlayer.toggleStartMusic(it)
                                startMusicEnabled = it
                            }
                        )
                    }
                }
            }
            "POKER" -> {
                when (state.screen) {
                    is GameScreen.PokerWelcome -> {
                        PokerWelcomeScreen(viewModel = gameViewModel, state = state)
                    }
                    is GameScreen.PokerLobby -> {
                        PokerLobbyScreen(viewModel = gameViewModel, state = state)
                    }
                    is GameScreen.PokerPlay -> {
                        PokerPlayScreen(viewModel = gameViewModel, state = state)
                    }
                    else -> {
                        PokerWelcomeScreen(viewModel = gameViewModel, state = state)
                    }
                }
            }
            else -> {
                GameSelectionScreen(viewModel = gameViewModel, state = state)
            }
        }
    }
}

// ==========================================
// CAMERA SCANNER COMPOSABLE
// ==========================================
@Composable
fun CameraScannerView(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeCameraProvider?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            executor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        activeCameraProvider = cameraProvider
                        val preview = androidx.camera.core.Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { qrText ->
                            onQrCodeScanned(qrText)
                        })

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with scanning frame and cancel button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // A nice scan overlay (with a box in the center)
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    .align(Alignment.Center)
            ) {
                Text(
                    text = "Align QR Code inside the frame",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ==========================================
// WELCOME SCREEN
// ==========================================
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WelcomeScreen(
    viewModel: GameViewModel,
    state: GameState,
    startMusicEnabled: Boolean,
    onToggleStartMusic: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf("") }
    var inputGameId by remember { mutableStateOf("") }
    var inputGameMoney by remember { mutableStateOf("100") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var modeSelection by remember { mutableStateOf<String?>(null) } // null, "create", "join", or "device"
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_custom),
            contentDescription = "Welcome Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (modeSelection == null) {
                    // Back and Screen Rotation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.exitGameSelection()
                            },
                            modifier = Modifier.testTag("exit_bank_hub")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                val activity = context as? Activity
                                activity?.let {
                                    val current = it.requestedOrientation
                                    if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White)
                        }
                    }
                    
                    // Logo Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "Bank Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "ባንክ ከአግርሽ ጋር 5.0",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    // Start Music Toggle Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .clickable {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                onToggleStartMusic(!startMusicEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (startMusicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = "Toggle Start Music",
                            tint = if (startMusicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (startMusicEnabled) "Start Music On" else "Start Music Off",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (startMusicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = "The ultimate card & asset trading experience. Choose how you want to play today!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    )

                    // Resume Game Button
                    if (state.canResume) {
                        Button(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                viewModel.resumeGame()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("btn_resume_game"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "RESUME PREVIOUS GAME",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                    val screenDesc = when (state.savedScreenString) {
                                        "Setup" -> "Setup Screen"
                                        "Lobby" -> "Multiplayer Lobby"
                                        "OrderWheel" -> "Player Ordering Wheel"
                                        "Play" -> "Active Game Board"
                                        else -> "In-progress Session"
                                    }
                                    Text(
                                        text = "Continue session ($screenDesc).",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Create Game Button
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            modeSelection = "create"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("btn_create_game"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddBox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "CREATE A GAME",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Host local Wi-Fi or local multiplayer.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Join Game Button
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            modeSelection = "join"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("btn_join_game"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "JOIN A GAME",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Connect to friend's game using Game ID.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (state.playModeSelection != "WIFI") {
                        // Play on One Device Button
                        Button(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                modeSelection = "device"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("btn_play_on_device"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "PLAY ON ONE DEVICE",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Play solo or local pass-and-play.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Bank application developed by Agraw Ali • Version 3.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                } else if (modeSelection == "create") {
                    // CREATE GAME SCREEN FLOW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                modeSelection = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create a New Game",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Enter Your Name") },
                        placeholder = { Text("e.g. Host Player") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputGameMoney,
                        onValueChange = { inputGameMoney = it },
                        label = { Text("Game Money Amount (ብር)") },
                        placeholder = { Text("e.g. 100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.AttachMoney, contentDescription = null)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Text(
                        text = "CONNECTION SERVER TYPE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    var connectionType by remember { mutableStateOf("cloud") } // "cloud" or "local"
                    var portInput by remember { mutableStateOf("8888") }

                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { connectionType = "cloud" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "cloud") MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (connectionType == "cloud") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cloud (Firebase)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { connectionType = "local" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "local") MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (connectionType == "local") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Local Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (connectionType == "local") {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port Number") },
                            placeholder = { Text("8888") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "CHOOSE GAME TYPE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    // Option A: Host Wi-Fi Multiplayer
                    Button(
                        onClick = {
                            if (inputName.isBlank()) {
                                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                            } else {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                val amount = inputGameMoney.trim().toIntOrNull() ?: 100
                                if (connectionType == "local") {
                                    val port = portInput.trim().toIntOrNull() ?: 8888
                                    viewModel.startLocalWifiHost(
                                        playerName = inputName.trim(),
                                        port = port,
                                        isPoker = false,
                                        gameMoney = amount
                                    )
                                } else {
                                    viewModel.updateSetupInputs(state.numPlayersInput, amount.toString())
                                    viewModel.hostGame(inputName.trim())
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Text("HOST WI-FI MULTIPLAYER", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option B: Play Offline
                    OutlinedButton(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.resetFullGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("PLAY OFFLINE (PASS & PLAY)", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Info, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Wi-Fi Multiplayer lets friends on your local network join your bank. Offline mode allows quick pass-and-play setup without any network required.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else if (modeSelection == "join") {
                    // JOIN GAME SCREEN FLOW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                modeSelection = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Join a Friend's Game",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    var connectionType by remember { mutableStateOf("cloud") } // "cloud" or "local"
                    var hostIpInput by remember { mutableStateOf(com.example.network.PokerNetworkManager.localIpAddress) }
                    var portInput by remember { mutableStateOf("8888") }

                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { connectionType = "cloud" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "cloud") MaterialTheme.colorScheme.secondary else Color.Transparent, contentColor = if (connectionType == "cloud") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cloud (Firebase)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { connectionType = "local" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "local") MaterialTheme.colorScheme.secondary else Color.Transparent, contentColor = if (connectionType == "local") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Local Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Enter Your Name") },
                        placeholder = { Text("e.g. Guest Player") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    )

                    if (connectionType == "local") {
                        OutlinedTextField(
                            value = hostIpInput,
                            onValueChange = { hostIpInput = it },
                            label = { Text("Host IP Address (e.g., 192.168.1.100)") },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port Number") },
                            placeholder = { Text("8888") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = inputGameId,
                            onValueChange = { inputGameId = it },
                            label = { Text("Friend's Game ID") },
                            placeholder = { Text("8-character hex code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                if (cameraPermissionState.status.isGranted) {
                                    showCameraScanner = true
                                } else {
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Text("SCAN QR CODE TO JOIN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (inputName.isBlank()) {
                                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                            } else if (connectionType == "cloud" && inputGameId.trim().length != 8) {
                                Toast.makeText(context, "Game ID must be exactly 8 characters", Toast.LENGTH_SHORT).show()
                            } else if (connectionType == "local" && hostIpInput.isBlank()) {
                                Toast.makeText(context, "Please enter the Host's IP address", Toast.LENGTH_SHORT).show()
                            } else {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                if (connectionType == "local") {
                                    val port = portInput.trim().toIntOrNull() ?: 8888
                                    viewModel.connectLocalWifiClient(
                                        playerName = inputName.trim(),
                                        hostIp = hostIpInput.trim(),
                                        port = port,
                                        isPoker = false
                                    )
                                } else {
                                    viewModel.joinGame(inputGameId.trim(), inputName.trim())
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Text("JOIN GAME SESSION", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (modeSelection == "device") {
                    // PLAY ON ONE DEVICE SCREEN FLOW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                modeSelection = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play on One Device",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Local Pass & Play Facilitator",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Use this mode to play completely offline on a single device. You will act as the facilitator/banker, managing players, trades, card shuffles, ledger histories, and starting balances directly on this screen without requiring any internet connection or Wi-Fi.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.resetFullGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btn_start_local_setup"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                            Text("PROCEED TO GAME SETUP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // Show error/status messages if any
                if (state.wheelMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.wheelMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (showCameraScanner) {
            CameraScannerView(
                onQrCodeScanned = { scannedId ->
                    val cleanedId = scannedId.trim()
                    if (cleanedId.length == 8) {
                        inputGameId = cleanedId
                        showCameraScanner = false
                        if (inputName.isNotBlank()) {
                            viewModel.joinGame(cleanedId, inputName.trim())
                        } else {
                            Toast.makeText(context, "Scanned ID: $cleanedId. Please enter your name to join.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Invalid QR code. Expected an 8-character game ID.", Toast.LENGTH_SHORT).show()
                    }
                },
                onClose = {
                    showCameraScanner = false
                }
            )
        }
    }
}

// ==========================================
// LOBBY SCREEN
// ==========================================
@Composable
fun LobbyScreen(viewModel: GameViewModel, state: GameState) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Lobby Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "Lobby Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "GAME LOBBY",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Share ID Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "UNIQUE GAME ID",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = state.gameId,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Share this with friends on your local Wi-Fi to join your bank!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (state.isLocalWifi) {
                    GameLinkShareCard(
                        ip = com.example.network.PokerNetworkManager.localIpAddress,
                        port = 8080
                    )
                }

                var showQrCode by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = {
                        SoundPlayer.playClick(state.vibrationEnabled)
                        showQrCode = !showQrCode
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (showQrCode) Icons.Default.VisibilityOff else Icons.Default.QrCode, contentDescription = null)
                        Text(if (showQrCode) "HIDE JOIN QR CODE" else "SHOW JOIN QR CODE")
                    }
                }

                if (showQrCode) {
                    val qrBitmap = remember(state.gameId) {
                        QrCodeHelper.generateQrCode(state.gameId, 256)
                    }
                    if (qrBitmap != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .size(200.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Game Join QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Connected Players List
                Text(
                    text = "CONNECTED PLAYERS (${state.lobbyPlayers.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.lobbyPlayers.forEachIndexed { index, name ->
                        val itemColor = PlayerColors[index % PlayerColors.size]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(itemColor)
                                )
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (index == 0) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "HOST",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isHost) {
                    // Host input: Game Money Amount (መደብ)
                    OutlinedTextField(
                        value = state.gameMoneyInput,
                        onValueChange = { viewModel.updateSetupInputs(state.numPlayersInput, it) },
                        label = { Text("Game Money (መደብ) Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (state.lobbyPlayers.size < 2) {
                                Toast.makeText(context, "Need at least 2 players to start a multiplayer game!", Toast.LENGTH_SHORT).show()
                            } else {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                viewModel.sendActionToHost("START_LOBBY_GAME")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("START MULTIPLAYER GAME", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Clients view: waiting for host
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = "Game Money",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Game Money (መደብ)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${state.gameMoneyInput} ብር",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Waiting for host to start game...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ORDER WHEEL SCREEN
// ==========================================
@Composable
fun OrderWheelScreen(viewModel: GameViewModel, state: GameState) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TURNS ORDER WHEEL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = "Host will spin the wheel to randomly determine the order of players. The turns order will sync across all devices!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The spinning wheel Canvas
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = size.minDimension / 2f - 10.dp.toPx()
                        
                        val playersCount = state.players.size.coerceAtLeast(1)
                        val anglePerSegment = 360f / playersCount
                        
                        for (i in 0 until playersCount) {
                            val player = state.players[i]
                            val pColor = PlayerColors[player.colorIndex % PlayerColors.size]
                            val startAngle = state.wheelAnimationAngle + i * anglePerSegment
                            
                            drawArc(
                                color = pColor,
                                startAngle = startAngle,
                                sweepAngle = anglePerSegment,
                                useCenter = true,
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius)
                            )
                        }
                        
                        // Draw center pin
                        drawCircle(
                            color = Color.White,
                            radius = 16.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                        )
                    }

                    // A little red indicator pointing from top down
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopCenter)
                            .background(Color.Red, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.wheelSelectedPlayerName != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.wheelSelectedPlayerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                        )
                    }
                } else if (state.isSpinningWheel) {
                    Text(
                        text = "Wheel is spinning...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = "Ready to determine turn order!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                if (state.isHost && !state.isSpinningWheel && state.wheelSelectedPlayerName == null) {
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.sendActionToHost("SPIN_WHEEL")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SPIN THE WHEEL", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Client view / Spinning view
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SETUP SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: GameViewModel, state: GameState) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_custom),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(24.dp)
                .testTag("setup_card"),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White.copy(alpha = 0.8f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Back Button to welcome screen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            if (state.playModeSelection == "DEVICE") {
                                viewModel.exitGameSelection()
                            } else {
                                viewModel.updateScreen(GameScreen.Welcome)
                            }
                        },
                        modifier = Modifier.testTag("setup_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Welcome Screen",
                            tint = Color.White
                        )
                    }
                }

                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Bank Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "ባንክ ከአግርሽ ጋር 5.0",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Configure your facilitator ledger to initiate the game session.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Input: Number of Players
                OutlinedTextField(
                    value = state.numPlayersInput,
                    onValueChange = { viewModel.updateSetupInputs(it, state.gameMoneyInput) },
                    label = { Text("Number of Players") },
                    placeholder = { Text("e.g. 3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("num_players_input"),
                    leadingIcon = {
                        Icon(Icons.Default.People, contentDescription = null)
                    }
                )

                // Input: Game Money (ብር)
                OutlinedTextField(
                    value = state.gameMoneyInput,
                    onValueChange = { viewModel.updateSetupInputs(state.numPlayersInput, it) },
                    label = { Text("Game Money Amount (ብር)") },
                    placeholder = { Text("e.g. 100") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("medeb_input"),
                    leadingIcon = {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        viewModel.startGame()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_game_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "START GAME SESSION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ==========================================
// PLAY DASHBOARD (WITH TWO WINDOW TABS)
// ==========================================
@Composable
fun PlayDashboard(
    viewModel: GameViewModel,
    state: GameState,
    startMusicEnabled: Boolean,
    onToggleStartMusic: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                state = state,
                viewModel = viewModel,
                startMusicEnabled = startMusicEnabled,
                onToggleStartMusic = onToggleStartMusic,
                onDismiss = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Tab Row Bar
                TabRow(
                    selectedTabIndex = if (state.activeTab == "BANK") 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = state.activeTab == "BANK",
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectTab("BANK")
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null)
                                Text("BANK", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_bank")
                    )
                    Tab(
                        selected = state.activeTab == "CARDS",
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectTab("CARDS")
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Style, contentDescription = null)
                                Text("CARDS", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_cards")
                    )
                    // Gear Icon
                    IconButton(onClick = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        scope.launch { drawerState.open() }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(state.activeTab) {
                            var accumulatedDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulatedDrag = 0f
                                },
                                onDragEnd = {
                                    val threshold = 350f // Higher threshold for a "harder and longer" swapping action
                                    if (accumulatedDrag > threshold) {
                                        // Swipe right (from left to right) - switch to Bank
                                        if (state.activeTab == "CARDS") {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.selectTab("BANK")
                                        }
                                    } else if (accumulatedDrag < -threshold) {
                                        // Swipe left (from right to left) - switch to Cards
                                        if (state.activeTab == "BANK") {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.selectTab("CARDS")
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    accumulatedDrag += dragAmount
                                }
                            )
                        }
                ) {
                    if (state.activeTab == "BANK") {
                        BankTabContent(viewModel = viewModel, state = state)
                    } else {
                        CardsTabContent(viewModel = viewModel, state = state)
                    }
                }
            }

            // Overlay is drawn here on top!
            GameEffectOverlay(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun SettingsDrawer(
    state: GameState,
    viewModel: GameViewModel,
    startMusicEnabled: Boolean,
    onToggleStartMusic: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Sound Effects")
                Switch(
                    checked = state.soundEnabled,
                    onCheckedChange = {
                        viewModel.toggleSound()
                        // Use !state.soundEnabled because we toggle it
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Vibration")
                Switch(
                    checked = state.vibrationEnabled,
                    onCheckedChange = {
                        viewModel.toggleVibration()
                        // Vibrate if vibration is currently enabled OR being enabled
                        SoundPlayer.playClick(context, state.vibrationEnabled || !state.vibrationEnabled)
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Start Music")
                Switch(
                    checked = startMusicEnabled,
                    onCheckedChange = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        onToggleStartMusic(it)
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Dark Mode")
                Switch(
                    checked = state.isDarkTheme,
                    onCheckedChange = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        viewModel.toggleTheme()
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Rotate Screen")
                IconButton(
                    onClick = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        val activity = context as? Activity
                        activity?.let {
                            val current = it.requestedOrientation
                            if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate Screen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state.selectedGameMode != "POKER") {
                Text("Player Box Layout", style = MaterialTheme.typography.titleMedium)
                
                val layouts = listOf("horizontal", "vertical", "grid")
                layouts.forEach { layout ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.playerBoxLayout == layout,
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.setPlayerBoxLayout(layout)
                            }
                        )
                        Text(layout.replaceFirstChar { it.uppercase() })
                    }
                }
            }

            if (state.selectedGameMode == "POKER") {
                HorizontalDivider()
                Text("Poker Settings", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFD700))
                
                Text("Bot Difficulty: ${state.pokerBotDifficulty}", style = MaterialTheme.typography.bodyMedium)
                val difficulties = listOf("Beginner", "Easy", "Medium", "Hard", "Expert")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    difficulties.forEach { diff ->
                        val isSelected = state.pokerBotDifficulty == diff
                        InputChip(
                            selected = isSelected,
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.setPokerBotDifficulty(diff)
                            },
                            label = { Text(diff, fontSize = 11.sp, color = if (isSelected) Color.Black else Color.White) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Color(0xFFFFD700),
                                containerColor = Color(0xFF222222)
                            )
                        )
                    }
                }
                
                Text("Table Width Scale: ${String.format("%.2f", state.pokerTableWidthScale)}x", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.pokerTableWidthScale,
                    onValueChange = { viewModel.setPokerTableWidthScale(it) },
                    valueRange = 0.6f..1.2f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD700),
                        activeTrackColor = Color(0xFFFFD700)
                    )
                )

                Text("Table Height: ${state.pokerTableHeight}dp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.pokerTableHeight.toFloat(),
                    onValueChange = { viewModel.setPokerTableHeight(it.toInt()) },
                    valueRange = 100f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD700),
                        activeTrackColor = Color(0xFFFFD700)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "ባንክ ከአግርሽ ጋር 5.0 (Version 5) application developed by Agraw Ali",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "next update: global internet matchmaking server",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        }
    }
}
data class Particle(
    val x: Float, // fractional width (0f to 1f)
    val y: Float, // fractional height (0f to 1f)
    val speedY: Float,
    val speedX: Float,
    val angle: Float,
    val rotationSpeed: Float,
    val text: String,
    val size: Float
)

@Composable
fun GameEffectOverlay(state: GameState, viewModel: GameViewModel) {
    val winPlayerName = state.winEffectPlayerName
    val losePlayerName = state.loseEffectPlayerName

    if (winPlayerName != null || losePlayerName != null) {
        LaunchedEffect(state.effectTriggerId) {
            // Let the effect display for exactly 2 seconds then dismiss
            delay(2000L)
            viewModel.clearActiveEffects()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable {
                    SoundPlayer.stopAll()
                    viewModel.clearActiveEffects()
                }
                .testTag("effect_overlay"),
            contentAlignment = Alignment.Center
        ) {
            if (winPlayerName != null) {
                WinAnimationContent(playerName = winPlayerName)
            } else if (losePlayerName != null) {
                LoseAnimationContent(playerName = losePlayerName)
            }
        }
    }
}

@Composable
fun FlyingParticles(emojis: List<String>, falling: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthDp = with(density) { widthPx.toDp() }
        val heightDp = with(density) { heightPx.toDp() }

        val particles = remember {
            List(30) {
                val startX = if (falling) kotlin.random.Random.nextFloat() else -0.2f
                val startY = if (falling) kotlin.random.Random.nextFloat() * -1f else kotlin.random.Random.nextFloat()
                Particle(
                    x = startX,
                    y = startY,
                    speedY = if (falling) kotlin.random.Random.nextFloat() * 0.015f + 0.008f else kotlin.random.Random.nextFloat() * 0.01f - 0.005f,
                    speedX = if (falling) kotlin.random.Random.nextFloat() * 0.004f - 0.002f else kotlin.random.Random.nextFloat() * 0.018f + 0.012f,
                    angle = kotlin.random.Random.nextFloat() * 360f,
                    rotationSpeed = kotlin.random.Random.nextFloat() * 10f - 5f,
                    text = emojis[kotlin.random.Random.nextInt(emojis.size)],
                    size = kotlin.random.Random.nextFloat() * 20f + 24f
                )
            }
        }

        var particleStates by remember { mutableStateOf(particles) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(16L) // ~60fps
                particleStates = particleStates.map { p ->
                    var nextY = p.y + p.speedY
                    var nextX = p.x + p.speedX

                    if (falling) {
                        if (nextY > 1.1f) {
                            nextY = -0.1f
                            nextX = kotlin.random.Random.nextFloat()
                        }
                    } else {
                        if (nextX > 1.2f) {
                            nextX = -0.2f
                            nextY = kotlin.random.Random.nextFloat()
                        }
                    }

                    p.copy(
                        x = nextX,
                        y = nextY,
                        angle = p.angle + p.rotationSpeed
                    )
                }
            }
        }

        particleStates.forEach { p ->
            val posX = widthDp * p.x
            val posY = heightDp * p.y
            Text(
                text = p.text,
                fontSize = p.size.sp,
                modifier = Modifier
                    .offset(x = posX, y = posY)
                    .graphicsLayer {
                        rotationZ = p.angle
                    }
            )
        }
    }
}

@Composable
fun WinAnimationContent(playerName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FlyingParticles(emojis = listOf("💵", "💸", "💰", "🪙", "🤑"), falling = true)

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            visible = true
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "🏆",
                    fontSize = 72.sp
                )
                Text(
                    text = "$playerName ጀግና!",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2ECC71),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 8f
                        )
                    )
                )
                Text(
                    text = "CHAMPION!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun LoseAnimationContent(playerName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FlyingParticles(emojis = listOf("💩", "😢", "😭", "📉", "😩"), falling = false)

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            visible = true
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "🌧️",
                    fontSize = 72.sp
                )
                Text(
                    text = "$playerName ቀዘነች!",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE74C3C),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 8f
                        )
                    )
                )
                Text(
                    text = "TRY HARDER NEXT TIME!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// ==========================================
// BANK WINDOW CONTENT
// ==========================================
@Composable
fun BankTabContent(viewModel: GameViewModel, state: GameState) {
    val context = LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }
    var activeQuitPlayer by remember { mutableStateOf<Player?>(null) }
    var activeBorrowPlayer by remember { mutableStateOf<Player?>(null) }
    var activeReturnPlayer by remember { mutableStateOf<Player?>(null) }

    val listState = rememberLazyListState()
    val horizontalListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val currentQuitPlayer = state.players.firstOrNull { it.id == activeQuitPlayer?.id }
    val currentBorrowPlayer = state.players.firstOrNull { it.id == activeBorrowPlayer?.id }
    val currentReturnPlayer = state.players.firstOrNull { it.id == activeReturnPlayer?.id }

    // Floating/Global dialog triggers
    if (currentQuitPlayer != null) {
        QuitGameDialog(
            player = currentQuitPlayer,
            viewModel = viewModel,
            onDismiss = { activeQuitPlayer = null }
        )
    }

    if (currentBorrowPlayer != null) {
        BorrowDialog(
            player = currentBorrowPlayer,
            viewModel = viewModel,
            allPlayers = state.players,
            onDismiss = { activeBorrowPlayer = null }
        )
    }

    if (currentReturnPlayer != null) {
        ReturnMoneyDialog(
            player = currentReturnPlayer,
            viewModel = viewModel,
            onDismiss = { activeReturnPlayer = null }
        )
    }

    if (showLogDialog) {
        LogSummaryDialog(
            logText = viewModel.generateGameLogString(),
            onDismiss = { showLogDialog = false }
        )
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        // Vault / Bank Amount Card - Fixed at the top!
        val cardPadding = if (isScrolled) 8.dp else 20.dp
        val titleFontSize = if (isScrolled) 10.sp else 13.sp
        val amountFontSize = if (isScrolled) 18.sp else 32.sp

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bank_amount_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CURRENT BANK AMOUNT",
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = if (isScrolled) 1.sp else 1.5.sp
                )
                if (!isScrolled) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "${state.bankAmount} ብር",
                    fontSize = amountFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isScrolled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                showLogDialog = true
                            },
                            label = { Text("View & Export Logs") },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Fixed Player shortcuts bar next to/below current bank amount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scroll to:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            state.players.forEachIndexed { index, player ->
                val playerColor = PlayerColors[player.colorIndex % PlayerColors.size]
                AssistChip(
                    onClick = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        coroutineScope.launch {
                            when (state.playerBoxLayout) {
                                "horizontal" -> {
                                    listState.animateScrollToItem(1)
                                    horizontalListState.animateScrollToItem(index)
                                }
                                "grid" -> {
                                    listState.animateScrollToItem(1 + (index / 2))
                                }
                                else -> { // "vertical"
                                    listState.animateScrollToItem(1 + index)
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = if (player.name.isNotBlank()) player.name else "Player ${index + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (player.isInitialized) playerColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (player.isInitialized) playerColor else Color.Gray)
                        )
                    },
                    border = BorderStroke(1.dp, playerColor.copy(alpha = 0.5f)),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = playerColor.copy(alpha = 0.08f)
                    )
                )
            }
        }

        val isAtBottom = remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    false
                } else {
                    val lastVisibleItem = visibleItems.last()
                    lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                }
            }
        }
        val isAtTop = remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
        val showRankingBoard = isAtTop.value || isAtBottom.value

        val bankPlayersList = state.players.filter { it.isInitialized }.map { (if (it.name.isNotBlank()) it.name else "Player") to it.currentBalance }
        if (bankPlayersList.isNotEmpty()) {
            AnimatedVisibility(
                visible = showRankingBoard,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                RealTimeRankingBoard(
                    title = "REAL-TIME RANKINGS",
                    players = bankPlayersList,
                    isDark = state.isDarkTheme,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Title: Players
            item {
                Text(
                    text = "PLAYER LEDGERS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
    
            // Colorful boxes dedicated to each player
            if (state.playerBoxLayout == "horizontal") {
                item {
                    LazyRow(
                        state = horizontalListState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        items(state.players, key = { it.id }) { player ->
                            Box(modifier = Modifier.width(320.dp)) {
                                PlayerBoxItem(
                                    player = player,
                                    viewModel = viewModel,
                                    state = state,
                                    onTriggerBorrow = { activeBorrowPlayer = player },
                                    onTriggerReturn = { activeReturnPlayer = player },
                                    onTriggerQuit = { activeQuitPlayer = player }
                                )
                            }
                        }
                    }
                }
            } else if (state.playerBoxLayout == "grid") {
                val chunks = state.players.chunked(2)
                chunks.forEach { rowPlayers ->
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPlayers.forEach { player ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PlayerBoxItem(
                                        player = player,
                                        viewModel = viewModel,
                                        state = state,
                                        onTriggerBorrow = { activeBorrowPlayer = player },
                                        onTriggerReturn = { activeReturnPlayer = player },
                                        onTriggerQuit = { activeQuitPlayer = player }
                                    )
                                }
                            }
                            if (rowPlayers.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                items(state.players, key = { it.id }) { player ->
                    PlayerBoxItem(
                        player = player,
                        viewModel = viewModel,
                        state = state,
                        onTriggerBorrow = { activeBorrowPlayer = player },
                        onTriggerReturn = { activeReturnPlayer = player },
                        onTriggerQuit = { activeQuitPlayer = player }
                    )
                }
            }

        // Bottom Level Global Utilities Area
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        }

        // Global Utility Card: Add money to bank (Tax)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add more money to the bank",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.bottomAddToBankInput,
                            onValueChange = { viewModel.updateBottomAddToBankInput(it) },
                            placeholder = { Text("Contribution amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("Amount") }
                        )
                        Button(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                val amount = state.bottomAddToBankInput.toIntOrNull() ?: 0
                                if (amount > 0) {
                                    val activePlayers = state.players.filter { it.isInitialized }.size
                                    viewModel.executeBottomAddToBank()
                                    val finalBankAmt = state.bankAmount + (amount * activePlayers)
                                    Toast.makeText(context, "bank contribution done. Total current bank amount is $finalBankAmt ብር", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Deduct & Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Global Utility: Add New Player
        item {
            if (state.isAddingNewPlayer) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Add New Player",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = state.newPlayerName,
                            onValueChange = { viewModel.updateNewPlayerInputs(it, state.newPlayerInitialSaving) },
                            label = { Text("New Player Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.newPlayerInitialSaving,
                            onValueChange = { viewModel.updateNewPlayerInputs(state.newPlayerName, it) },
                            label = { Text("Initial Contribution") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.toggleAddingNewPlayer(false) }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { viewModel.addNewPlayer() }) {
                                Text("Initialize Player")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.toggleAddingNewPlayer(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("ADD A NEW PLAYER TO GAME", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Export Log / History Section Trigger button
        item {
            Button(
                onClick = {
                    val logData = viewModel.generateGameLogString()
                    saveTextToDownloads(context, "banker_agrish_v3_1_log_${System.currentTimeMillis()}.txt", logData)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Text("DOWNLOAD LEDGER & SEQUENCE LOG", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        // Reset Game Banner / Danger Zone Section
        item {
            var showResetConfirmDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DANGER ZONE",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Resetting the game will start fresh and wipe all data.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            showResetConfirmDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("reset_game_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Game Icon",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET GAME & START FRESH",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (showResetConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmDialog = false },
                    title = { Text("Reset Game?") },
                    text = { Text("Are you sure you want to reset the current game? This will wipe out all balances, histories, players, and start fresh from the setup screen.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResetConfirmDialog = false
                                viewModel.resetFullGame()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Everything")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
    }
    }
}

// ==========================================
// PLAYER BOX ITEM (DEDICATED COLORFUL BOX)
// ==========================================
@Composable
fun PlayerBoxItem(
    player: Player,
    viewModel: GameViewModel,
    state: GameState,
    onTriggerBorrow: () -> Unit,
    onTriggerReturn: () -> Unit,
    onTriggerQuit: () -> Unit
) {
    val borderColor = PlayerColors[player.colorIndex % PlayerColors.size]
    val boxBg = Color.Black.copy(alpha = 0.7f)
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White.copy(alpha = 0.8f),
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        focusedBorderColor = borderColor,
        unfocusedBorderColor = borderColor.copy(alpha = 0.5f),
        focusedLeadingIconColor = Color.White,
        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
    )

    val isEditable = !state.isMultiplayer || player.id == state.localPlayerId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_box_${player.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = boxBg),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Name and initialized status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!player.isInitialized) {
                    if (isEditable) {
                        // Let user set the name and initial contribution before initializing
                        OutlinedTextField(
                            value = player.name,
                            onValueChange = { viewModel.updatePlayerTempName(player.id, it) },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(end = 8.dp),
                            colors = textFieldColors
                        )
                        OutlinedTextField(
                            value = player.initialContribution.toString(),
                            onValueChange = { viewModel.updatePlayerInitialContribution(player.id, it) },
                            label = { Text("Initial Contribution") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(start = 8.dp),
                            colors = textFieldColors
                        )
                    } else {
                        // Static representation of uninitialized player if not editable
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = player.name.ifBlank { "Uninitialized Player" },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Initial Contribution: ${player.initialContribution} ብር (Pending Initialization)",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    // Initialized static header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(borderColor)
                            )
                            Text(
                                text = player.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = borderColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Initialized",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            
                            if (!state.isMultiplayer) {
                                // Reorder buttons - move up / down
                                IconButton(
                                    onClick = {
                                        SoundPlayer.playClick(state.vibrationEnabled)
                                        viewModel.movePlayerUp(player.id)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        SoundPlayer.playClick(state.vibrationEnabled)
                                        viewModel.movePlayerDown(player.id)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!player.isInitialized) {
                if (isEditable) {
                    // Initialize button
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.initializePlayer(player.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("initialize_${player.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Initialize Player", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                } else {
                    // Lock icon and waiting message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Waiting for ${player.name} to initialize...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                // Initialized controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Balance:",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${player.currentBalance} ብር",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (player.currentBalance >= 0) Color.White else Color.Red
                    )
                }

                // Compact Player Stats Summary in 2 lines
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val diff = player.currentBalance - player.initialContribution
                        val diffColor = if (diff >= 0) Color(0xFF2ECC71) else Color(0xFFE74C3C)
                        val diffSign = if (diff >= 0) "+" else ""

                        // Row 1: Won, Lost, Additions, Diff
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val diff = player.currentBalance - player.initialContribution
                            val diffSign = if (diff >= 0) "+" else ""
                            Text(
                                text = "Won: ${player.totalWon} ብር | Lost: ${player.totalLost} ብር | Additions: ${player.totalSavingsAdded} ብር | Diff: $diffSign$diff ብር",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Row 2: Bank (Initial + Mass), Borrowed, Lended, Returned, Net
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val initialDeduct = viewModel.state.value.gameMoneyAmount
                            val massDeduct = player.totalMassContributionsPaid
                            Text(
                                text = "Bank: ${initialDeduct}+${massDeduct} ብር | Borrowed: ${player.totalBorrowed} ብር | Lended: ${player.totalLended} ብር | Returned: ${player.totalReturned} ብር | Net: ${player.currentBalance} ብር",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (isEditable) {
                    // Empty play money field
                    OutlinedTextField(
                        value = player.playMoneyInput,
                        onValueChange = { viewModel.updatePlayMoneyInput(player.id, it) },
                        label = { Text("Play Money Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_money_input_${player.id}"),
                        leadingIcon = {
                            Icon(Icons.Default.Casino, contentDescription = null)
                        },
                        colors = textFieldColors
                    )

                    // Warning message logic if play money is larger than balance
                    if (player.warningMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = player.warningMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Minimum amount to add to continue: ${player.minNeededToContinue ?: 0}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                                
                                // A space and a button to add money to saving inside the warning
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = player.warningInput,
                                        onValueChange = { viewModel.updateSavingInput(player.id, it, isWarningField = true) },
                                        placeholder = { Text("Amount") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.error,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(state.vibrationEnabled)
                                            viewModel.addMoneyToSaving(player.id, isWarningField = true)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Add Saving")
                                    }
                                }
                            }
                        }
                    }

                    // Win (Green) and Lose (Red) buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val amt = player.playMoneyInput.toIntOrNull() ?: 0
                                if (amt > 0) {
                                    SoundPlayer.playWin(state.soundEnabled)
                                }
                                viewModel.playWin(player.id)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("win_button_${player.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Win", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val amt = player.playMoneyInput.toIntOrNull() ?: 0
                                if (amt > 0) {
                                    SoundPlayer.playLose(state.soundEnabled)
                                }
                                viewModel.playLose(player.id)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("lose_button_${player.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Lose", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Standard Saving section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = player.savingInput,
                            onValueChange = { viewModel.updateSavingInput(player.id, it, isWarningField = false) },
                            placeholder = { Text("Saving amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("Saving") },
                            colors = textFieldColors
                        )
                        Button(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                viewModel.addMoneyToSaving(player.id, isWarningField = false)
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    // Peer Borrow and Return buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                onTriggerBorrow()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Borrow", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                onTriggerReturn()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Return Owed", fontSize = 12.sp)
                        }
                    }

                    // Quit Game button
                    Button(
                        onClick = onTriggerQuit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("QUIT GAME FOR ${player.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Read-Only Box (Multiplayer Spectator Mode)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// BORROW DIALOG
// ==========================================
@Composable
fun BorrowDialog(
    player: Player,
    viewModel: GameViewModel,
    allPlayers: List<Player>,
    onDismiss: () -> Unit
) {
    val potentialLenders = allPlayers.filter { it.id != player.id && it.isInitialized }
    var selectedLenderId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Borrow Money from Player") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select the creditor player you wish to borrow money from:")

                if (potentialLenders.isEmpty()) {
                    Text(
                        "No other active initialized players in the game to borrow from.",
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (lender in potentialLenders) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedLenderId = lender.id }
                                    .background(
                                        if (selectedLenderId == lender.id) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.15f
                                        ) else Color.Transparent
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedLenderId == lender.id,
                                        onClick = { selectedLenderId = lender.id }
                                    )
                                    Text(lender.name, fontWeight = FontWeight.Bold)
                                }
                                Text("${lender.currentBalance} ብር", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = player.borrowAmountInput,
                        onValueChange = { input ->
                            val cleanInput = input.filter { it.isDigit() }
                            val amount = cleanInput.toIntOrNull() ?: 0
                            val selectedLender = potentialLenders.firstOrNull { it.id == selectedLenderId }
                            if (selectedLender != null) {
                                if (amount <= selectedLender.currentBalance) {
                                    viewModel.updateBorrowInput(player.id, cleanInput)
                                } else {
                                    viewModel.updateBorrowInput(player.id, selectedLender.currentBalance.toString())
                                }
                            } else {
                                viewModel.updateBorrowInput(player.id, cleanInput)
                            }
                        },
                        label = { Text("Borrow Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedLenderId.isNotEmpty()) {
                        viewModel.borrowMoney(borrowerId = player.id, lenderId = selectedLenderId)
                        onDismiss()
                    }
                },
                enabled = selectedLenderId.isNotEmpty() && 
                          player.borrowAmountInput.isNotEmpty() && 
                          (player.borrowAmountInput.toIntOrNull() ?: 0) > 0 && 
                          (player.borrowAmountInput.toIntOrNull() ?: 0) <= (potentialLenders.firstOrNull { it.id == selectedLenderId }?.currentBalance ?: 0)
            ) {
                Text("Confirm Borrow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// RETURN MONEY DIALOG
// ==========================================
@Composable
fun ReturnMoneyDialog(
    player: Player,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    var selectedCreditorName by remember { mutableStateOf("") }
    val debts = player.debts

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return Borrowed Money") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Choose whom to pay back:")

                if (debts.isEmpty()) {
                    Text("This player does not have any active borrowed debt to pay.", color = Color.Gray)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for ((creditorName, amountOwed) in debts) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCreditorName = creditorName }
                                    .background(
                                        if (selectedCreditorName == creditorName) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.15f
                                        ) else Color.Transparent
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedCreditorName == creditorName,
                                        onClick = { selectedCreditorName = creditorName }
                                    )
                                    Text(creditorName, fontWeight = FontWeight.Bold)
                                }
                                Text("Owes: $amountOwed ብር", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = player.returnAmountInput,
                        onValueChange = { input ->
                            val cleanInput = input.filter { it.isDigit() }
                            val amount = cleanInput.toIntOrNull() ?: 0
                            val maxOwed = debts[selectedCreditorName] ?: 0
                            if (selectedCreditorName.isNotEmpty()) {
                                if (amount <= maxOwed) {
                                    viewModel.updateReturnInput(player.id, cleanInput)
                                } else {
                                    viewModel.updateReturnInput(player.id, maxOwed.toString())
                                }
                            } else {
                                viewModel.updateReturnInput(player.id, cleanInput)
                            }
                        },
                        label = { Text("Amount to Return") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCreditorName.isNotEmpty()) {
                        viewModel.returnMoney(returningId = player.id, creditorName = selectedCreditorName)
                        onDismiss()
                    }
                },
                enabled = selectedCreditorName.isNotEmpty() && 
                          player.returnAmountInput.isNotEmpty() && 
                          (player.returnAmountInput.toIntOrNull() ?: 0) > 0 && 
                          (player.returnAmountInput.toIntOrNull() ?: 0) <= (debts[selectedCreditorName] ?: 0)
            ) {
                Text("Return Money")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ==========================================
// QUIT GAME DIALOG (WITH 3 PROCEDURES)
// ==========================================
@Composable
fun QuitGameDialog(
    player: Player,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    var showReturnSubdialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quit Game Session") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${player.name} has ${player.currentBalance} ብር amount of money left. How do you want to proceed?",
                    fontWeight = FontWeight.Bold
                )

                if (player.debts.isNotEmpty()) {
                    Text(
                        text = "Warning: This player has unsettled debts: ${player.debts}",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Option A: Add saving to bank
                Button(
                    onClick = {
                        viewModel.quitAddSavingToBank(player.id)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add Saving to Bank")
                }

                // Option B: Settle return money first
                Button(
                    onClick = { showReturnSubdialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Settle / Return Borrowed Money")
                }

                // Option C: Leave with money
                Button(
                    onClick = {
                        viewModel.quitLeaveWithMoney(player.id)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Leave with Money")
                }
            }
        },
        confirmButton = {
            // Cancel/Close dialog button
            TextButton(onClick = onDismiss) {
                Text("Cancel / Stay in Game")
            }
        }
    )

    if (showReturnSubdialog) {
        ReturnMoneyDialog(
            player = player,
            viewModel = viewModel,
            onDismiss = { showReturnSubdialog = false }
        )
    }
}

// ==========================================
// IN-APP LOG VIEWER DIALOG
// ==========================================
@Composable
fun LogSummaryDialog(
    logText: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ledger Logs & Statistics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    val scroll = rememberScrollState()
                    Text(
                        text = logText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "Logs Copied to Clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Logs")
                    }

                    Button(
                        onClick = {
                            saveTextToDownloads(context, "banker_agrish_v3_1_log_${System.currentTimeMillis()}.txt", logText)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Dev", color = Color.Black)
                    }
                }
            }
        }
    }
}

// ==========================================
// CARD SEQUENCES LOG VIEWER DIALOG
// ==========================================
@Composable
fun CardLogSummaryDialog(
    logText: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card Deal Sequences Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    val scroll = rememberScrollState()
                    Text(
                        text = logText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "Sequence Logs Copied to Clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Logs")
                    }

                    Button(
                        onClick = {
                            saveTextToDownloads(context, "card_deals_log_${System.currentTimeMillis()}.txt", logText)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Dev", color = Color.Black)
                    }
                }
            }
        }
    }
}

// ==========================================
// CARDS WINDOW TAB CONTENT
// ==========================================
@Composable
fun CardsTabContent(viewModel: GameViewModel, state: GameState) {
    val context = LocalContext.current
    var showCardLogDialog by remember { mutableStateOf(false) }
    var activePlayerForPopup by remember { mutableStateOf<Player?>(null) }
    
    var activeQuitPlayer by remember { mutableStateOf<Player?>(null) }
    var activeBorrowPlayer by remember { mutableStateOf<Player?>(null) }
    var activeReturnPlayer by remember { mutableStateOf<Player?>(null) }

    val currentQuitPlayer = state.players.firstOrNull { it.id == activeQuitPlayer?.id }
    val currentBorrowPlayer = state.players.firstOrNull { it.id == activeBorrowPlayer?.id }
    val currentReturnPlayer = state.players.firstOrNull { it.id == activeReturnPlayer?.id }

    LaunchedEffect(state.glowLeft) {
        if (state.glowLeft && state.soundEnabled) {
            SoundPlayer.playDealSound()
        }
    }
    LaunchedEffect(state.glowRight) {
        if (state.glowRight && state.soundEnabled) {
            SoundPlayer.playDealSound()
        }
    }
    LaunchedEffect(state.glowMiddle) {
        if (state.glowMiddle && state.soundEnabled) {
            SoundPlayer.playDealSound()
        }
    }

    // Floating/Global dialog triggers for Cards page
    if (currentQuitPlayer != null) {
        QuitGameDialog(
            player = currentQuitPlayer,
            viewModel = viewModel,
            onDismiss = { activeQuitPlayer = null }
        )
    }

    if (currentBorrowPlayer != null) {
        BorrowDialog(
            player = currentBorrowPlayer,
            viewModel = viewModel,
            allPlayers = state.players,
            onDismiss = { activeBorrowPlayer = null }
        )
    }

    if (currentReturnPlayer != null) {
        ReturnMoneyDialog(
            player = currentReturnPlayer,
            viewModel = viewModel,
            onDismiss = { activeReturnPlayer = null }
        )
    }

    if (showCardLogDialog) {
        CardLogSummaryDialog(
            logText = viewModel.generateCardLogString(),
            onDismiss = { showCardLogDialog = false }
        )
    }

    if (activePlayerForPopup != null) {
        val playerInState = state.players.firstOrNull { it.id == activePlayerForPopup!!.id }
        if (playerInState != null) {
            Dialog(onDismissRequest = { activePlayerForPopup = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .shadow(8.dp, shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${playerInState.name}'s Quick Bank",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PlayerColors[playerInState.colorIndex % PlayerColors.size]
                            )
                            IconButton(onClick = { activePlayerForPopup = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        PlayerBoxItem(
                            player = playerInState,
                            viewModel = viewModel,
                            state = state,
                            onTriggerBorrow = { activeBorrowPlayer = playerInState },
                            onTriggerReturn = { activeReturnPlayer = playerInState },
                            onTriggerQuit = { activeQuitPlayer = playerInState }
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Header Card: Bank Amount and Player Savings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CURRENT BANK AMOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${state.bankAmount} ብር",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                
                Text(
                    text = "PLAYERS SAVING BALANCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.players.forEach { player ->
                        val borderColor = PlayerColors[player.colorIndex % PlayerColors.size]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = borderColor.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                activePlayerForPopup = player
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = player.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${player.currentBalance} ብር",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // History Navigation Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gameplay History",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.goBack()
                        },
                        enabled = state.canGoBack,
                        modifier = Modifier.testTag("history_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = if (state.canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }

                    IconButton(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.goForward()
                        },
                        enabled = state.canGoForward,
                        modifier = Modifier.testTag("history_forward_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go Forward",
                            tint = if (state.canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // 6 Cards Left Warning
        if (state.leftDeck.size == 6) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "⚠️ 6 CARDS LEFT!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "There are exactly 6 cards left in the deck. Shuffle recommended soon!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Warnings for low card decks
        val isLeftDeckLow = state.leftDeck.size < 5
        val isRightDeckLow = state.rightDeck.size < 5
        if (isLeftDeckLow || isRightDeckLow) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        val warningText = buildString {
                            if (isLeftDeckLow && isRightDeckLow) {
                                append("Both left and right decks have less than 5 cards left!")
                            } else if (isLeftDeckLow) {
                                append("Left deck has less than 5 cards left!")
                            } else {
                                append("Right deck has less than 5 cards left!")
                            }
                        }
                        Text(
                            text = "Deck Warning",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = warningText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Double Cards Display: Two boxes next to each other
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Card Display Box
            CardBox(
                title = "Left Display",
                cards = state.leftDealt,
                modifier = Modifier.weight(1f),
                glow = state.glowLeft,
                originX = 60.dp,
                originY = 280.dp
            )

            // Right Card Display Box
            CardBox(
                title = "Right Display",
                cards = state.rightDealt,
                modifier = Modifier.weight(1f),
                glow = state.glowRight,
                originX = (-60).dp,
                originY = 280.dp
            )
        }

        // Play middle card section
        val middleGlowColor = when (state.cardsPlayResult) {
            "WIN" -> Color(0xFF2ECC71)
            "LOSE" -> Color(0xFFE74C3C)
            else -> MaterialTheme.colorScheme.tertiary
        }
        val middleBorder = if (state.glowMiddle) {
            BorderStroke(3.dp, middleGlowColor)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
        val middleShadowModifier = if (state.glowMiddle) {
            Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), ambientColor = middleGlowColor, spotColor = middleGlowColor)
        } else {
            Modifier
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(middleShadowModifier),
            colors = CardDefaults.cardColors(
                containerColor = if (state.glowMiddle) {
                    when (state.cardsPlayResult) {
                        "WIN" -> Color(0xFF2ECC71).copy(alpha = 0.1f)
                        "LOSE" -> Color(0xFFE74C3C).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                    }
                } else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = middleBorder,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MIDDLE PLAY CARD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                if (state.middleCard != null) {
                    FancyThrowCardContainer(
                        card = state.middleCard,
                        originX = (-80).dp,
                        originY = 200.dp
                    ) {
                        PlayingCardView(card = state.middleCard)
                    }

                    AnimatedVisibility(
                        visible = state.cardsPlayResult != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.cardsPlayResult == "WIN") "🎉 WIN! Card fell between." else "😢 LOSE! Card is outside or equal.",
                                color = if (state.cardsPlayResult == "WIN") Color(0xFF2ECC71) else Color(0xFFE74C3C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp).testTag("cards_play_result_text")
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(75.dp, 105.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "EMPTY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // Deck States & Deck warning
        if (state.leftDeck.isEmpty() || state.rightDeck.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Decks Empty! Please reset decks to continue.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = {
                            SoundPlayer.playClick(state.vibrationEnabled)
                            viewModel.resetCardDecks()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("RESET CARD DECKS")
                    }
                }
            }
        }

        if (state.isMultiplayer) {
            val activePlayer = state.players.firstOrNull { it.id == state.activeTurnPlayerId }
            val isMyTurn = state.activeTurnPlayerId == state.localPlayerId
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MULTIPLAYER GAMEPLAY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    
                    if (activePlayer != null) {
                        val activeColor = PlayerColors[activePlayer.colorIndex % PlayerColors.size]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(activeColor)
                            )
                            Text(
                                text = if (isMyTurn) "👉 It's YOUR Turn (${activePlayer.name})" else "🕒 Turn: ${activePlayer.name}...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (state.wheelMessage.isNotEmpty()) {
                        Text(
                            text = state.wheelMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    when (state.multiplayerTurnState) {
                        "DEAL_TWO_CARDS" -> {
                            Text("Dealing first two cards...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        "PLAY_OR_PASS" -> {
                            if (isMyTurn) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.sendActionToHost("PLAY")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text("PLAY", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.sendActionToHost("PASS")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text("PASS", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else {
                                Text("Waiting for ${activePlayer?.name ?: "active player"} to Play or Pass...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        "INPUT_AMOUNT" -> {
                            if (isMyTurn) {
                                var tempBet by remember { mutableStateOf("") }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = tempBet,
                                        onValueChange = { tempBet = it },
                                        label = { Text("Play Amount") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.sendActionToHost("ENTER_AMOUNT:$tempBet")
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("SUBMIT AMOUNT", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text("Waiting for ${activePlayer?.name ?: "active player"} to enter amount...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        "DEAL_MIDDLE" -> {
                            val betAmount = state.currentPlayAmountInput
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Play Amount: $betAmount ብር", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (state.isHost) {
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.sendActionToHost("DEAL_MIDDLE")
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("DEAL MIDDLE CARD", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text("Waiting for Host to deal the middle card...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                        "RESULT" -> {
                            val betAmount = state.currentPlayAmountInput
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Play Amount: $betAmount ብር", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (state.cardsPlayResult == "WIN") {
                                    Text("🎉 WINNER!", color = Color(0xFF2ECC71), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                } else {
                                    Text("😢 LOST!", color = Color(0xFFE74C3C), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                }
                                
                                if (state.isHost) {
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.sendActionToHost("NEXT_PLAYER")
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("CONTINUE TO NEXT PLAYER", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text("Waiting for Host to advance turn...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Gameplay Buttons Row
            val canPlay = state.leftDealt.isNotEmpty() && state.rightDealt.isNotEmpty() && state.leftDealt.size == state.rightDealt.size && state.middleCard == null

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deal Cards Column
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                viewModel.dealCards()
                            },
                            enabled = state.leftDeck.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("deal_cards_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Text("DEAL CARDS", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${state.leftDeck.size} Cards Left",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    // Play Button Column
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                SoundPlayer.playClick(state.vibrationEnabled)
                                viewModel.playMiddleCard()
                            },
                            enabled = canPlay,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("play_cards_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canPlay) MaterialTheme.colorScheme.tertiary else Color.Gray.copy(alpha = 0.4f),
                                contentColor = if (canPlay) Color.Black else Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (canPlay) Color.Black else Color.White.copy(alpha = 0.4f))
                                Text("PLAY", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (!canPlay) {
                    Text(
                        text = "please deal a new card on the right/left side or or on both displays",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    SoundPlayer.playClick(state.vibrationEnabled)
                    viewModel.resetCardDecks()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("RESET & SHUFFLE DECKS")
                }
            }
        }

        Button(
            onClick = {
                SoundPlayer.playClick(state.vibrationEnabled)
                showCardLogDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_card_log_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Text("GENERATE LOG", fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

// Custom throwing animation container for dealing and playing cards
@Composable
fun FancyThrowCardContainer(
    card: Card,
    originX: androidx.compose.ui.unit.Dp,
    originY: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val originXInPx = with(density) { originX.toPx() }
    val originYInPx = with(density) { originY.toPx() }

    val animX = remember { androidx.compose.animation.core.Animatable(originXInPx) }
    val animY = remember { androidx.compose.animation.core.Animatable(originYInPx) }
    val animScale = remember { androidx.compose.animation.core.Animatable(0.2f) }
    val animRotation = remember { androidx.compose.animation.core.Animatable(-180f) }

    LaunchedEffect(card) {
        animX.snapTo(originXInPx)
        animY.snapTo(originYInPx)
        animScale.snapTo(0.2f)
        animRotation.snapTo(-180f)

        val spec = androidx.compose.animation.core.tween<Float>(
            durationMillis = 650,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )

        launch {
            animX.animateTo(0f, spec)
        }
        launch {
            animY.animateTo(0f, spec)
        }
        launch {
            animScale.animateTo(1f, spec)
        }
        launch {
            animRotation.animateTo(0f, spec)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = animX.value
                translationY = animY.value
                scaleX = animScale.value
                scaleY = animScale.value
                rotationZ = animRotation.value
            }
    ) {
        content()
    }
}

// Helper card box for displaying dealt history and upside down stack
@Composable
fun CardBox(
    title: String,
    cards: List<Card>,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    originX: androidx.compose.ui.unit.Dp = 0.dp,
    originY: androidx.compose.ui.unit.Dp = 0.dp
) {
    val glowColor = MaterialTheme.colorScheme.primary
    val borderStroke = if (glow) {
        BorderStroke(3.dp, glowColor)
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    }
    
    val shadowModifier = if (glow) {
        Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), ambientColor = glowColor, spotColor = glowColor)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .height(180.dp)
            .then(shadowModifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        border = borderStroke,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            // Dealt display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (cards.isEmpty()) {
                    Text(
                        "No Dealt Cards",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    // Show only the latest card (fully covering bottom cards)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FancyThrowCardContainer(
                            card = cards.last(),
                            originX = originX,
                            originY = originY
                        ) {
                            PlayingCardView(card = cards.last())
                        }
                    }
                }
            }
        }
    }
}

// Vector-like rendering of a beautiful playing card in Jetpack Compose
@Composable
fun PlayingCardView(card: Card, modifier: Modifier = Modifier.size(70.dp, 100.dp)) {
    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, Color.Black.copy(alpha = 0.8f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cardWidth = maxWidth
            val scale = (cardWidth / 70.dp).coerceIn(0.4f, 2.0f)
            val suitColor = if (card.suit.colorRed) Color(0xFFC0392B) else Color.Black

            // Top-left rank and suit
            Column(
                modifier = Modifier
                    .padding((4 * scale).dp)
                    .align(Alignment.TopStart),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.rank.display,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = suitColor,
                    lineHeight = (12 * scale).sp
                )
                Text(
                    text = card.suit.symbol,
                    fontSize = (11 * scale).sp,
                    color = suitColor,
                    lineHeight = (10 * scale).sp
                )
            }

            // Big center suit icon
            Text(
                text = card.suit.symbol,
                fontSize = (32 * scale).sp,
                color = suitColor,
                modifier = Modifier.align(Alignment.Center)
            )

            // Bottom-right rank and suit (inverted rotation)
            Column(
                modifier = Modifier
                    .padding((4 * scale).dp)
                    .align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.suit.symbol,
                    fontSize = (11 * scale).sp,
                    color = suitColor,
                    lineHeight = (10 * scale).sp
                )
                Text(
                    text = card.rank.display,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = suitColor,
                    lineHeight = (12 * scale).sp
                )
            }
        }
    }
}

// ==========================================
// MEDIASTORE DOWNLOADS FILE WRITER & FALLBACK
// ==========================================
fun saveTextToDownloads(context: Context, fileName: String, content: String) {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            null
        }
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.write(content.toByteArray())
            }
            Toast.makeText(context, "Ledger downloaded successfully to Downloads folder", Toast.LENGTH_LONG).show()
        } else {
            shareTextFallback(context, content)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        shareTextFallback(context, content)
    }
}

fun shareTextFallback(context: Context, content: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_SUBJECT, "ባንክ ከአግርሽ ጋር 5.0 Ledger Export")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Ledger Log"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting log: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================
// POKER GAME ENGINE UI MODULES & COMPONENTS
// ==========================================

@Composable
fun GameSelectionScreen(
    viewModel: GameViewModel,
    state: GameState
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0C)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_custom),
            contentDescription = "Game Selection Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.35f
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Show Back button if in a sub-mode
            if (state.playModeSelection != "NONE") {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectPlayMode("NONE")
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            }

            Text(
                text = "GAME HUB",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD700),
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val subtitleText = when (state.playModeSelection) {
                "DEVICE" -> "Select a game to play offline on this device"
                "WIFI" -> "Select multiplayer mode to play over Wi-Fi"
                else -> "Select how you would like to play today"
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (state.playModeSelection == "NONE") {
                // Play on Device Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectPlayMode("DEVICE")
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_device_mode"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Device Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Play on Device",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Play solo with AI Bots or Pass & Play with friends offline.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Play over Wi-Fi Multiplayer Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectPlayMode("WIFI")
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_wifi_mode"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Wi-Fi Multiplayer",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Host or Join a game with friends over local Wi-Fi or Cloud.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else if (state.playModeSelection == "DEVICE") {
                // Bank Game (Pass & Play offline)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectGame("BANK")
                            viewModel.resetFullGame()
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_bank_offline"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "Bank Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "ባንክ ከአግርሽ ጋር 5.0",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Play local offline pass-and-play bank facilitator & card trading.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Poker Game (Offline Bots)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectGame("POKER")
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_poker_offline"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = "Poker Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Poker Club (AI Bots)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Play high-stakes Texas Hold'em against local offline AI bots.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else if (state.playModeSelection == "WIFI") {
                // Bank Game (Multiplayer over Wi-Fi / Cloud)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectGame("BANK")
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_bank_wifi"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "Bank Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "ባንክ ከአግርሽ ጋር 5.0 (Wi-Fi)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Host or join multiplayer Bank sessions on Wi-Fi or Cloud.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Poker Game (Multiplayer over Wi-Fi / Cloud)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clickable {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.selectGame("POKER")
                        }
                        .padding(vertical = 12.dp)
                        .testTag("select_poker_wifi"),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF1E1E1C), CircleShape)
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = "Poker Icon",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Poker Club (Wi-Fi)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Host or join high-stakes Poker tables on Wi-Fi or Cloud.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start Music Toggle Button
            var musicEnabledState by remember { mutableStateOf(SoundPlayer.startMusicEnabled) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFD700).copy(alpha = 0.08f))
                    .clickable {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        val next = !musicEnabledState
                        musicEnabledState = next
                        viewModel.toggleStartMusic(next)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (musicEnabledState) Icons.Default.MusicNote else Icons.Default.MusicOff,
                    contentDescription = "Toggle Start Music",
                    tint = if (musicEnabledState) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (musicEnabledState) "Start Music On" else "Start Music Off",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (musicEnabledState) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PokerWelcomeScreen(
    viewModel: GameViewModel,
    state: GameState
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf("") }
    var inputGameId by remember { mutableStateOf("") }
    var initialChipsInput by remember { mutableStateOf("1000") }
    var aiCountInput by remember { mutableStateOf("3") }
    
    var pokerModeSelection by remember {
        mutableStateOf<String?>(
            if (state.playModeSelection == "DEVICE") "bots" else null
        )
    }
    var showCameraScanner by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0E)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_custom),
            contentDescription = "Welcome Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pokerModeSelection == null) {
                    // Return to game hub & screen rotation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.exitGameSelection()
                            },
                            modifier = Modifier.testTag("exit_poker_hub")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                val activity = context as? Activity
                                activity?.let {
                                    val current = it.requestedOrientation
                                    if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF1E1E1C), CircleShape)
                            .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Poker Club Logo",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Poker Club",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = "High-stakes Texas Hold'em. Play offline against local bots or over Wi-Fi with friends.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Enter Name
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Your Name", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("poker_name_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Resume Game if applicable
                    if (state.canResume && state.pokerPlayers.isNotEmpty()) {
                        Button(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.resumeGame()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("poker_resume_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume Previous Poker Game", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (state.playModeSelection != "WIFI") {
                        // Play offline with bots
                        Button(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                pokerModeSelection = "bots"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("poker_play_bots_btn")
                        ) {
                            Icon(Icons.Default.Android, contentDescription = "Bots", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play with AI Bots", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
 
                    if (state.playModeSelection != "DEVICE") {
                        // Host game
                        Button(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                pokerModeSelection = "host"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("poker_host_btn")
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = "Host", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Host Wi-Fi Game", fontWeight = FontWeight.Bold, color = Color.White)
                        }
 
                        // Join game
                        Button(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                pokerModeSelection = "join"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("poker_join_btn")
                        ) {
                            Icon(Icons.Default.LeakAdd, contentDescription = "Join", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Wi-Fi Game", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else if (pokerModeSelection == "bots") {
                    // PLAY WITH AI BOTS SUB-PAGE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                if (state.playModeSelection == "DEVICE") {
                                    viewModel.exitGameSelection()
                                } else {
                                    pokerModeSelection = null
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play with AI Bots",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = aiCountInput,
                        onValueChange = { aiCountInput = it },
                        label = { Text("Number of Bots (1 - 5)", color = Color.White.copy(alpha = 0.6f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = initialChipsInput,
                        onValueChange = { initialChipsInput = it },
                        label = { Text("Starting Chips", color = Color.White.copy(alpha = 0.6f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            val count = aiCountInput.toIntOrNull()?.coerceIn(1, 5) ?: 3
                            val chips = initialChipsInput.toIntOrNull()?.coerceAtLeast(100) ?: 1000
                            viewModel.startPokerSoloGame(count, chips, inputName.ifBlank { "Player" })
                            pokerModeSelection = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("PLAY NOW", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else if (pokerModeSelection == "host") {
                    // HOST MULTIPLAYER GAME SUB-PAGE
                    var connectionType by remember { mutableStateOf("cloud") } // "cloud" or "local"
                    var portInput by remember { mutableStateOf("8888") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                pokerModeSelection = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Host Wi-Fi Game",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { connectionType = "cloud" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "cloud") Color(0xFFFFD700) else Color.Transparent, contentColor = if (connectionType == "cloud") Color.Black else Color.White),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cloud (Firebase)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { connectionType = "local" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "local") Color(0xFFFFD700) else Color.Transparent, contentColor = if (connectionType == "local") Color.Black else Color.White),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Local Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (connectionType == "local") {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port Number", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("8888") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = initialChipsInput,
                        onValueChange = { initialChipsInput = it },
                        label = { Text("Starting Chips", color = Color.White.copy(alpha = 0.6f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            val chips = initialChipsInput.toIntOrNull()?.coerceAtLeast(100) ?: 1000
                            if (connectionType == "local") {
                                val port = portInput.trim().toIntOrNull() ?: 8888
                                viewModel.startLocalWifiHost(
                                    playerName = inputName.ifBlank { "Host" },
                                    port = port,
                                    isPoker = true,
                                    chips = chips
                                )
                            } else {
                                viewModel.hostPokerGame(inputName.ifBlank { "Host" }, chips)
                            }
                            pokerModeSelection = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("HOST LOBBY", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else if (pokerModeSelection == "join") {
                    // JOIN MULTIPLAYER GAME SUB-PAGE
                    var connectionType by remember { mutableStateOf("cloud") } // "cloud" or "local"
                    var hostIpInput by remember { mutableStateOf(com.example.network.PokerNetworkManager.localIpAddress) }
                    var portInput by remember { mutableStateOf("8888") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                pokerModeSelection = null
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Join Wi-Fi Game",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { connectionType = "cloud" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "cloud") Color(0xFFFFD700) else Color.Transparent, contentColor = if (connectionType == "cloud") Color.Black else Color.White),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cloud (Firebase)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { connectionType = "local" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (connectionType == "local") Color(0xFFFFD700) else Color.Transparent, contentColor = if (connectionType == "local") Color.Black else Color.White),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Local Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Your Name", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = initialChipsInput,
                        onValueChange = { initialChipsInput = it },
                        label = { Text("Initial Game Money", color = Color.White.copy(alpha = 0.6f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val canJoinOrScan = inputName.trim().isNotEmpty() && initialChipsInput.trim().isNotEmpty()

                    if (connectionType == "local") {
                        OutlinedTextField(
                            value = hostIpInput,
                            onValueChange = { hostIpInput = it },
                            label = { Text("Host IP Address (e.g., 192.168.1.100)", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canJoinOrScan
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port Number", color = Color.White.copy(alpha = 0.6f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canJoinOrScan
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputGameId,
                                onValueChange = { inputGameId = it },
                                label = { Text("Enter Game ID", color = Color.White.copy(alpha = 0.6f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f),
                                enabled = canJoinOrScan
                            )
                            
                            IconButton(
                                onClick = {
                                    SoundPlayer.playClick(context, state.vibrationEnabled)
                                    if (cameraPermissionState.status.isGranted) {
                                        showCameraScanner = true
                                    } else {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                                enabled = canJoinOrScan,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (canJoinOrScan) Color(0xFF222222) else Color(0xFF111111), RoundedCornerShape(8.dp))
                                    .border(1.dp, if (canJoinOrScan) Color(0xFFFFD700).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "Scan QR Code", tint = if (canJoinOrScan) Color(0xFFFFD700) else Color.Gray)
                            }
                        }
                    }

                    if (!canJoinOrScan) {
                        Text(
                            text = "⚠️ Enter name & initial game money to scan or join",
                            color = Color(0xFFFF8F00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            val chips = initialChipsInput.toIntOrNull()?.coerceAtLeast(100) ?: 1000
                            if (connectionType == "local") {
                                if (hostIpInput.trim().isNotEmpty()) {
                                    val port = portInput.trim().toIntOrNull() ?: 8888
                                    viewModel.connectLocalWifiClient(
                                        playerName = inputName.ifBlank { "Guest" },
                                        hostIp = hostIpInput.trim(),
                                        port = port,
                                        isPoker = true,
                                        chips = chips
                                    )
                                    pokerModeSelection = null
                                } else {
                                    Toast.makeText(context, "Please enter the Host's IP address", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val gameIdTrimmed = inputGameId.trim().uppercase()
                                if (gameIdTrimmed.isNotEmpty()) {
                                    viewModel.joinPokerGame(gameIdTrimmed, inputName.ifBlank { "Guest" }, chips)
                                    pokerModeSelection = null
                                } else {
                                    Toast.makeText(context, "Please enter a Game ID", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = canJoinOrScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canJoinOrScan) Color(0xFFFFD700) else Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("JOIN NOW", color = if (canJoinOrScan) Color.Black else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCameraScanner) {
        CameraScannerView(
            onQrCodeScanned = { scannedId ->
                val cleanedId = scannedId.trim()
                if (cleanedId.length == 8) {
                    inputGameId = cleanedId
                    showCameraScanner = false
                    val chips = initialChipsInput.toIntOrNull()?.coerceAtLeast(100) ?: 1000
                    viewModel.joinPokerGame(cleanedId, inputName.ifBlank { "Guest" }, chips)
                    pokerModeSelection = null
                } else {
                    Toast.makeText(context, "Invalid QR code. Expected an 8-character game ID.", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = {
                showCameraScanner = false
            }
        )
    }
}

@Composable
fun PokerLobbyScreen(
    viewModel: GameViewModel,
    state: GameState
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0E)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_custom),
            contentDescription = "Lobby Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Poker Game Lobby",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Share this Game ID with friends to join:",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                // Large Glowing Game ID
                Surface(
                    color = Color(0xFF1E1E1C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD700)),
                    modifier = Modifier
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Game ID", state.gameId))
                            Toast.makeText(context, "Game ID copied!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = state.gameId,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 2.sp
                    )
                }

                if (state.isLocalWifi) {
                    GameLinkShareCard(
                        ip = com.example.network.PokerNetworkManager.localIpAddress,
                        port = 8080
                    )
                }

                var showQrCode by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = {
                        SoundPlayer.playClick(context, state.vibrationEnabled)
                        showQrCode = !showQrCode
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (showQrCode) Icons.Default.VisibilityOff else Icons.Default.QrCode, contentDescription = null)
                        Text(if (showQrCode) "HIDE JOIN QR CODE" else "SHOW JOIN QR CODE")
                    }
                }

                if (showQrCode) {
                    val qrBitmap = remember(state.gameId) {
                        QrCodeHelper.generateQrCode(state.gameId, 256)
                    }
                    if (qrBitmap != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Game Join QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Players Connected (${state.lobbyPlayers.size}):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.lobbyPlayers) { pName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFFD700), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(pName.take(1), fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(pName, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isHost) {
                    Button(
                        onClick = {
                            SoundPlayer.playClick(context, state.vibrationEnabled)
                            viewModel.startPokerLobbyGame()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = state.lobbyPlayers.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Start Poker Game", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (state.lobbyPlayers.size < 2) {
                        Text(
                            text = "Need at least 2 players to start game.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                    Text("Waiting for host to start...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PlayingCardBackView(modifier: Modifier = Modifier.size(70.dp, 100.dp)) {
    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8B0000)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, Color(0xFFFFD700))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(Color(0xFF6B0000), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            val cardWidth = maxWidth
            val scale = (cardWidth / 70.dp).coerceIn(0.4f, 2.0f)
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = "Card Back Icon",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size((36 * scale).dp)
            )
        }
    }
}

@Composable
fun RealTimeRankingBoard(
    title: String,
    players: List<Pair<String, Int>>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedPlayers = remember(players) {
        players.sortedByDescending { it.second }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title.replace("🏆 ", "").replace("🏆", ""),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!expanded && sortedPlayers.isNotEmpty()) {
                        val topPlayer = sortedPlayers[0]
                        Text(
                            text = "Leader: ${topPlayer.first} (${topPlayer.second})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFD700) else Color(0xFFB59410)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sortedPlayers.forEachIndexed { index, pair ->
                        val rankLabel = "${index + 1}."
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (index == 0) {
                                        Color(0xFFFFD700).copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = rankLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.LightGray else Color.DarkGray
                                )
                                Text(
                                    text = pair.first,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                            }
                            Text(
                                text = "${pair.second}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) {
                                    if (isDark) Color(0xFFFFD700) else Color(0xFFB59410)
                                } else {
                                    if (isDark) Color.LightGray else Color.DarkGray
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniCardView(card: Card?, isFolded: Boolean = false) {
    if (card != null) {
        val suitColor = if (card.suit.colorRed) Color(0xFFC0392B) else Color.Black
        Card(
            modifier = Modifier
                .size(24.dp, 36.dp)
                .shadow(1.dp, RoundedCornerShape(3.dp)),
            colors = CardDefaults.cardColors(containerColor = if (isFolded) Color.Gray else Color.White),
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = card.rank.display,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = suitColor,
                        lineHeight = 8.sp
                    )
                    Text(
                        text = card.suit.symbol,
                        fontSize = 8.sp,
                        color = suitColor,
                        lineHeight = 7.sp
                    )
                }
            }
        }
    } else {
        // Card Back
        Card(
            modifier = Modifier
                .size(24.dp, 36.dp)
                .shadow(1.dp, RoundedCornerShape(3.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFC0392B)),
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFC0392B)),
                contentAlignment = Alignment.Center
            ) {
                Text("♠", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

fun getPokerTipsLocal(
    playerCards: List<Card>,
    communityCards: List<Card>,
    stage: String,
    bestHand: HandEvaluation
): String {
    if (playerCards.size < 2) return "Waiting for cards..."
    val card1 = playerCards[0]
    val card2 = playerCards[1]
    
    val val1 = if (card1.rank == CardRank.ACE) 14 else card1.rank.value
    val val2 = if (card2.rank == CardRank.ACE) 14 else card2.rank.value
    
    val isPocketPair = val1 == val2
    val isSuited = card1.suit == card2.suit
    val isConnector = Math.abs(val1 - val2) == 1
    
    if (stage == "PRE_FLOP" || communityCards.isEmpty()) {
        return when {
            isPocketPair && val1 >= 11 -> "🔥 Monster Pocket Pair (${card1.rank.display}s)! Excellent starting hand. Raise aggressively to build the pot!"
            isPocketPair && val1 >= 8 -> "✨ Good Pocket Pair (${card1.rank.display}s). Strong hand. Call or raise to see the flop."
            isPocketPair -> "🃏 Small Pocket Pair (${card1.rank.display}s). Worth playing cheaply, but watch for overcards."
            val1 >= 13 && val2 >= 13 -> "🚀 Two High Cards! Excellent starting hand. Call or raise to take control."
            isSuited && isConnector && val1 >= 8 -> "💡 Suited Connectors! Great straight and flush potential. Worth playing."
            isSuited && (val1 == 14 || val2 == 14) -> "🎯 Suited Ace! Excellent flush potential. Worth seeing the flop."
            val1 >= 11 || val2 >= 11 -> "👍 Single High Card. Decent to play, but be cautious if others raise."
            isConnector -> "🔗 Connectors. Has straight potential, but play cheaply."
            else -> "⚠️ Weak starting hand. High card is only ${maxOf(val1, val2)}. Consider folding if there is heavy action."
        }
    } else {
        // Draw potentials
        val combined = playerCards + communityCards
        val suitsCount = combined.groupBy { it.suit }.mapValues { it.value.size }
        val hasFlushDraw = suitsCount.any { it.value == 4 }
        
        val vals = combined.map { if (it.rank == CardRank.ACE) 14 else it.rank.value }.distinct().sorted()
        var hasStraightDraw = false
        if (vals.size >= 4) {
            for (i in 0..vals.size - 4) {
                if (vals[i + 3] - vals[i] == 3) {
                    hasStraightDraw = true
                    break
                }
            }
        }
        
        return when (bestHand.rank) {
            HandRank.ROYAL_FLUSH, HandRank.STRAIGHT_FLUSH, HandRank.FOUR_OF_A_KIND, HandRank.FULL_HOUSE -> {
                "👑 Monster Hand (${bestHand.description})! You are practically unbeatable. Let others bet, then raise big!"
            }
            HandRank.FLUSH -> {
                "🌊 Flush! Very strong. Protect it with a solid bet, but watch for pairs on the board."
            }
            HandRank.STRAIGHT -> {
                "🌈 Straight! Strong hand. Bet to charge players with draws, but keep an eye out for flushes."
            }
            HandRank.THREE_OF_A_KIND -> {
                "⚡ Three of a Kind! Excellent hand. Try to build a bigger pot, but be careful of coordinated boards."
            }
            HandRank.TWO_PAIR -> {
                "💥 Two Pair! Strong but vulnerable. Make a medium bet to thin out the field."
            }
            HandRank.ONE_PAIR -> {
                val isHighPair = (bestHand.values.getOrNull(0) ?: 0) >= 11
                if (isHighPair) {
                    "Top Pair! Solid hand. Worth betting/calling, but be cautious of aggressive raises."
                } else {
                    "Mid/Low Pair. Vulnerable. Check and call small bets, fold to major strength."
                }
            }
            HandRank.HIGH_CARD -> {
                when {
                    hasFlushDraw && hasStraightDraw -> "⭐ Double Draw! Flush and Straight draws. Call reasonable bets."
                    hasFlushDraw -> "💧 Flush Draw! One card away from a flush. Worth calling moderate bets."
                    hasStraightDraw -> "✏️ Straight Draw! One card away from a straight. Worth staying in if cheap."
                    else -> "❌ High Card only. No pairs or draws. Folding is the safest play."
                }
            }
        }
    }
}

@Composable
fun PokerRulesHelpView(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { onDismiss() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {}
                .border(2.dp, Color(0xFFFFD700), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏆 POKER HAND RANKINGS",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        RuleHelpItem(
                            title = "1. Royal Flush",
                            desc = "The ultimate poker hand: Ten, Jack, Queen, King, and Ace of the same suit.",
                            cards = listOf(
                                Card(CardSuit.CLUBS, CardRank.TEN),
                                Card(CardSuit.CLUBS, CardRank.JACK),
                                Card(CardSuit.CLUBS, CardRank.QUEEN),
                                Card(CardSuit.CLUBS, CardRank.KING),
                                Card(CardSuit.CLUBS, CardRank.ACE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "2. Straight Flush",
                            desc = "Five consecutive cards of the same suit.",
                            cards = listOf(
                                Card(CardSuit.SPADES, CardRank.FIVE),
                                Card(CardSuit.SPADES, CardRank.SIX),
                                Card(CardSuit.SPADES, CardRank.SEVEN),
                                Card(CardSuit.SPADES, CardRank.EIGHT),
                                Card(CardSuit.SPADES, CardRank.NINE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "3. Four of a Kind",
                            desc = "Four cards of the same rank.",
                            cards = listOf(
                                Card(CardSuit.HEARTS, CardRank.ACE),
                                Card(CardSuit.DIAMONDS, CardRank.ACE),
                                Card(CardSuit.CLUBS, CardRank.ACE),
                                Card(CardSuit.SPADES, CardRank.ACE),
                                Card(CardSuit.HEARTS, CardRank.FIVE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "4. Full House",
                            desc = "Three of a kind combined with a pair.",
                            cards = listOf(
                                Card(CardSuit.HEARTS, CardRank.KING),
                                Card(CardSuit.DIAMONDS, CardRank.KING),
                                Card(CardSuit.CLUBS, CardRank.KING),
                                Card(CardSuit.SPADES, CardRank.TEN),
                                Card(CardSuit.HEARTS, CardRank.TEN)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "5. Flush",
                            desc = "Five cards of the same suit, not in sequence.",
                            cards = listOf(
                                Card(CardSuit.DIAMONDS, CardRank.ACE),
                                Card(CardSuit.DIAMONDS, CardRank.JACK),
                                Card(CardSuit.DIAMONDS, CardRank.EIGHT),
                                Card(CardSuit.DIAMONDS, CardRank.SIX),
                                Card(CardSuit.DIAMONDS, CardRank.THREE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "6. Straight",
                            desc = "Five consecutive cards of different suits.",
                            cards = listOf(
                                Card(CardSuit.CLUBS, CardRank.FIVE),
                                Card(CardSuit.DIAMONDS, CardRank.SIX),
                                Card(CardSuit.HEARTS, CardRank.SEVEN),
                                Card(CardSuit.SPADES, CardRank.EIGHT),
                                Card(CardSuit.CLUBS, CardRank.NINE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "7. Three of a Kind",
                            desc = "Three cards of the same rank.",
                            cards = listOf(
                                Card(CardSuit.HEARTS, CardRank.QUEEN),
                                Card(CardSuit.DIAMONDS, CardRank.QUEEN),
                                Card(CardSuit.CLUBS, CardRank.QUEEN),
                                Card(CardSuit.SPADES, CardRank.ACE),
                                Card(CardSuit.HEARTS, CardRank.TWO)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "8. Two Pair",
                            desc = "Two different pairs.",
                            cards = listOf(
                                Card(CardSuit.CLUBS, CardRank.JACK),
                                Card(CardSuit.DIAMONDS, CardRank.JACK),
                                Card(CardSuit.HEARTS, CardRank.NINE),
                                Card(CardSuit.SPADES, CardRank.NINE),
                                Card(CardSuit.DIAMONDS, CardRank.FOUR)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "9. One Pair",
                            desc = "Two cards of the same rank.",
                            cards = listOf(
                                Card(CardSuit.CLUBS, CardRank.TEN),
                                Card(CardSuit.DIAMONDS, CardRank.TEN),
                                Card(CardSuit.HEARTS, CardRank.KING),
                                Card(CardSuit.SPADES, CardRank.EIGHT),
                                Card(CardSuit.DIAMONDS, CardRank.FIVE)
                            )
                        )
                    }
                    item {
                        RuleHelpItem(
                            title = "10. High Card",
                            desc = "No hand combinations; highest card wins.",
                            cards = listOf(
                                Card(CardSuit.SPADES, CardRank.ACE),
                                Card(CardSuit.DIAMONDS, CardRank.QUEEN),
                                Card(CardSuit.HEARTS, CardRank.EIGHT),
                                Card(CardSuit.SPADES, CardRank.FIVE),
                                Card(CardSuit.CLUBS, CardRank.TWO)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RuleHelpItem(
    title: String,
    desc: String,
    cards: List<Card>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 13.sp)
            Text(desc, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEach { card ->
                    Card(
                        modifier = Modifier.size(22.dp, 32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val suitColor = if (card.suit.colorRed) Color(0xFFC0392B) else Color.Black
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(card.rank.display, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = suitColor, lineHeight = 7.sp)
                                Text(card.suit.symbol, fontSize = 7.sp, color = suitColor, lineHeight = 6.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

val avatarEmojis = listOf("🐱", "🐶", "🐟", "🐯", "🐘", "🌸", "🤖", "🐻", "🦁", "🦖", "🐼", "🦊", "🐝", "🐥", "👾")

fun getPokerDetailedHelp(
    playerCards: List<Card>,
    communityCards: List<Card>,
    bestHand: HandEvaluation
): String {
    if (playerCards.size < 2) return "Detailed poker assistant will analyze your hands here once dealt."
    val card1 = playerCards[0]
    val card2 = playerCards[1]
    val r1 = card1.rank
    val r2 = card2.rank
    val sameSuit = card1.suit == card2.suit
    val isPair = r1 == r2
    
    val sb = java.lang.StringBuilder()
    if (isPair) {
        sb.append("🔥 Pair of ${r1.display}s! Great pre-flop strength. 12% set potential on flop.\n\n")
    } else if (sameSuit) {
        sb.append("💧 Suited: High flush potential in ${card1.suit.name.lowercase().capitalize()}!\n\n")
    } else {
        sb.append("⚠️ Unsuited: Moderate starting strength.\n\n")
    }
    
    if (communityCards.isEmpty()) {
        sb.append("💡 Pre-Flop: Position is key! Raise aggressively if premium, otherwise call cheaply to see the Flop.")
    } else {
        sb.append("Current hand: ${bestHand.description}\n\n")
        val combined = playerCards + communityCards
        val suitsCount = combined.groupBy { it.suit }.mapValues { it.value.size }
        val maxSuitCount = suitsCount.values.maxOrNull() ?: 0
        if (maxSuitCount == 4) {
            sb.append("🎯 FLUSH DRAW! Need 1 card for a complete Flush.\n\n")
        } else if (maxSuitCount >= 5) {
            sb.append("👑 FLUSH MADE! Strong, extract value from opponents.\n\n")
        }
        
        val values = combined.map { if (it.rank == CardRank.ACE) 14 else it.rank.value }.distinct().sorted()
        var hasDraw = false
        if (values.size >= 4) {
            for (i in 0..values.size - 4) {
                if (values[i + 3] - values[i] == 3) {
                    hasDraw = true
                    break
                }
            }
        }
        if (hasDraw && bestHand.rank < HandRank.STRAIGHT) {
            sb.append("🔗 STRAIGHT DRAW! Sequential run active.\n\n")
        }
        
        sb.append("💡 Verdict: ")
        when (bestHand.rank) {
            HandRank.ROYAL_FLUSH, HandRank.STRAIGHT_FLUSH, HandRank.FOUR_OF_A_KIND, HandRank.FULL_HOUSE, HandRank.FLUSH -> {
                sb.append("Monster Hand! Play aggressively, raise big.")
            }
            HandRank.STRAIGHT, HandRank.THREE_OF_A_KIND, HandRank.TWO_PAIR -> {
                sb.append("Very strong. Bet to protect hand and build pot.")
            }
            HandRank.ONE_PAIR -> {
                sb.append("Moderate. Call reasonable bets, check for free cards.")
            }
            HandRank.HIGH_CARD -> {
                if (maxSuitCount == 4 || hasDraw) {
                    sb.append("Drawing. Stay in if cheap, otherwise fold.")
                } else {
                    sb.append("Weak. Safe fold to any pressure.")
                }
            }
        }
    }
    return sb.toString()
}

class GameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float,
    val decay: Float,
    val type: Int, // 0 = Spark/Firework, 1 = Confetti
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var gravity: Float = 0.05f
)

fun getWonPotSize(logs: List<String>): Int {
    val lastLog = logs.lastOrNull() ?: return 0
    if (lastLog.contains("wins the pot of ")) {
        val parts = lastLog.split("wins the pot of ")
        if (parts.size > 1) {
            val numStr = parts[1].substringBefore(" chips").trim()
            return numStr.toIntOrNull() ?: 0
        }
    }
    if (lastLog.contains("(Pot: ")) {
        val parts = lastLog.split("(Pot: ")
        if (parts.size > 1) {
            val numStr = parts[1].substringBefore(" chips").trim()
            return numStr.toIntOrNull() ?: 0
        }
    }
    return 0
}

val celebrationParticleColors = listOf(
    Color(0xFFFFD700), // Gold
    Color(0xFFFF3E3E), // Red
    Color(0xFF3E8BFF), // Blue
    Color(0xFF3EFF3E), // Green
    Color(0xFFFF3EFF), // Magenta
    Color(0xFF3EFFFF), // Cyan
    Color(0xFFFF8C00), // Orange
    Color(0xFF9400D3)  // Violet
)

@Composable
fun PokerPlayScreen(
    viewModel: GameViewModel,
    state: GameState
) {
    val context = LocalContext.current
    val myPlayerObj = remember(state.pokerPlayers, state.localPlayerId, state.isPokerMultiplayer) {
        state.pokerPlayers.find { p ->
            p.id == "player_human" || (state.isPokerMultiplayer && p.id == state.localPlayerId)
        }
    }
    var inputRaiseAmtStr by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showHelpOverlay by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var isAddingToInitial by remember { mutableStateOf(false) }
    
    // Interactive Draggable positions persistent state
    val playerOffsets = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }

    // Winner coin collection tracking
    val winningPlayer = remember(state.pokerStage, state.pokerPlayers) {
        if (state.pokerStage == "FINISHED") {
            val winLog = state.pokerLogs.lastOrNull { it.contains("wins the pot") || it.contains("Winner:") }
            if (winLog != null) {
                state.pokerPlayers.firstOrNull { winLog.contains(it.name) }
            } else {
                null
            }
        } else {
            null
        }
    }

    var animateCoinsWinnerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(winningPlayer) {
        if (winningPlayer != null) {
            animateCoinsWinnerId = winningPlayer.id
        }
    }

    val animatedCoinProgress by animateFloatAsState(
        targetValue = if (animateCoinsWinnerId != null) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        finishedListener = {
            animateCoinsWinnerId = null
        }
    )

    // Celebration effects states
    var activeCelebrationPot by remember { mutableStateOf<Int?>(null) }
    var activeCelebrationWinner by remember { mutableStateOf<String?>(null) }
    val celebrationParticles = remember { mutableStateListOf<GameParticle>() }
    var frameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(winningPlayer) {
        if (winningPlayer != null) {
            val potSize = getWonPotSize(state.pokerLogs)
            val isSignificant = potSize >= (state.pokerInitialChips * 0.15f) || potSize >= 150
            if (isSignificant) {
                activeCelebrationPot = potSize
                activeCelebrationWinner = winningPlayer.name
                celebrationParticles.clear()
                
                // Auto-dismiss after 6 seconds
                delay(6000)
                activeCelebrationPot = null
                activeCelebrationWinner = null
                celebrationParticles.clear()
            } else {
                activeCelebrationPot = null
                activeCelebrationWinner = null
                celebrationParticles.clear()
            }
        } else {
            activeCelebrationPot = null
            activeCelebrationWinner = null
            celebrationParticles.clear()
        }
    }

    LaunchedEffect(activeCelebrationPot) {
        if (activeCelebrationPot != null) {
            var lastTime = System.currentTimeMillis()
            while (activeCelebrationPot != null) {
                withFrameMillis { time ->
                    val now = System.currentTimeMillis()
                    val delta = (now - lastTime).coerceIn(1L, 100L) / 1000f
                    lastTime = now
                    
                    val iterator = celebrationParticles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += p.gravity
                        p.alpha -= p.decay
                        p.rotation += p.rotationSpeed
                        if (p.alpha <= 0f) {
                            iterator.remove()
                        }
                    }
                    frameTime = time
                }
            }
        }
    }
    
    // Auto Scroll for Logs
    val logListState = rememberLazyListState()
    LaunchedEffect(state.pokerLogs.size) {
        if (state.pokerLogs.isNotEmpty()) {
            logListState.animateScrollToItem(state.pokerLogs.size - 1)
        }
    }

    // Staggered card dealing animation count
    var dealtCardsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.pokerStage, state.pokerPlayers.firstOrNull()?.cards) {
        if (state.pokerStage == "PRE_FLOP") {
            dealtCardsCount = 0
            for (i in 1..7) {
                delay(150)
                dealtCardsCount = i
            }
        } else {
            dealtCardsCount = 7
        }
    }

    // Dynamic slot indices mapping
    val playerSlots = remember(state.pokerPlayers.size) {
        val slots = mutableMapOf<String, Int>()
        var botCount = 0
        state.pokerPlayers.forEach { p ->
            val isMe = p.id == "player_human" || (state.isPokerMultiplayer && p.id == state.localPlayerId)
            if (isMe) {
                slots[p.id] = 5 // Bottom Left
            } else {
                val botSlots = listOf(1, 0, 2, 3, 4, 6)
                slots[p.id] = botSlots.getOrNull(botCount) ?: 0
                botCount++
            }
        }
        slots
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                state = state,
                viewModel = viewModel,
                startMusicEnabled = false,
                onToggleStartMusic = {},
                onDismiss = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.exitGameSelection()
                            },
                            modifier = Modifier.testTag("poker_exit_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White)
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                showSummaryDialog = true
                            },
                            modifier = Modifier.testTag("poker_summary_log_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Game Log Summary",
                                tint = Color(0xFFFFD700)
                              )
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                showHelpOverlay = !showHelpOverlay
                            },
                            modifier = Modifier.testTag("poker_help_btn")
                        ) {
                            Icon(
                                imageVector = if (showHelpOverlay) Icons.Default.Close else Icons.Default.Help,
                                contentDescription = "Help",
                                tint = if (showHelpOverlay) Color(0xFFFFD700) else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.startPokerHand()
                            },
                            enabled = state.pokerStage == "FINISHED" || state.pokerStage == "SHOWDOWN",
                            modifier = Modifier.testTag("poker_new_hand_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "New Hand", tint = Color.White)
                        }
                    }
                }
            },
            containerColor = Color(0xFF0F0F0E)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // Central Relative Table Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        
                        // Center Table Area
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth(state.pokerTableWidthScale)
                                .height(state.pokerTableHeight.dp)
                                .padding(horizontal = 16.dp)
                                .background(Color(0xFF0E4324), RoundedCornerShape(20.dp))
                                .border(3.dp, Color(0xFF42210B), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerWidth = maxWidth
                            val containerHeight = maxHeight

                            val maxCardHeightFromHeight = (containerHeight - 55.dp).coerceAtLeast(30.dp)
                            val maxCardWidthFromWidth = ((containerWidth - 24.dp) / 5).coerceAtLeast(20.dp)

                            val computedCardHeight = minOf(maxCardHeightFromHeight, maxCardWidthFromWidth / 0.7f)
                            val computedCardWidth = computedCardHeight * 0.7f

                            val computedCardSpacing = ((containerWidth - (computedCardWidth * 5) - 16.dp) / 6).coerceIn(2.dp, 12.dp)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = "POT: ${state.pokerPot} CHIPS",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color(0xFFFFD700)
                                    )
                                    Text(
                                        text = "  •  ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "Stage: ${state.pokerStage}",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(computedCardSpacing),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(5) { idx ->
                                        val card = state.pokerCommunityCards.getOrNull(idx)
                                        val cardModifier = Modifier.size(computedCardWidth, computedCardHeight)
                                        if (card != null) {
                                            if (dealtCardsCount >= (idx + 3)) {
                                                PlayingCardView(card = card, modifier = cardModifier)
                                            } else {
                                                PlayingCardBackView(modifier = cardModifier)
                                            }
                                        } else {
                                            PlayingCardBackView(modifier = cardModifier)
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Coins collection effect overlay
                        if (animateCoinsWinnerId != null) {
                            val slot = playerSlots[animateCoinsWinnerId] ?: 0
                            val targetDpX = when (slot) {
                                0 -> -110f // TopStart
                                1 -> 0f    // TopCenter
                                2 -> 110f  // TopEnd
                                3 -> -110f // CenterStart
                                4 -> 110f  // CenterEnd
                                5 -> -110f // BottomStart
                                6 -> 110f  // BottomEnd
                                else -> 0f
                            }
                            val targetDpY = when (slot) {
                                0 -> -85f // TopStart
                                1 -> -85f // TopCenter
                                2 -> -85f // TopEnd
                                3 -> 0f   // CenterStart
                                4 -> 0f   // CenterEnd
                                5 -> 85f  // BottomStart
                                6 -> 85f  // BottomEnd
                                else -> 0f
                            }
                            repeat(12) { i ->
                                val stagger = i * 0.05f
                                val progress = ((animatedCoinProgress - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                                if (progress > 0f) {
                                    val dragOffset = playerOffsets[animateCoinsWinnerId] ?: androidx.compose.ui.geometry.Offset.Zero
                                    val curX = (targetDpX * progress).dp + (dragOffset.x * progress / 2f).dp
                                    val curY = (targetDpY * progress).dp + (dragOffset.y * progress / 2f).dp
                                    Text(
                                        text = "🪙",
                                        fontSize = 20.sp,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset(x = curX, y = curY)
                                            .graphicsLayer {
                                                alpha = 1f - progress
                                                scaleX = 1f + progress * 0.3f
                                                scaleY = 1f + progress * 0.3f
                                                rotationZ = progress * 720f // fast spinning coin!
                                            }
                                    )
                                }
                            }
                        }

                        // Infinite pulsing transition for highlights
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val borderGlowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "glow"
                        )

                        // Render Surrounding Players
                        val alignments = listOf(
                            Alignment.TopStart,
                            Alignment.TopCenter,
                            Alignment.TopEnd,
                            Alignment.CenterStart,
                            Alignment.CenterEnd,
                            Alignment.BottomStart,
                            Alignment.BottomEnd
                        )

                        state.pokerPlayers.forEach { p ->
                            val slot = playerSlots[p.id] ?: 0
                            val alignment = alignments.getOrNull(slot) ?: Alignment.TopStart
                            val dragOffset = playerOffsets[p.id] ?: androidx.compose.ui.geometry.Offset.Zero
                            
                            val isMe = p.id == "player_human" || (state.isPokerMultiplayer && p.id == state.localPlayerId)
                            val isActive = p.id == (state.pokerPlayers.getOrNull(state.pokerActivePlayerIndex)?.id ?: "")
                            val isWinner = winningPlayer != null && winningPlayer.id == p.id

                            Box(
                                modifier = Modifier
                                    .align(alignment)
                                    .offset { androidx.compose.ui.unit.IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
                                    .pointerInput(p.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            playerOffsets[p.id] = (playerOffsets[p.id] ?: androidx.compose.ui.geometry.Offset.Zero) + dragAmount
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .border(
                                            width = if (isWinner) 4.dp else if (isActive) 3.dp else 1.dp,
                                            color = if (isWinner) Color(0xFFFFD700).copy(alpha = borderGlowAlpha) else if (isActive) Color(0xFFFFD700).copy(alpha = borderGlowAlpha) else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isWinner) Color(0xFF4A3B07) else if (isMe) Color(0xFF2E2620) else if (p.hasFolded) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.8f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Prominent D, SB, and BB Badges replacing Avatar side-by-side
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(18.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (p.isDealer) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFFFD700), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("D", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                }
                                            }
                                            if (p.isSmallBlind) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF27AE60), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("SB", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                            if (p.isBigBlind) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFC0392B), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("BB", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                            if (!p.isDealer && !p.isSmallBlind && !p.isBigBlind) {
                                                Spacer(modifier = Modifier.height(1.dp))
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = p.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) Color(0xFFFFD700) else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
 
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        Text(
                                            text = "${p.chips} chips",
                                            fontSize = 9.sp,
                                            color = Color.LightGray
                                        )
 
                                        Spacer(modifier = Modifier.height(4.dp))
 
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val showHoleCards = isMe || state.pokerStage == "FINISHED" || state.pokerStage == "SHOWDOWN"
                                            if (dealtCardsCount >= 1 && p.cards.size >= 2) {
                                                MiniCardView(
                                                    card = if (showHoleCards) p.cards[0] else null,
                                                    isFolded = p.hasFolded
                                                )
                                                MiniCardView(
                                                    card = if (showHoleCards) p.cards[1] else null,
                                                    isFolded = p.hasFolded
                                                )
                                            } else {
                                                Box(modifier = Modifier.size(24.dp, 36.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp)))
                                                Box(modifier = Modifier.size(24.dp, 36.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp)))
                                            }
                                        }
 
                                        if (p.hasFolded) {
                                            Text("FOLDED", fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                        } else if (p.isAllIn) {
                                            Text("ALL-IN", fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                        } else if (p.currentBet > 0) {
                                            Text("Bet: ${p.currentBet}", fontSize = 8.sp, color = Color.LightGray, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }

                                // Last Action speech bubble/cloud with white background and tiny black text
                                val showBubble = p.id == state.pokerLastActorId && !p.lastAction.isNullOrBlank()
                                if (showBubble) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-32).dp)
                                            .background(Color.White, RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = p.lastAction ?: "",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .padding(8.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .padding(end = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("DETAILED ASSISTANT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                                    
                                    if (myPlayerObj != null && !myPlayerObj.hasFolded) {
                                        val bestHand = viewModel.evaluate7CardHand(myPlayerObj.cards + state.pokerCommunityCards)
                                        val detailedHelp = remember(myPlayerObj.cards, state.pokerCommunityCards, state.pokerStage) {
                                            getPokerDetailedHelp(myPlayerObj.cards, state.pokerCommunityCards, bestHand)
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(top = 4.dp)
                                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(4.dp)
                                        ) {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                item {
                                                    Text(
                                                        text = detailedHelp,
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF00FFFF),
                                                        textAlign = TextAlign.Start
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(top = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Folded / No Hand", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                                        }
                                    }
                                }
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isAddingToInitial) "Add Initial Money" else "Add Current Chips",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD700)
                                        )
                                        IconButton(
                                            onClick = {
                                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                                isAddingToInitial = !isAddingToInitial
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = "Toggle add target",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                                if (isAddingToInitial) {
                                                    viewModel.pokerAddInitialChips(500)
                                                } else {
                                                    viewModel.pokerAddChips(500)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                            modifier = Modifier.height(24.dp).weight(1f),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+500", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = {
                                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                                if (isAddingToInitial) {
                                                    viewModel.pokerAddInitialChips(1000)
                                                } else {
                                                    viewModel.pokerAddChips(1000)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                            modifier = Modifier.height(24.dp).weight(1f),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+1000", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))

                            Column(
                                modifier = Modifier
                                    .weight(1.7f)
                                    .fillMaxHeight()
                                    .padding(start = 6.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .background(Color(0xFF0C0C0B), RoundedCornerShape(6.dp))
                                        .padding(6.dp)
                                ) {
                                    items(state.pokerLogs) { log ->
                                        Text(
                                            text = log,
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val activePlayerObj = state.pokerPlayers.getOrNull(state.pokerActivePlayerIndex)
                                    val isMyTurn = activePlayerObj != null && (
                                        activePlayerObj.id == "player_human" || 
                                        (state.isPokerMultiplayer && activePlayerObj.id == state.localPlayerId)
                                    )

                                    if (isMyTurn && state.pokerStage != "FINISHED" && state.pokerStage != "SHOWDOWN") {
                                        val callAmount = state.pokerCurrentBet - (activePlayerObj?.currentBet ?: 0)
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    SoundPlayer.playClick(context, state.vibrationEnabled)
                                                    viewModel.pokerFold()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                                                modifier = Modifier.weight(1f).height(30.dp).testTag("poker_fold_btn"),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("FOLD", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
                                            }

                                            Button(
                                                onClick = {
                                                    SoundPlayer.playClick(context, state.vibrationEnabled)
                                                    if (callAmount == 0) {
                                                        viewModel.pokerCheck()
                                                    } else {
                                                        viewModel.pokerCall()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                                modifier = Modifier.weight(1.3f).height(30.dp).testTag("poker_call_btn"),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = if (callAmount == 0) "CHECK" else "CALL $callAmount",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            BasicTextField(
                                                value = inputRaiseAmtStr,
                                                onValueChange = { inputRaiseAmtStr = it },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(28.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        if (inputRaiseAmtStr.isEmpty()) {
                                                            Text("Amt", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )

                                            Button(
                                                onClick = {
                                                    SoundPlayer.playClick(context, state.vibrationEnabled)
                                                    val raiseVal = inputRaiseAmtStr.toIntOrNull()?.coerceAtLeast(10) ?: 20
                                                    viewModel.pokerRaise(raiseVal)
                                                    inputRaiseAmtStr = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                                modifier = Modifier.weight(1.1f).height(28.dp).testTag("poker_raise_btn"),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("RAISE", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Color.Black)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (state.pokerStage == "FINISHED" || state.pokerStage == "SHOWDOWN") {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "Hand finished!",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFFFD700),
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "Click ↻ in top menu to redeal.",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 9.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "Waiting for ${activePlayerObj?.name ?: "others"}...",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = showHelpOverlay,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    PokerRulesHelpView(
                        onDismiss = { showHelpOverlay = false }
                    )
                }

                if (showSummaryDialog) {
                    val totalGained = state.pokerTotalChipsAdded
                    val currentStack = myPlayerObj?.chips ?: 0
                    val startingMoney = state.pokerInitialChips
                    val balance = currentStack - startingMoney
                    
                    AlertDialog(
                        onDismissRequest = { showSummaryDialog = false },
                        title = {
                            Text(
                                text = "Game Log Summary",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD700)
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1C)),
                                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Initial starting money:", fontSize = 12.sp, color = Color.Gray)
                                            Text("$startingMoney chips", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Current Stack Balance:", fontSize = 12.sp, color = Color.Gray)
                                            Text("$currentStack chips", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Extra top-up added:", fontSize = 12.sp, color = Color.Gray)
                                            Text("$totalGained chips", fontSize = 12.sp, color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Net Performance:", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (balance >= 0) "+$balance chips" else "$balance chips",
                                                fontSize = 13.sp,
                                                color = if (balance >= 0) Color(0xFF27AE60) else Color(0xFFC0392B),
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                Text("Detailed Activity History:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFFD700))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(state.pokerLogs) { log ->
                                            Text(
                                                text = "• $log",
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp,
                                                color = Color.LightGray,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showSummaryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                            ) {
                                Text("CLOSE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF0F0F0E),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // Celebration particle Canvas and overlay Banner
                if (activeCelebrationPot != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dummy = frameTime // Trigger redraw
                        
                        val width = size.width
                        val height = size.height
                        
                        // Spawn confetti at top of the screen
                        if (celebrationParticles.size < 150 && Math.random() < 0.25) {
                            repeat(5) {
                                celebrationParticles.add(
                                    GameParticle(
                                        x = (Math.random() * width).toFloat(),
                                        y = -20f,
                                        vx = ((Math.random() - 0.5) * 5f).toFloat(),
                                        vy = (Math.random() * 4f + 2f).toFloat(),
                                        color = celebrationParticleColors.random(),
                                        size = (Math.random() * 10f + 6f).toFloat(),
                                        alpha = 1f,
                                        decay = (Math.random() * 0.006f + 0.004f).toFloat(),
                                        type = 1, // Confetti
                                        rotation = (Math.random() * 360f).toFloat(),
                                        rotationSpeed = ((Math.random() - 0.5f) * 12f).toFloat(),
                                        gravity = (Math.random() * 0.12f + 0.06f).toFloat()
                                    )
                                )
                            }
                        }
                        
                        // Occasional fireworks bursts
                        if (Math.random() < 0.02) {
                            val explodeX = (Math.random() * 0.8f + 0.1f).toFloat() * width
                            val explodeY = (Math.random() * 0.5f + 0.15f).toFloat() * height
                            val fireworkColor = celebrationParticleColors.random()
                            repeat(35) {
                                val angle = Math.random() * 2.0 * Math.PI
                                val speed = Math.random() * 7f + 3f
                                val vx = (Math.cos(angle) * speed).toFloat()
                                val vy = (Math.sin(angle) * speed).toFloat()
                                celebrationParticles.add(
                                    GameParticle(
                                        x = explodeX,
                                        y = explodeY,
                                        vx = vx,
                                        vy = vy,
                                        color = fireworkColor,
                                        size = (Math.random() * 5f + 3f).toFloat(),
                                        alpha = 1f,
                                        decay = (Math.random() * 0.016f + 0.008f).toFloat(),
                                        type = 0, // Spark/Firework
                                        rotation = 0f,
                                        rotationSpeed = 0f,
                                        gravity = 0.07f
                                    )
                                )
                            }
                        }
                        
                        // Render particles
                        celebrationParticles.forEach { p ->
                            if (p.type == 0) {
                                drawCircle(
                                    color = p.color.copy(alpha = p.alpha),
                                    radius = p.size,
                                    center = androidx.compose.ui.geometry.Offset(p.x, p.y)
                                )
                            } else {
                                val halfW = p.size
                                val halfH = p.size * 0.4f
                                rotate(degrees = p.rotation, pivot = androidx.compose.ui.geometry.Offset(p.x, p.y)) {
                                    drawRect(
                                        color = p.color.copy(alpha = p.alpha),
                                        topLeft = androidx.compose.ui.geometry.Offset(p.x - halfW, p.y - halfH),
                                        size = androidx.compose.ui.geometry.Size(halfW * 2f, halfH * 2f)
                                    )
                                }
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                activeCelebrationPot = null
                                activeCelebrationWinner = null
                                celebrationParticles.clear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                            border = BorderStroke(2.dp, Color(0xFFFFD700)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .padding(24.dp)
                                .widthIn(max = 320.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🏆 POT VICTORY 🏆",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFD700),
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = "${activeCelebrationWinner ?: "Winner"} wins the pot!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = "+${activeCelebrationPot} CHIPS",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF27AE60),
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "Tap to dismiss celebration",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OldPokerPlayScreen(
    viewModel: GameViewModel,
    state: GameState
) {}

/*
    val context = LocalContext.current
    var inputRaiseAmtStr by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Auto Scroll for Logs
    val logListState = rememberLazyListState()
    LaunchedEffect(state.pokerLogs.size) {
        if (state.pokerLogs.isNotEmpty()) {
            logListState.animateScrollToItem(state.pokerLogs.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                state = state,
                viewModel = viewModel,
                startMusicEnabled = false,
                onToggleStartMusic = {},
                onDismiss = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.exitGameSelection()
                            },
                            modifier = Modifier.testTag("poker_exit_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Poker Table • Pot: ${state.pokerPot}", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 16.sp)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                SoundPlayer.playClick(context, state.vibrationEnabled)
                                viewModel.startPokerHand()
                            },
                            enabled = state.pokerStage == "FINISHED" || state.pokerStage == "SHOWDOWN",
                            modifier = Modifier.testTag("poker_new_hand_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "New Hand", tint = Color.White)
                        }
                    }
                }
            },
            containerColor = Color(0xFF0F0F0E)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Opponents list to prevent overlapping
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.pokerPlayers) { p ->
                        val isMe = p.id == "player_human" || (state.isPokerMultiplayer && p.id == state.localPlayerId)
                        val isActive = p.id == (state.pokerPlayers.getOrNull(state.pokerActivePlayerIndex)?.id ?: "")
                        val isWinner = winningPlayer != null && winningPlayer.id == p.id
                        
                        Card(
                            modifier = Modifier
                                .width(94.dp)
                                .border(
                                    width = if (isWinner) 4.dp else if (isActive) 2.dp else 1.dp,
                                    color = if (isWinner) Color(0xFFFFD700).copy(alpha = borderGlowAlpha) else if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isWinner) Color(0xFF4A3B07) else if (isMe) Color(0xFF2E2620) else if (p.hasFolded) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Prominent role badges replacing Avatar side-by-side in landscape
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (p.isDealer) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFFD700), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("D", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                    }
                                    if (p.isSmallBlind) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF27AE60), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("SB", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    if (p.isBigBlind) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFC0392B), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("BB", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    if (!p.isDealer && !p.isSmallBlind && !p.isBigBlind) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                }
                                
                                Text(
                                    text = if (isMe) "YOU (${p.name})" else p.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Text(
                                    text = "${p.chips} 🪙",
                                    fontSize = 9.sp,
                                    color = Color(0xFFFFD700)
                                )
                                
                                Spacer(modifier = Modifier.height(1.dp))

                                if (p.hasFolded) {
                                    Text("FOLDED", fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else if (p.isAllIn) {
                                    Text("ALL-IN", fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else if (p.currentBet > 0) {
                                    Text("Bet: ${p.currentBet}", fontSize = 8.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }

                val pokerPlayersList = state.pokerPlayers.map { it.name to it.chips }
                if (pokerPlayersList.isNotEmpty()) {
                    RealTimeRankingBoard(
                        title = "🏆 POKER CHIP RANKINGS",
                        players = pokerPlayersList,
                        isDark = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Central Felt Table Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFF0E4324), RoundedCornerShape(24.dp))
                        .border(4.dp, Color(0xFF42210B), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "POT: ${state.pokerPot} CHIPS",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "  •  ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Stage: ${state.pokerStage}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(5) { idx ->
                                val card = state.pokerCommunityCards.getOrNull(idx)
                                if (card != null) {
                                    PlayingCardView(card = card)
                                } else {
                                    PlayingCardBackView()
                                }
                            }
                        }
                    }
                }

                // Interactive Bottom Actions Panel
                val myPlayerObj = state.pokerPlayers.find { p ->
                    p.id == "player_human" || (state.isPokerMultiplayer && p.id == state.localPlayerId)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // Left Panel: Your cards and hand evaluation
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .padding(end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("YOUR HAND", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
                            
                            if (myPlayerObj != null && !myPlayerObj.hasFolded) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    myPlayerObj.cards.forEach { card ->
                                        PlayingCardView(card = card)
                                    }
                                }
                                
                                val bestHand = viewModel.evaluate7CardHand(myPlayerObj.cards + state.pokerCommunityCards)
                                Text(
                                    text = bestHand.description,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text("Folded / No Hand", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }

                        // Middle Divider
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))

                        // Right Panel: Active Action Buttons & Logs
                        Column(
                            modifier = Modifier
                                .weight(1.8f)
                                .padding(start = 8.dp)
                        ) {
                            // Scrolling Action Logs Panel
                            LazyColumn(
                                state = logListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .background(Color(0xFF161616), RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                items(state.pokerLogs) { log ->
                                    Text(
                                        text = log,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Controls
                            val activePlayerObj = state.pokerPlayers.getOrNull(state.pokerActivePlayerIndex)
                            val isMyTurn = activePlayerObj != null && (
                                activePlayerObj.id == "player_human" || 
                                (state.isPokerMultiplayer && activePlayerObj.id == state.localPlayerId)
                            )

                            if (isMyTurn && state.pokerStage != "FINISHED" && state.pokerStage != "SHOWDOWN") {
                                val callAmount = state.pokerCurrentBet - (activePlayerObj?.currentBet ?: 0)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // FOLD
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            viewModel.pokerFold()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("poker_fold_btn"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("FOLD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                                    }

                                    // CHECK / CALL
                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            if (callAmount == 0) {
                                                viewModel.pokerCheck()
                                            } else {
                                                viewModel.pokerCall()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                        modifier = Modifier.weight(1.3f).height(36.dp).testTag("poker_call_btn"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = if (callAmount == 0) "CHECK" else "CALL $callAmount",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // RAISE panel
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = inputRaiseAmtStr,
                                        onValueChange = { inputRaiseAmtStr = it },
                                        placeholder = { Text("Amt", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFFFD700)
                                        ),
                                        modifier = Modifier.weight(1f).height(50.dp)
                                    )

                                    Button(
                                        onClick = {
                                            SoundPlayer.playClick(context, state.vibrationEnabled)
                                            val raiseVal = inputRaiseAmtStr.toIntOrNull()?.coerceAtLeast(10) ?: 20
                                            viewModel.pokerRaise(raiseVal)
                                            inputRaiseAmtStr = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                        modifier = Modifier.weight(1.2f).height(36.dp).testTag("poker_raise_btn"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("RAISE", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
                                    }
                                }
                            } else {
                                // Turn status indicators
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.pokerStage == "FINISHED" || state.pokerStage == "SHOWDOWN") {
                                        Text(
                                            text = "Hand Finished! Click ↻ to start new deal.",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD700),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            text = "Waiting for ${activePlayerObj?.name ?: "others"}...",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
*/

@Composable
fun GameLinkShareCard(ip: String, port: Int) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val link = "http://$ip:8080"
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BROWSER GAME LINK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                text = link,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Share this link with friends to play in their browser without installing the app!",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link))
                    Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("COPY GAME LINK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
