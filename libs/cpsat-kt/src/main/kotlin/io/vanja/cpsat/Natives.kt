package io.vanja.cpsat

import com.google.ortools.Loader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe wrapper around [com.google.ortools.Loader.loadNativeLibraries].
 *
 * OR-Tools ships its solver as a native library that must be unpacked from a
 * classifier jar (e.g. `ortools-darwin-aarch64`) and loaded into the JVM
 * before any `com.google.ortools.sat.*` class is touched. Calling it more
 * than once is wasteful (and in some old versions, broken). We guard with an
 * [AtomicBoolean] so the first caller wins and subsequent calls are no-ops.
 *
 * Every public entry point in this library that ultimately constructs a
 * CP-SAT object calls [ensureNativesLoaded] first. You should rarely have to
 * call it yourself, but it's exposed for the rare case where you touch the
 * OR-Tools Java classes directly via `.toJava()` before using the DSL.
 */
public object Natives {
    private val loaded = AtomicBoolean(false)

    /**
     * Load OR-Tools native libraries exactly once per JVM. Safe to call from
     * any thread. Subsequent calls are cheap (just an atomic-boolean check).
     */
    public fun ensureNativesLoaded() {
        if (loaded.compareAndSet(false, true)) {
            try {
                Loader.loadNativeLibraries()
            } catch (t: Throwable) {
                // If loading failed, allow future callers to retry.
                loaded.set(false)
                throw t
            }
        }
    }
}

/** Shortcut for [Natives.ensureNativesLoaded]. */
public fun ensureNativesLoaded(): Unit = Natives.ensureNativesLoaded()
