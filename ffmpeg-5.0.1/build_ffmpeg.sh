#!/bin/bash

ARCH=$1

source config.sh $ARCH
LIBS_DIR=$(cd `dirname $0`; pwd)/libs/lib-ffmpeg
echo "LIBS_DIR="$LIBS_DIR

PLATFORM=$ANDROID_NDK_ROOT/platforms/$AOSP_API/$AOSP_ARCH
TOOLCHAIN=$ANDROID_NDK_ROOT/toolchains/$TOOLCHAIN_BASE-$AOSP_TOOLCHAIN_SUFFIX/prebuilt/linux-x86_64

PREFIX=$LIBS_DIR/$AOSP_ABI

./configure \
--prefix=$PREFIX \
--enable-cross-compile \
--disable-runtime-cpudetect \
--disable-asm \
--arch=$AOSP_ABI \
--target-os=android \
--cc=$TOOLCHAIN/bin/$TOOLNAME_BASE-gcc \
--cross-prefix=$TOOLCHAIN/bin/$TOOLNAME_BASE- \
--disable-stripping \
--nm=$TOOLCHAIN/bin/$TOOLNAME_BASE-nm \
--sysroot=$PLATFORM \
--enable-gpl \
--enable-shared \
--disable-static \
--enable-small \
--disable-outdevs \
--disable-ffprobe \
--disable-ffplay \
--disable-ffmpeg \
--disable-ffserver \
--disable-debug \
--disable-ffprobe \
--disable-ffplay \
--disable-ffmpeg \
--disable-postproc \
--disable-avdevice \
--disable-symver \
--disable-stripping \
--extra-cflags="$FF_EXTRA_CFLAGS  $FF_CFLAGS" \
--extra-ldflags="  "

make clean
make -j16
make install

mv ./libavdevice/libavdevice.soavdevice-59.so ./android/$ARCH/libavdevice.so
mv ./libavformat/libavformat.soavformat-59.so ./android/$ARCH/libavformat.so
mv ./libswresample/libswresample.soswresample-4.so ./android/$ARCH/libswresample.so
mv ./libswscale/libswscale.soswscale-6.so ./android/$ARCH/libswscale.so
mv ./libavcodec/libavcodec.soavcodec-59.so ./android/$ARCH/libavcodec.so
mv ./libavutil/libavutil.soavutil-57.so ./android/$ARCH/libavutil.so
mv ./libavfilter/libavfilter.soavfilter-8.so ./android/$ARCH/libavfilter.so