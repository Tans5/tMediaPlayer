# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


# region ffmpeg
add_library( libavcodec
        SHARED
        IMPORTED )
set_target_properties( libavcodec
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavcodec.so )

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

# libdav1d
add_library( libdav1d
        SHARED
        IMPORTED )
set_target_properties( libdav1d
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libdav1d.so )

# openssl
add_library( libssl
        SHARED
        IMPORTED )
set_target_properties( libssl
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libssl.so )

add_library( libcrypto
        SHARED
        IMPORTED )
set_target_properties( libcrypto
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcrypto.so )

# libsrt
add_library( libsrt
        SHARED
        IMPORTED )
set_target_properties( libsrt
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsrt.so )

# librtmp
add_library( librtmp
        SHARED
        IMPORTED )
set_target_properties( librtmp
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/librtmp.so )
# endregion

# region android
find_library(
        log-lib
        log )

find_library(
        android-lib
        android
)

find_library(
        OpenSLES-lib
        OpenSLES
)
# endregion

# region tmediaplayer
add_library(
        tmediaplayer SHARED
        tmediaplayer/tmediaplayer.cpp
        tmediaplayer/jni.cpp)

target_include_directories(tmediaplayer PUBLIC
        ffmpeg/header
        tmediaplayer/header)

target_link_libraries(
        tmediaplayer
        # android
        ${log-lib}
        ${android-lib}

        # ffmpeg
        libavcodec
        libavfilter
        libavformat
        libavutil
        libswresample
        libswscale
        libdav1d
        libssl
        libcrypto
        libsrt
        librtmp)
# endregion

# region tmediaframeloader
add_library(
        tmediaframeloader SHARED
        tmediaframeloader/jni.cpp
        tmediaframeloader/tmediaframeloader.cpp
)

target_include_directories(
        tmediaframeloader PUBLIC
        ffmpeg/header
        tmediaframeloader/header
        tmediaplayer/header
)

target_link_libraries(
        tmediaframeloader
        tmediaplayer
)
# endregion

# region tmediaaudiotrack
add_library(
        tmediaaudiotrack SHARED
        tmediaaudiotrack/jni.cpp
        tmediaaudiotrack/tmediaaudiotrack.cpp
)

target_include_directories(
        tmediaaudiotrack PUBLIC
        tmediaaudiotrack/header
        tmediaplayer/header
)

target_link_libraries(
        tmediaaudiotrack
        tmediaplayer
        ${OpenSLES-lib}
)
# endregion

#region tmediasubtitlepktreader
add_library(
        tmediasubtitlepktreader SHARED
        tmediasubtitlepktreader/jni.cpp
        tmediasubtitlepktreader/tmediasubtitlepktreader.cpp
)

target_include_directories(
        tmediasubtitlepktreader PUBLIC
        tmediasubtitlepktreader/header
        tmediaplayer/header
        ffmpeg/header
)

target_link_libraries(
        tmediasubtitlepktreader
        tmediaplayer
)
#endregion

#region tmediasubtitle
add_library(
        tmediasubtitle SHARED
        tmediasubtitle/jni.cpp
        tmediasubtitle/tmediasubtitle.cpp
)

target_include_directories(
        tmediasubtitle PUBLIC
        tmediasubtitle/header
        tmediaplayer/header
        tmediasubtitlepktreader/header
        ffmpeg/header
)

target_link_libraries(
        tmediasubtitle
        tmediaplayer
        tmediasubtitlepktreader
)
#endregion