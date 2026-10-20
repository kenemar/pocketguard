# Toolchain locations for build.sh
export ANDROID_SDK="${ANDROID_SDK:-$HOME/android-sdk}"
export BUILD_TOOLS="$ANDROID_SDK/build-tools/34.0.0"
export PLATFORM="$ANDROID_SDK/platforms/android-33"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$PATH"
