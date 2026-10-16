package com.opencode2api.android.core

import android.content.Context
import java.io.File

/**
 * Locates the bundled Go gateway.
 *
 * The gateway is not a JNI library. It is a normal ELF executable that is
 * shipped under `jniLibs/<abi>/libopencode2api.so` for one reason: the Android
 * package manager only extracts and marks executable the files under
 * `nativeLibraryDir`, and it only does that for names matching `lib*.so`.
 * Android 10+ forbids executing binaries from the app's writable data
 * directory, so shipping the binary as an asset and chmod-ing it cannot work.
 *
 * Requiring `android:extractNativeLibs="true"` (see `useLegacyPackaging` in
 * `app/build.gradle.kts`) is what turns that extraction on; without it the
 * loader would rather mmap the library straight out of the APK, leaving no
 * file on disk to spawn.
 */
object GatewayBinary {

    const val FILE_NAME = "libopencode2api.so"

    /** Absolute path to the executable, whether or not it exists. */
    fun path(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, FILE_NAME)

    fun exists(context: Context): Boolean = path(context).let { it.isFile && it.canExecute() }

    /**
     * `true` when the device's CPU is an ABI this build actually ships a
     * gateway for. Guards against a mismatched APK install, which would
     * otherwise fail at spawn time with a confusing "no such file" error.
     */
    fun isAbiSupported(context: Context): Boolean = exists(context)
}
