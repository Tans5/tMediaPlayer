# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.18.1)


project("tmediaplayer")


#################################ffmepg#########################################
#aux_source_directory(ffmepg ffmpeg_src)
#add_library(
#        ffmepg
#        SHARED
#        ${ffmpeg_src})

#################################ffmepg#########################################

add_library( # Sets the name of the library.
             tmediaplayer

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             tmediaplayer.cpp )



find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

target_link_libraries( # Specifies the target library.
                       tmediaplayer

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )