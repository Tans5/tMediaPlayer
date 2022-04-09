# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


#################################ffmepg#########################################
add_library( avutil
        SHARED
        IMPORTED )
set_target_properties( avutil
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libavutil.so)

add_library( avcodec
        SHARED
        IMPORTED )
set_target_properties( avcodec
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libavcodec.so)

add_library( avdevice
        SHARED
        IMPORTED )
set_target_properties( avdevice
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libavdevice.so)


add_library( avfilter
        SHARED
        IMPORTED )
set_target_properties( avfilter
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libavfilter.so )


add_library( avformat
        SHARED
        IMPORTED )
set_target_properties( avformat
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libavformat.so )


add_library( swresample
        SHARED
        IMPORTED )
set_target_properties( swresample
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libswresample.so )


add_library( swscale
        SHARED
        IMPORTED )
set_target_properties( swscale
        PROPERTIES IMPORTED_LOCATION
        ../jniLibs/arm64-v8a/libswscale.so )


#################################ffmepg#########################################


#################################tmediaplayer#########################################


add_library( # Sets the name of the library.
             tmediaplayer

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             tmediaplayer.cpp )

target_include_directories(tmediaplayer PUBLIC
        ffmpeg/header)


find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

target_link_libraries( # Specifies the target library.
        tmediaplayer
        avutil
        avcodec
        avdevice
        avfilter
        avformat
        swresample
        swscale
        ${log-lib}
        )

#################################tmediaplayer#########################################