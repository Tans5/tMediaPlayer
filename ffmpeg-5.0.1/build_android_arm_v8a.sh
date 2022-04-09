#./configure --disable-x86asm
# 清空上次的编译
make clean
# 这里先配置你的 NDK 路径
export NDK=/home/tans/Android/Sdk/ndk/23.0.7599858
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64


function build_android
{

./configure \
--prefix=$PREFIX \
--disable-postproc \
--disable-debug \
--disable-doc \
--enable-FFmpeg \
--disable-doc \
--disable-symver \
--disable-static \
--enable-shared \
--cross-prefix=$CROSS_PREFIX \
--target-os=android \
--arch=$ARCH \
--cpu=$CPU \
--cc=$CC \
--cxx=$CXX \
--enable-cross-compile \
--sysroot=$SYSROOT \
--extra-cflags="-Os -fpic $OPTIMIZE_CFLAGS" \
--extra-ldflags="$ADDI_LDFLAGS"

make clean
make -j16
make install

echo "============================ build android arm64-v8a success =========================="

}

# arm64-v8a
ARCH=arm64
CPU=armv8-a
API=21
CC=$TOOLCHAIN/bin/aarch64-linux-android$API-clang
CXX=$TOOLCHAIN/bin/aarch64-linux-android$API-clang++
SYSROOT=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot
CROSS_PREFIX=$TOOLCHAIN/bin/aarch64-linux-android-
PREFIX=$(pwd)/android/$CPU
OPTIMIZE_CFLAGS="-march=$CPU"

echo $CC

build_android