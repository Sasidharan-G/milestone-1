package com.kadaikutty.pos.core.branding

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a shop's logo lives in the cloud. */
interface ShopLogoCloud {
    /** Saves the picture and returns the version (the server's time) the shop profile now carries. */
    suspend fun upload(picture: ByteArray): Long

    /** The cloud's picture, or null when the shop has none. */
    suspend fun download(): ByteArray?

    /** Removes the cloud picture and returns the version the shop profile now carries. */
    suspend fun remove(): Long
}

/** What this phone remembers about the logo: the file it shows, the file the cloud last got from here, and the cloud version it matches. */
class ShopLogoState(val path: String, val uploadedPath: String, val version: Long)

/** This phone's side of the logo. */
interface ShopLogoLocal {
    suspend fun state(): ShopLogoState

    /** The picture's bytes, or null when the file is gone or unreadable. */
    suspend fun read(path: String): ByteArray?

    /** Makes a picture that came from the cloud this phone's logo; nothing is left to upload. */
    suspend fun adopt(picture: ByteArray, version: Long)

    /** The cloud now holds [path] as of [version]. */
    suspend fun markUploaded(path: String, version: Long)

    /** This phone has no logo (and the cloud has none either, as far as it knows). */
    suspend fun clear(version: Long)
}

/**
 * Keeps the shop logo the same on the phone and in the cloud, so it survives a reinstall and
 * appears on every device of the shop. Run whenever the shop profile is fetched or saved.
 *
 * In this order:
 *  1. A logo chosen on this phone that the cloud does not have yet is uploaded.
 *  2. A logo the owner removed on this phone is removed from the cloud.
 *  3. Otherwise the cloud wins: its logo is downloaded when this phone has none or an older one,
 *     and a logo removed on another device is dropped here. If the cloud merely lost its picture
 *     (nothing says it was removed), this phone's copy is sent again rather than thrown away.
 *
 * Anything that fails (offline, server down) simply throws; nothing was marked as done, so the
 * next run starts from the same place and tries again.
 */
class ShopLogoSync(private val cloud: ShopLogoCloud, private val local: ShopLogoLocal) {
    enum class Outcome { IN_SYNC, UPLOADED, REMOVED_FROM_CLOUD, DOWNLOADED, CLEARED_HERE }

    // The settings screen and the periodic profile refresh can both ask at once.
    private val lock = Mutex()

    /** [remoteVersion] and [remoteHasLogo] come from the shop profile the server returned. */
    suspend fun sync(remoteVersion: Long, remoteHasLogo: Boolean): Outcome = lock.withLock {
        var state = local.state()

        if (state.path.isNotBlank() && state.path != state.uploadedPath) {
            val picture = local.read(state.path)
            if (picture != null) {
                local.markUploaded(state.path, cloud.upload(picture))
                return Outcome.UPLOADED
            }
            // The chosen file has vanished; there is nothing to send, so follow the cloud instead.
            local.clear(0)
            state = local.state()
        }

        if (state.path.isBlank() && state.uploadedPath.isNotBlank()) {
            local.clear(cloud.remove())
            return Outcome.REMOVED_FROM_CLOUD
        }

        // Versions are server times and only move forward. A profile older than the one this phone
        // already matched was read before a change made here (or by an older server): it says nothing
        // new, and acting on it could replace or drop a logo that was just saved.
        if (remoteVersion < state.version) return Outcome.IN_SYNC

        if (remoteHasLogo) {
            val haveCurrent = state.path.isNotBlank() && state.version == remoteVersion && local.read(state.path) != null
            if (haveCurrent) return Outcome.IN_SYNC
            val picture = cloud.download()
            if (picture != null) {
                local.adopt(picture, remoteVersion)
                return Outcome.DOWNLOADED
            }
            // The profile says there is a logo but the picture is gone from the cloud: handled below.
        }

        if (state.path.isBlank()) return Outcome.IN_SYNC

        // The cloud has no picture, yet this phone shows one it believes the cloud holds.
        if (!remoteHasLogo && remoteVersion > state.version) {
            // The cloud moved on without a logo: it was removed on another device.
            local.clear(remoteVersion)
            return Outcome.CLEARED_HERE
        }
        // Nothing says it was removed, so the cloud simply lost it; this phone's copy may be the only one left.
        val mine = local.read(state.path)
        if (mine != null) {
            local.markUploaded(state.path, cloud.upload(mine))
            return Outcome.UPLOADED
        }
        local.clear(remoteVersion)
        return Outcome.CLEARED_HERE
    }
}
