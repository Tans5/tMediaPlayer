# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


#################################ffmepg#########################################
add_library( ffmpeg
        SHARED
        IMPORTED )
set_target_properties( ffmpeg
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/librxffmpeg-core.so )

#################################ffmepg#########################################


#################################tmediaplayer#########################################


add_library( # Sets the name of the library.
        tmediaplayer

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        t_media_jni.cpp
        mediaplayer/media_player.cpp
        mediaplayer/media_time.cpp
        mediaplayer/media_player_pool.cpp)

target_include_directories(tmediaplayer PUBLIC
        ffmpeg/header
        header)


find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

find_library(
        android-lib
        android
)

find_library(
        OpenSLES-lib
        OpenSLES
)

target_link_libraries( # Specifies the target library.
        tmediaplayer
        ffmpeg
        ${log-lib}
        ${android-lib}
        ${OpenSLES-lib}
        )

#################################tmediaplayer#########################################