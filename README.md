
## Summary

Android media player library base on FFmpeg 8.0. Support single image frame load, subtitle render, video hw decode and ascii art image filter.


## Screenshots

![](./screenshots/screenshot.gif)

Demo Apks:  
[arm64-v8a](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-arm64-v8a-release.apk)  
[armeabi-v7a](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-armeabi-v7a-release.apk)  
[x86_64](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-x86_64-release.apk)  
[x86](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-x86-release.apk)

## Quik Start

### 1. Add Dependencies

In your app-level `build.gradle`:

```groovy
dependencies {
    // Core library  
    implementation("io.github.tans5:tmediaplayer:1.6.2")
} 
```

### 2. Basic Usage

```Kotlin
// Initialize player
val player = tMediaPlayer()

// Prepare media source (local file or remote URL)
val result = player.prepare("https://example.com/video.mp4") // or "path/to/local/file.mp4"

if (result == Opt.Success) {
    // Attach view for rendering
    player.attachPlayerView(playerView)
    
    // Start playback
    player.play()
} else {
    // Handle loading error
    Log.e("tMediaPlayer", "Failed to load media source")
}

// Control playback
player.pause()
player.seekTo(milliseconds)
player.stop()

// Set listeners for state and progress updates
player.setListener(object : tMediaPlayerListener {
    override fun onPlayerState(state: tMediaPlayerState) {
        // Handle state changes (playing, paused, stopped, etc.)
    }

    override fun onProgressUpdate(progress: Long, duration: Long) {
        // Update UI with current progress and total duration
    }
})

// Always release player when done
player.release()
```

## ASCII Art Filter

Add ascii art filter to player: 

```Kotlin
// Get or create the ASCII art filter
val asciiArtFilter = player.getFilter() as? AsciiArtImageFilter ?: run {
    val filter = AsciiArtImageFilter()
    player.setFilter(filter)
    filter
}

// Enable/disable the filter
asciiArtFilter.enable(true)

// Customize the appearance
asciiArtFilter.apply {
    setCharLineWidth(128)        
    reverseChar(true)         
    reverseColor(true)  
    colorFillRate(0.8f)       
}
```

