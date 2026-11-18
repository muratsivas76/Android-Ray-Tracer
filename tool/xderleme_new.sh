#!/bin/bash

./clean.sh

# Android SDK yollari
export ANDROID_HOME=/usr/java/android
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/build-tools/33.0.2:$ANDROID_HOME/platform-tools

# Java yolu
export JAVA_HOME=/usr/java/jdk-25
export PATH=$JAVA_HOME/bin:$PATH

# Proje dizinleri
PROJECT_DIR=$(pwd)
BUILD_DIR=$PROJECT_DIR/obj
DIST_DIR=$PROJECT_DIR/bin
SRC_DIR=$PROJECT_DIR/src
RES_DIR=$PROJECT_DIR/res
ASSETS_DIR=$PROJECT_DIR/assets

# Temizlik
echo "Cleaning..."
rm -rf $BUILD_DIR $DIST_DIR
mkdir -p $BUILD_DIR $DIST_DIR

# Keystore olustur (yoksa)
if [ ! -f TGS.keystore ]; then
    echo "Creating keystore..."
    $JAVA_HOME/bin/keytool -genkey -alias TGS.keystore -keyalg RSA -validity 1000000 -dname "CN=Murat inan, OU=Freelance, O=Traductor, S=Merkez, C=TR" -keystore TGS.keystore -storepass 5EmrE432 -keypass 5EmrE432
fi

# Resource'lari derle ve R.java olustur
echo "Compiling resources..."
$ANDROID_HOME/build-tools/33.0.2/aapt package -v -f -m -S $RES_DIR -J $SRC_DIR -M AndroidManifest.xml -I $ANDROID_HOME/platforms/android-33/android.jar

# Java kodlarini derle (Java 8 uyumlulugu ile)
echo "Compiling Java code..."
$JAVA_HOME/bin/javac -source 1.8 -target 1.8 -bootclasspath $ANDROID_HOME/platforms/android-33/android.jar -sourcepath $SRC_DIR -cp $ANDROID_HOME/platforms/android-33/android.jar -g:none -proc:none -nowarn -O -d $BUILD_DIR $SRC_DIR/net/murat/tg/TGS.java

# DEX dosyasi olustur (dx ile)
$ANDROID_HOME/build-tools/29.0.0/dx --dex --verbose --output=bin/classes.dex obj libs

# Imzasiz APK olustur
echo "Creating unsigned APK..."
$ANDROID_HOME/build-tools/33.0.2/aapt package -v -f -M AndroidManifest.xml -A $ASSETS_DIR -S $RES_DIR -I $ANDROID_HOME/platforms/android-33/android.jar -F $DIST_DIR/TGS.unsigned.apk $DIST_DIR

# APK'yi imzala
echo "Signing APK..."
$JAVA_HOME/bin/jarsigner -verbose -keystore TGS.keystore -storepass 5EmrE432 -keypass 5EmrE432 -signedjar $DIST_DIR/TGS.signed.apk $DIST_DIR/TGS.unsigned.apk TGS.keystore

# Zipalign
echo "Zipaligning..."
$ANDROID_HOME/build-tools/33.0.2/zipalign -v -f 4 $DIST_DIR/TGS.signed.apk $DIST_DIR/TGS.apk

# Gecici dosyalari temizle
echo "Final cleaning..."
rm -f $DIST_DIR/*.signed.* $DIST_DIR/*.unsigned.* $DIST_DIR/classes.dex

echo " "
echo "Run this firstly: "
echo "$ANDROID_HOME/tools/emulator -wipe-data -avd MuratAVD &"

echo " "
echo "Run this when the emulator is ready:"
echo "$ANDROID_HOME/tools/adb -e install $DIST_DIR/TGS.apk"

echo " "
echo "You can remove it from emulator with this command:"
echo "$ANDROID_HOME/tools/adb shell rm /data/app/net.murat.tg.apk"

echo " "
echo "You can uninstall it from emulator with this command:"
echo "$ANDROID_HOME/tools/adb -e uninstall net.murat.tg"
echo " "
