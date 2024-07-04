
## Summary

Android media player library base on FFmpeg 7.0.1. Support single image frame load, subtitle render, video hw decode and ascii art image filter.


## Screenshots

![](./screenshots/screenshot.gif)

Demo Apks:  
[arm64-v8a](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-arm64-v8a-release.apk)  
[armeabi-v7a](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-armeabi-v7a-release.apk)  
[x86_64](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-x86_64-release.apk)  
[x86](https://github.com/Tans5/tMediaPlayer/raw/master/apk/demo-x86-release.apk)

## Usage


### Add dependency

```groovy

dependencies {
	 // ...
    implementation 'io.github.tans5:tmediaplayer:1.2.0'
    // ...
}

```

### Play a local media file


```Kotlin

val mediaPlayer = tMediaPlayer()

// set media file and use hardware decode.
mediaPlayer.prepare("xxxxx.mp4", true)

// if prepare success, play it.
mediaPlayer.play()

// if you need display images, add a tMediaPlayerView to render it.
mediaPlayer.attachPlayerView(playerView)

```

When you don't need player, remenber to release it.  

``` Kotlin
mediaPlayer.release()
```

Basic media player methods.  

```Kotlin
 mediaPlayer.setListener(object : tMediaPlayerListener {
     /**
      * Player state update
      */
     override fun onPlayerState(state: tMediaPlayerState) {
     }
     /**
      * Play progress update
      */
     override fun onProgressUpdate(progress: Long, duration: Long) {
     }
 })
 mediaPlayer.play()
 
 mediaPlayer.pause()
 
 mediaPlayer.seekTo(0)
 
 mediaPlayer.stop()

```

### Ascii art image filter

Open ascii art image filter

```Kotlin
playerView.enableAsciiArtFilter(true)
```

Ascii art image filter settings

```Kotlin

val filter = playerView.getAsciiArtImageFilter()
filter.setCharLineWidth(128)
filter.reverseChar(true)
filter.reverseColor(true)
filter.colorFillRate(1.0f)
```

