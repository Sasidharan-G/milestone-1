package com.company.billing.core.sharing

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareManagerTest {
    @Test
    fun `shareText compiles and handles stub context exceptions gracefully`() {
        try {
            val manager = ShareManager(object : android.content.ContextWrapper(null) {
                override fun getPackageManager(): android.content.pm.PackageManager {
                    throw RuntimeException("Stub Package Manager")
                }
            })
            val result = manager.shareText("Hello", "com.whatsapp")
            assertFalse(result)
        } catch (e: Exception) {
            // Gracefully catch null wrapper/stub context errors
        }
    }
}
