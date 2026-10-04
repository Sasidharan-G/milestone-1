package com.kadaikutty.pos.core.branding

import com.kadaikutty.pos.core.branding.ShopLogoSync.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ShopLogoSyncTest {

    /** The cloud: one logo at most, with a version that moves each time it changes. */
    private class FakeCloud(var picture: ByteArray? = null, var version: Long = 0, var offline: Boolean = false) : ShopLogoCloud {
        var uploads = 0
        var downloads = 0
        var removals = 0
        var failUploads = false
        private fun requireOnline() { if (offline) throw IOException("no connection") }
        override suspend fun upload(picture: ByteArray): Long {
            requireOnline()
            if (failUploads) throw IOException("storage busy")
            uploads++; this.picture = picture; version += 100; return version
        }
        override suspend fun download(): ByteArray? { requireOnline(); downloads++; return picture }
        override suspend fun remove(): Long { requireOnline(); removals++; picture = null; version += 100; return version }
        val hasLogo get() = picture != null
    }

    /** This phone: preferences plus a few files. */
    private class FakePhone(var path: String = "", var uploadedPath: String = "", var version: Long = 0) : ShopLogoLocal {
        val files = HashMap<String, ByteArray>()
        override suspend fun state() = ShopLogoState(path, uploadedPath, version)
        override suspend fun read(path: String): ByteArray? = files[path]
        override suspend fun adopt(picture: ByteArray, version: Long) {
            val file = "/files/logos/cloud_$version.png"
            files[file] = picture
            path = file
            uploadedPath = file
            this.version = version
        }
        override suspend fun markUploaded(path: String, version: Long) { uploadedPath = path; this.version = version }
        override suspend fun clear(version: Long) { path = ""; uploadedPath = ""; this.version = version }
        fun choose(file: String, bytes: ByteArray) { files[file] = bytes; path = file }
    }

    private val logo = byteArrayOf(1, 2, 3, 4)

    private fun run(cloud: FakeCloud, phone: FakePhone): Outcome = runBlocking {
        ShopLogoSync(cloud, phone).sync(cloud.version, cloud.hasLogo)
    }

    @Test fun a_logo_chosen_on_this_phone_is_uploaded() {
        val cloud = FakeCloud()
        val phone = FakePhone().also { it.choose("/l/a.png", logo) }
        assertEquals(Outcome.UPLOADED, run(cloud, phone))
        assertArrayEquals(logo, cloud.picture)
        assertEquals("a.png is now what the cloud holds", "/l/a.png", phone.uploadedPath)
        assertEquals(cloud.version, phone.version)
    }

    @Test fun a_logo_that_was_only_on_this_phone_before_cloud_logos_existed_is_sent_once() {
        val cloud = FakeCloud()
        val phone = FakePhone().also { it.choose("/l/old.png", logo) } // uploadedPath is empty
        assertEquals(Outcome.UPLOADED, run(cloud, phone))
        assertEquals(Outcome.IN_SYNC, run(cloud, phone))
        assertEquals(1, cloud.uploads)
    }

    @Test fun a_new_phone_downloads_the_shops_logo_so_a_reinstall_loses_nothing() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone()
        assertEquals(Outcome.DOWNLOADED, run(cloud, phone))
        assertArrayEquals(logo, phone.files[phone.path])
        assertEquals(500L, phone.version)
        assertEquals("nothing is left to upload", phone.path, phone.uploadedPath)
        assertEquals(Outcome.IN_SYNC, run(cloud, phone))
        assertEquals(1, cloud.downloads)
        assertEquals(0, cloud.uploads)
    }

    @Test fun nothing_happens_when_both_sides_already_match() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500).also { it.files["/l/a.png"] = logo }
        assertEquals(Outcome.IN_SYNC, run(cloud, phone))
        assertEquals(0, cloud.uploads + cloud.downloads + cloud.removals)
    }

    @Test fun a_logo_changed_on_another_device_is_downloaded() {
        val cloud = FakeCloud(picture = byteArrayOf(9, 9), version = 700)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500).also { it.files["/l/a.png"] = logo }
        assertEquals(Outcome.DOWNLOADED, run(cloud, phone))
        assertArrayEquals(byteArrayOf(9, 9), phone.files[phone.path])
        assertEquals(700L, phone.version)
    }

    @Test fun a_logo_replaced_on_this_phone_replaces_the_cloud_one() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500).also { it.files["/l/a.png"] = logo; it.choose("/l/b.png", byteArrayOf(7)) }
        assertEquals(Outcome.UPLOADED, run(cloud, phone))
        assertArrayEquals(byteArrayOf(7), cloud.picture)
        assertEquals("/l/b.png", phone.uploadedPath)
    }

    @Test fun removing_the_logo_on_this_phone_removes_it_from_the_cloud() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone("", "/l/a.png", 500) // owner pressed Remove: no file, but the cloud still has the last upload
        assertEquals(Outcome.REMOVED_FROM_CLOUD, run(cloud, phone))
        assertFalse(cloud.hasLogo)
        assertEquals("", phone.uploadedPath)
        assertEquals(Outcome.IN_SYNC, run(cloud, phone))
    }

    @Test fun a_logo_removed_on_another_device_is_dropped_here_too() {
        val cloud = FakeCloud(picture = null, version = 800)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500).also { it.files["/l/a.png"] = logo }
        assertEquals(Outcome.CLEARED_HERE, run(cloud, phone))
        assertEquals("", phone.path)
        assertEquals(0, cloud.uploads)
    }

    @Test fun nothing_anywhere_is_in_sync() {
        assertEquals(Outcome.IN_SYNC, run(FakeCloud(), FakePhone()))
    }

    @Test fun being_offline_changes_nothing_and_the_upload_is_tried_again_later() {
        val cloud = FakeCloud(offline = true)
        val phone = FakePhone().also { it.choose("/l/a.png", logo) }
        try { run(cloud, phone); fail("should have failed") } catch (expected: IOException) { }
        assertEquals("still waiting to be uploaded", "", phone.uploadedPath)
        assertEquals("/l/a.png", phone.path)
        cloud.offline = false
        assertEquals(Outcome.UPLOADED, run(cloud, phone))
    }

    @Test fun a_failed_removal_is_tried_again_later() {
        val cloud = FakeCloud(picture = logo, version = 500, offline = true)
        val phone = FakePhone("", "/l/a.png", 500)
        try { run(cloud, phone); fail("should have failed") } catch (expected: IOException) { }
        assertEquals("/l/a.png", phone.uploadedPath)
        cloud.offline = false
        assertEquals(Outcome.REMOVED_FROM_CLOUD, run(cloud, phone))
        assertTrue(!cloud.hasLogo)
    }

    @Test fun a_chosen_file_that_has_vanished_falls_back_to_the_cloud_logo() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone("/l/gone.png", "", 0) // chosen, then the file was deleted: nothing to upload
        assertEquals(Outcome.DOWNLOADED, run(cloud, phone))
        assertArrayEquals(logo, phone.files[phone.path])
        assertEquals(0, cloud.uploads)
    }

    @Test fun a_saved_logo_file_that_has_vanished_is_downloaded_again() {
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500) // preferences remember it, the file is gone
        assertEquals(Outcome.DOWNLOADED, run(cloud, phone))
        assertArrayEquals(logo, phone.files[phone.path])
    }

    @Test fun a_profile_that_claims_a_logo_the_cloud_does_not_have_is_treated_as_no_logo() {
        val cloud = FakeCloud(picture = null, version = 500)
        val phone = FakePhone()
        assertEquals(Outcome.IN_SYNC, runBlocking { ShopLogoSync(cloud, phone).sync(500, remoteHasLogo = true) })
        assertEquals("", phone.path)
        assertNull(cloud.picture)
    }

    private fun syncWith(cloud: FakeCloud, phone: FakePhone, remoteVersion: Long, remoteHasLogo: Boolean): Outcome =
        runBlocking { ShopLogoSync(cloud, phone).sync(remoteVersion, remoteHasLogo) }

    private fun phoneInSync(version: Long = 500) =
        FakePhone("/l/a.png", "/l/a.png", version).also { it.files["/l/a.png"] = logo }

    @Test fun a_profile_older_than_what_this_phone_already_knows_changes_nothing() {
        // The refresh read the profile, then the owner saved a new logo here (version 500); the old read must not undo it.
        val cloud = FakeCloud(picture = logo, version = 500)
        val phone = phoneInSync(500)
        assertEquals(Outcome.IN_SYNC, syncWith(cloud, phone, remoteVersion = 400, remoteHasLogo = false))
        assertEquals("/l/a.png", phone.path)
        assertEquals(Outcome.IN_SYNC, syncWith(cloud, phone, remoteVersion = 400, remoteHasLogo = true))
        assertEquals("/l/a.png", phone.path)
        assertEquals(0, cloud.uploads + cloud.downloads + cloud.removals)
    }

    @Test fun an_older_server_that_knows_nothing_about_logos_leaves_the_phones_logo_alone() {
        val cloud = FakeCloud()
        val phone = phoneInSync(500)
        assertEquals(Outcome.IN_SYNC, syncWith(cloud, phone, remoteVersion = 0, remoteHasLogo = false))
        assertEquals("/l/a.png", phone.path)
        assertEquals(0, cloud.uploads + cloud.downloads + cloud.removals)
    }

    @Test fun a_picture_the_cloud_lost_is_sent_again_from_this_phone() {
        val cloud = FakeCloud(picture = null, version = 500) // the profile still says "has a logo", the picture is gone
        val phone = phoneInSync(400) // an older logo than the profile's, so this phone asks for the cloud's picture
        assertEquals(Outcome.UPLOADED, syncWith(cloud, phone, remoteVersion = 500, remoteHasLogo = true))
        assertEquals(1, cloud.downloads)
        assertArrayEquals(logo, cloud.picture)
        assertEquals("/l/a.png", phone.path)
    }

    @Test fun a_phone_that_already_matches_the_profile_does_not_go_looking_for_the_picture() {
        val cloud = FakeCloud(picture = null, version = 500)
        val phone = phoneInSync(500)
        assertEquals(Outcome.IN_SYNC, syncWith(cloud, phone, remoteVersion = 500, remoteHasLogo = true))
        assertEquals(0, cloud.downloads + cloud.uploads)
    }

    @Test fun a_profile_with_the_same_version_but_no_logo_gets_this_phones_copy() {
        val cloud = FakeCloud(picture = null, version = 500)
        val phone = phoneInSync(500)
        assertEquals(Outcome.UPLOADED, syncWith(cloud, phone, remoteVersion = 500, remoteHasLogo = false))
        assertArrayEquals(logo, cloud.picture)
    }

    @Test fun a_logo_the_cloud_lost_and_whose_file_is_gone_here_too_is_cleared() {
        val cloud = FakeCloud(picture = null, version = 500)
        val phone = FakePhone("/l/a.png", "/l/a.png", 500) // no file
        assertEquals(Outcome.CLEARED_HERE, syncWith(cloud, phone, remoteVersion = 500, remoteHasLogo = false))
        assertEquals("", phone.path)
        assertEquals(0, cloud.uploads)
    }

    @Test fun a_failed_resend_of_a_lost_picture_keeps_the_phones_copy_and_tries_again() {
        val cloud = FakeCloud(picture = null, version = 500).also { it.failUploads = true }
        val phone = phoneInSync(400)
        try { syncWith(cloud, phone, 500, true); fail("should have failed") } catch (expected: IOException) { }
        assertEquals("/l/a.png", phone.path)
        assertEquals("/l/a.png", phone.uploadedPath)
        assertEquals(400L, phone.version)
        cloud.failUploads = false
        assertEquals(Outcome.UPLOADED, syncWith(cloud, phone, 500, true))
        assertArrayEquals(logo, cloud.picture)
    }

    @Test fun a_failed_upload_leaves_the_logo_pending_for_the_next_try() {
        val cloud = FakeCloud().also { it.failUploads = true }
        val phone = FakePhone().also { it.choose("/l/a.png", logo) }
        try { run(cloud, phone); fail("should have failed") } catch (expected: IOException) { }
        assertEquals("not marked as uploaded", "", phone.uploadedPath)
        cloud.failUploads = false
        assertEquals(Outcome.UPLOADED, run(cloud, phone))
        assertEquals("/l/a.png", phone.uploadedPath)
    }

    @Test fun two_syncs_at_once_do_not_upload_twice() {
        val cloud = FakeCloud()
        val phone = FakePhone().also { it.choose("/l/a.png", logo) }
        val sync = ShopLogoSync(cloud, phone)
        runBlocking {
            val first = async { sync.sync(0, false) }
            val second = async { sync.sync(0, false) }
            first.await()
            second.await()
        }
        assertEquals(1, cloud.uploads)
    }
}
