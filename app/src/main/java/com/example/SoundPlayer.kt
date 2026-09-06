package com.example

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SoundPlayer {
    private var appContext: Context? = null
    var soundEnabled: Boolean = true
    var vibrationEnabled: Boolean = true
    var startMusicEnabled: Boolean = true // Flag for the background start music

    private const val TAG = "SoundPlayer"

    // Audio URLs (streamable download links)
    private const val URL_CLICK = "https://docs.google.com/uc?export=download&id=1s1PsHp8tkxIGIeyTLWlpEyVTaMzcep3z"
    private const val URL_LOSE = "https://docs.google.com/uc?export=download&id=1gnkOIzBBAGiamDei4YDqYTLUtl2xKMfj"
    private const val URL_WIN = "https://docs.google.com/uc?export=download&id=1qETfvRN5Zew7xGhqnJULP31fSZhRlZcf"
    private const val URL_START_MUSIC = "https://docs.google.com/uc?export=download&id=1cnwkgqSNVJGaP51EonPs5nbg1neKb24C"
    private const val URL_DEAL = "https://docs.google.com/uc?export=download&id=1kNoYvAmWVxeL5_KGSFjDKS4YoW6lC4E7"
    private const val URL_CONTRIBUTE = "https://drive.google.com/uc?export=download&id=1KCcrLEUOB0OthunzXNa90MmxCrkv_TFD"

    // Local cached file names to completely avoid network lagging
    private const val FILE_CLICK = "sound_click_v3.mp3"
    private const val FILE_LOSE = "sound_lose_v3.mp3"
    private const val FILE_WIN = "sound_win_v3.mp3"
    private const val FILE_START_MUSIC = "sound_start_music_v3.mp3"
    private const val FILE_DEAL = "sound_deal_v3.mp3"
    private const val FILE_CONTRIBUTE = "sound_contribute_v3.mp3"

    // Precise trimming offsets (in milliseconds) to skip initial silence
    private const val TRIM_CLICK = 50
    private const val TRIM_LOSE = 150
    private const val TRIM_WIN = 150
    private const val TRIM_START_MUSIC = 400

    private var startMusicPlayer: MediaPlayer? = null
    private var winLosePlayer: MediaPlayer? = null
    private var winLoseJob: kotlinx.coroutines.Job? = null

    private const val CLICK_POOL_SIZE = 4
    private val clickPlayers = ArrayList<MediaPlayer>()
    private var clickPoolIndex = 0

    private const val DEAL_POOL_SIZE = 3
    private val dealPlayers = ArrayList<MediaPlayer>()
    private var dealPoolIndex = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        // Pre-download all audio files on a background thread so they are cached locally
        CoroutineScope(Dispatchers.IO).launch {
            fillClickPool()
            fillDealPool()
            preDownloadAll()
        }
    }

    fun stopAll() {
        stopStartMusic()
        stopWinLose()
    }

    private suspend fun preDownloadAll() {
        val targetContext = appContext ?: return
        val items = listOf(
            Pair(FILE_CLICK, URL_CLICK),
            Pair(FILE_LOSE, URL_LOSE),
            Pair(FILE_WIN, URL_WIN),
            Pair(FILE_START_MUSIC, URL_START_MUSIC),
            Pair(FILE_DEAL, URL_DEAL),
            Pair(FILE_CONTRIBUTE, URL_CONTRIBUTE)
        )
        for ((fileName, url) in items) {
            val file = File(targetContext.filesDir, fileName)
            if (!file.exists() || file.length() == 0L) {
                downloadFileWithRedirects(url, file)
            }
        }
        fillClickPool()
        fillDealPool()
    }

    private fun triggerDownload(fileName: String, url: String) {
        val targetContext = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(targetContext.filesDir, fileName)
            if (!file.exists() || file.length() == 0L) {
                downloadFileWithRedirects(url, file)
                if (fileName == FILE_CLICK) {
                    fillClickPool()
                } else if (fileName == FILE_DEAL) {
                    fillDealPool()
                }
            }
        }
    }

    private suspend fun downloadFileWithRedirects(urlString: String, outputFile: File) {
        withContext(Dispatchers.IO) {
            try {
                var currentUrl = urlString
                var redirectCount = 0
                val maxRedirects = 5
                var conn: HttpURLConnection? = null
                
                while (redirectCount < maxRedirects) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.instanceFollowRedirects = true
                    
                    val status = conn.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                        status == HttpURLConnection.HTTP_MOVED_PERM || 
                        status == 307 || status == 308) {
                        val newUrl = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (newUrl != null) {
                            currentUrl = newUrl
                            redirectCount++
                            continue
                        }
                    }
                    break
                }
                
                conn?.let { connection ->
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val tempFile = File(outputFile.absolutePath + ".tmp")
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (outputFile.exists()) outputFile.delete()
                            tempFile.renameTo(outputFile)
                            Log.d(TAG, "Successfully downloaded audio cache: ${outputFile.name}")
                        }
                    } else {
                        Log.e(TAG, "Failed to download audio: ${outputFile.name}, Response: ${connection.responseCode}")
                    }
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading audio cache: ${outputFile.name}", e)
            }
        }
    }

    private fun applyAudioAttributes(mp: MediaPlayer) {
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_GAME)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mp.setAudioAttributes(audioAttributes)
    }

    private fun fillClickPool() {
        val targetContext = appContext ?: return
        val clickFile = File(targetContext.filesDir, FILE_CLICK)
        if (!clickFile.exists() || clickFile.length() == 0L) return
        
        synchronized(clickPlayers) {
            for (mp in clickPlayers) {
                try { mp.release() } catch (e: Exception) {}
            }
            clickPlayers.clear()
            
            for (i in 0 until CLICK_POOL_SIZE) {
                try {
                    val mp = MediaPlayer()
                    applyAudioAttributes(mp)
                    mp.setDataSource(clickFile.absolutePath)
                    mp.setOnPreparedListener { player ->
                        if (TRIM_CLICK > 0 && player.duration > TRIM_CLICK) {
                            player.seekTo(TRIM_CLICK)
                        }
                    }
                    mp.prepare()
                    clickPlayers.add(mp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing click player in pool", e)
                }
            }
        }
    }

    private fun fillDealPool() {
        val targetContext = appContext ?: return
        val dealFile = File(targetContext.filesDir, FILE_DEAL)
        if (!dealFile.exists() || dealFile.length() == 0L) return
        
        synchronized(dealPlayers) {
            for (mp in dealPlayers) {
                try { mp.release() } catch (e: Exception) {}
            }
            dealPlayers.clear()
            
            for (i in 0 until DEAL_POOL_SIZE) {
                try {
                    val mp = MediaPlayer()
                    applyAudioAttributes(mp)
                    mp.setDataSource(dealFile.absolutePath)
                    mp.prepare()
                    dealPlayers.add(mp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing deal player in pool", e)
                }
            }
        }
    }

    private fun playLocalOrUrl(fileName: String, remoteUrl: String, trimMs: Int = 0) {
        if (!soundEnabled) return
        val targetContext = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localFile = File(targetContext.filesDir, fileName)
                val mp = MediaPlayer()
                applyAudioAttributes(mp)
                
                if (localFile.exists() && localFile.length() > 0) {
                    mp.setDataSource(localFile.absolutePath)
                } else {
                    mp.setDataSource(remoteUrl)
                    triggerDownload(fileName, remoteUrl)
                }
                
                mp.setOnPreparedListener { player ->
                    try {
                        if (trimMs > 0 && player.duration > trimMs) {
                            player.setOnSeekCompleteListener {
                                it.start()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                player.seekTo(trimMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                            } else {
                                player.seekTo(trimMs)
                            }
                        } else {
                            player.start()
                        }
                    } catch (seekEx: Exception) {
                        Log.e(TAG, "Error seeking to trimmed offset", seekEx)
                        player.start()
                    }
                }
                mp.setOnCompletionListener { player ->
                    player.release()
                }
                mp.setOnErrorListener { player, _, _ ->
                    player.release()
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound: $fileName", e)
            }
        }
    }

    fun playClick(vibrationEnabled: Boolean) {
        playClick(appContext, vibrationEnabled)
    }

    fun playClick(context: Context? = null, vibrationEnabled: Boolean = false) {
        val actualVibration = vibrationEnabled && this.vibrationEnabled
        val targetContext = context ?: appContext
        if (actualVibration && targetContext != null) {
            val vibrator = targetContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
        
        // Stop any currently playing win/lose sounds on click
        stopWinLose()
        
        if (!soundEnabled) return

        // Prioritize win/lose music: if win/lose is playing, do NOT play click sound!
        val isWinLosePlaying = try {
            winLosePlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
        if (isWinLosePlaying) {
            return
        }

        // Play from zero-latency click pool
        synchronized(clickPlayers) {
            if (clickPlayers.isNotEmpty()) {
                val mp = clickPlayers[clickPoolIndex]
                try {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(TRIM_CLICK.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(TRIM_CLICK)
                    }
                    mp.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing click from pool, trying fallback", e)
                    playLocalOrUrl(FILE_CLICK, URL_CLICK, TRIM_CLICK)
                }
                clickPoolIndex = (clickPoolIndex + 1) % clickPlayers.size
            } else {
                playLocalOrUrl(FILE_CLICK, URL_CLICK, TRIM_CLICK)
            }
        }
    }

    fun playWin(soundEnabled: Boolean = true) {
        if (!soundEnabled || !this.soundEnabled) return
        playWinLose(FILE_WIN, URL_WIN, TRIM_WIN)
    }

    fun playLose(soundEnabled: Boolean = true) {
        if (!soundEnabled || !this.soundEnabled) return
        playWinLose(FILE_LOSE, URL_LOSE, TRIM_LOSE)
    }

    private fun playWinLose(fileName: String, remoteUrl: String, trimMs: Int) {
        val targetContext = appContext ?: return
        stopWinLose()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localFile = File(targetContext.filesDir, fileName)
                val mp = MediaPlayer().apply {
                    applyAudioAttributes(this)
                    if (localFile.exists() && localFile.length() > 0) {
                        setDataSource(localFile.absolutePath)
                    } else {
                        setDataSource(remoteUrl)
                        triggerDownload(fileName, remoteUrl)
                    }
                    isLooping = false // Loop manually to play for exactly 4 seconds per loop
                    setOnPreparedListener { player ->
                        if (soundEnabled) {
                            try {
                                player.start()
                                if (trimMs > 0 && player.duration > trimMs) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        player.seekTo(trimMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                                    } else {
                                        player.seekTo(trimMs)
                                    }
                                }
                                
                                winLoseJob = CoroutineScope(Dispatchers.Main).launch {
                                    while (true) {
                                        kotlinx.coroutines.delay(4000)
                                        try {
                                            if (player.isPlaying) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    player.seekTo(trimMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                                                } else {
                                                    player.seekTo(trimMs)
                                                }
                                            } else {
                                                break
                                            }
                                        } catch (e: Exception) {
                                            break
                                        }
                                    }
                                }
                            } catch (seekEx: Exception) {
                                Log.e(TAG, "Error seeking win/lose", seekEx)
                                player.start()
                            }
                        } else {
                            player.release()
                        }
                    }
                    setOnErrorListener { player, _, _ ->
                        player.release()
                        if (winLosePlayer == player) {
                            winLosePlayer = null
                        }
                        true
                    }
                }
                winLosePlayer = mp
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing win/lose sound", e)
            }
        }
    }

    fun stopWinLose() {
        winLoseJob?.cancel()
        winLoseJob = null
        try {
            winLosePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            winLosePlayer = null
        }
    }

    fun playDealSound() {
        if (!soundEnabled) return
        
        synchronized(dealPlayers) {
            if (dealPlayers.isNotEmpty()) {
                val mp = dealPlayers[dealPoolIndex]
                try {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                    mp.seekTo(0)
                    mp.start()
                    
                    // Stop after 1 second (1000ms)
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(1000)
                        try {
                            if (mp.isPlaying) {
                                mp.pause()
                            }
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing deal from pool, fallback", e)
                    playLocalOrUrl(FILE_DEAL, URL_DEAL, 0)
                }
                dealPoolIndex = (dealPoolIndex + 1) % dealPlayers.size
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val targetContext = appContext ?: return@launch
                        val file = File(targetContext.filesDir, FILE_DEAL)
                        val mp = MediaPlayer().apply {
                            applyAudioAttributes(this)
                            if (file.exists() && file.length() > 0) {
                                setDataSource(file.absolutePath)
                            } else {
                                setDataSource(URL_DEAL)
                                triggerDownload(FILE_DEAL, URL_DEAL)
                            }
                            setOnPreparedListener { player ->
                                if (soundEnabled) {
                                    player.start()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(1000)
                                        try {
                                            if (player.isPlaying) {
                                                player.stop()
                                            }
                                            player.release()
                                        } catch (e: Exception) {}
                                    }
                                } else {
                                    player.release()
                                }
                            }
                            setOnErrorListener { player, _, _ ->
                                player.release()
                                true
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fallback deal", e)
                    }
                }
            }
        }
    }

    // --- Start / Background Music ---

    fun startStartMusic() {
        if (!soundEnabled || !startMusicEnabled) {
            stopStartMusic()
            return
        }
        if (startMusicPlayer != null) return // Already playing or preparing

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetContext = appContext ?: return@launch
                val file = File(targetContext.filesDir, FILE_START_MUSIC)
                val mp = MediaPlayer().apply {
                    applyAudioAttributes(this)
                    if (file.exists() && file.length() > 0) {
                        setDataSource(file.absolutePath)
                    } else {
                        setDataSource(URL_START_MUSIC)
                        triggerDownload(FILE_START_MUSIC, URL_START_MUSIC)
                    }
                    isLooping = true
                    setOnPreparedListener { player ->
                        if (soundEnabled && startMusicEnabled) {
                            try {
                                if (player.duration > TRIM_START_MUSIC) {
                                    player.setOnSeekCompleteListener {
                                        it.start()
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        player.seekTo(TRIM_START_MUSIC.toLong(), MediaPlayer.SEEK_CLOSEST)
                                    } else {
                                        player.seekTo(TRIM_START_MUSIC)
                                    }
                                } else {
                                    player.start()
                                }
                            } catch (seekEx: Exception) {
                                Log.e(TAG, "Error seeking start music", seekEx)
                                player.start()
                            }
                        } else {
                            player.release()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        this.release()
                        if (startMusicPlayer == this) {
                            startMusicPlayer = null
                        }
                        true
                    }
                    prepareAsync()
                }
                startMusicPlayer = mp
            } catch (e: Exception) {
                Log.e(TAG, "Error playing start music", e)
            }
        }
    }

    fun stopStartMusic() {
        try {
            startMusicPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            startMusicPlayer = null
        }
    }

    fun toggleStartMusic(enabled: Boolean) {
        startMusicEnabled = enabled
        if (enabled) {
            startStartMusic()
        } else {
            stopStartMusic()
        }
    }

    fun playContributeSound() {
        if (!soundEnabled) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetContext = appContext ?: return@launch
                val file = File(targetContext.filesDir, FILE_CONTRIBUTE)
                val mp = MediaPlayer().apply {
                    applyAudioAttributes(this)
                    if (file.exists() && file.length() > 0) {
                        setDataSource(file.absolutePath)
                    } else {
                        setDataSource(URL_CONTRIBUTE)
                        triggerDownload(FILE_CONTRIBUTE, URL_CONTRIBUTE)
                    }
                    setOnPreparedListener { player ->
                        if (soundEnabled) {
                            player.start()
                            CoroutineScope(Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(1000) // Trimmed to one second!
                                try {
                                    if (player.isPlaying) {
                                        player.stop()
                                    }
                                    player.release()
                                } catch (e: Exception) {}
                            }
                        } else {
                            player.release()
                        }
                    }
                    setOnErrorListener { player, _, _ ->
                        player.release()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing contribute sound", e)
            }
        }
    }
}
