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

add_library( libaom
        SHARED
        IMPORTED )
set_target_properties( libamo
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libaom.so )
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
        libaom)
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