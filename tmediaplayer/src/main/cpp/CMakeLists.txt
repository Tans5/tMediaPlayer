# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


#################################ffmepg#########################################
add_library( libavcodec
        SHARED
        IMPORTED )
set_target_properties( libavcodec
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavcodec.so )

#add_library( libavdevice
#        SHARED
#        IMPORTED )
#set_target_properties( libavdevice
#        PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavdevice.so )

add_library( libavfilter
        SHARED
        IMPORTED )
set_target_properties( libavfilter
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavfilter.so )

add_library( libavformat
        SHARED
        IMPORTED )
set_target_properties( libavformat
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavformat.so )

add_library( libavutil
        SHARED
        IMPORTED )
set_target_properties( libavutil
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavutil.so )

add_library( libswresample
        SHARED
        IMPORTED )
set_target_properties( libswresample
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libswresample.so )

add_library( libswscale
        SHARED
        IMPORTED )
set_target_properties( libswscale
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libswscale.so )

#################################ffmepg#########################################


#################################tmediaplayer#########################################

#file(GLOB examples_src
#        examples/*.c)

add_library( # Sets the name of the library.
        tmediaplayer

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        t_media_jni.cpp
        mediaplayer/media_player.cpp
        mediaplayer/media_time.cpp
        mediaplayer/media_player_pool.cpp

        # tmediaplayer
        tmediaplayer/tmediaplayer.cpp)

target_include_directories(tmediaplayer PUBLIC
        ffmpeg/header
        header
        tmediaplayer/header)


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
        ${log-lib}
        ${android-lib}
        ${OpenSLES-lib}

        # ffmpeg
        libavcodec
        #libavdevice
        libavfilter
        libavformat
        libavutil
        libswresample
        libswscale
        )

#################################tmediaplayer#########################################