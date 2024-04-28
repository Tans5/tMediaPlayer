# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


################################# ffmepg start #########################################
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

################################# ffmepg end #########################################


################################# tmediaplayer start #########################################

#file(GLOB examples_src
#        examples/*.c)

add_library(
        tmediaplayer SHARED
        # tmediaplayer
        tmediaplayer/media_time.cpp
        tmediaplayer/tmediaplayer.cpp
        tmediaplayer/jni.cpp)

target_include_directories(tmediaplayer PUBLIC
        ffmpeg/header
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

target_link_libraries( # Specifies the target library.
        tmediaplayer
        ${log-lib}
        ${android-lib}

        # ffmpeg
        libavcodec
        #libavdevice
        libavfilter
        libavformat
        libavutil
        libswresample
        libswscale)

################################# tmediaplayer end #########################################


################################# tmediaframeloader start ##################################
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

################################# tmediaframeloader end ####################################

################################# tmediaaudiotrack start ##################################
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

find_library(
        OpenSLES-lib
        OpenSLES
)

target_link_libraries(
        tmediaaudiotrack
        tmediaplayer
        ${OpenSLES-lib}
)

################################# tmediaaudiotrack end ####################################