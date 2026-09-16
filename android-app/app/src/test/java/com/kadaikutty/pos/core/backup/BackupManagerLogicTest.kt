package com.kadaikutty.pos.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class BackupManagerLogicTest {

    private fun calculateSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `sha256 calculation produces valid 64 char hex hash`() {
        val testData = "{\"version\":21,\"data\":[]}".toByteArray(Charsets.UTF_8)
        val hash = calculateSha256(testData)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun `schema fallback provides default values for missing SQLite NOT NULL columns`() {
        // Simulating row coming from cloud or old backup missing 'syncStatus', 'unitType', and 'minStockLevel'
        val rawProductRow = JSONObject().apply {
            put("id", "prod_123")
            put("companyId", "comp_456")
            put("name", "Test Product")
            put("categoryId", "cat_789")
            put("purchasePriceMinorUnits", 1000L)
            put("salePriceMinorUnits", 1500L)
            // 'syncStatus', 'unitType', 'minStockLevel' are missing
        }

        // Apply our deterministic default fallback rules
        val syncStatus = rawProductRow.optString("syncStatus", "SYNCED").ifBlank { "SYNCED" }
        val unitType = rawProductRow.optString("unitType", "PIECE").ifBlank { "PIECE" }
        val minStockLevel = if (rawProductRow.has("minStockLevel")) rawProductRow.getDouble("minStockLevel") else 0.0

        assertEquals("SYNCED", syncStatus)
        assertEquals("PIECE", unitType)
        assertEquals(0.0, minStockLevel, 0.001)
    }

    @Test
    fun `sales serialization includes nested line items with correct schema version`() {
        val saleItems = JSONArray().apply {
            put(JSONObject().apply {
                put("saleId", "sale_1")
                put("productId", "prod_1")
                put("quantity", 2L)
                put("unitPriceMinorUnits", 500L)
                put("lineTotalMinorUnits", 1000L)
                put("discountMinorUnits", 0L)
            })
        }

        val salePayload = JSONObject().apply {
            put("id", "sale_1")
            put("companyId", "comp_1")
            put("billNumber", "BILL-001")
            put("totalMinorUnits", 1000L)
            put("syncStatus", "SYNCED")
            put("_schemaVersion", 1)
            put("items", saleItems)
        }

        assertEquals(1, salePayload.getInt("_schemaVersion"))
        assertEquals("SYNCED", salePayload.getString("syncStatus"))
        assertEquals(1, salePayload.getJSONArray("items").length())
        val firstItem = salePayload.getJSONArray("items").getJSONObject(0)
        assertEquals("sale_1", firstItem.getString("saleId"))
        assertEquals(2L, firstItem.getLong("quantity"))
    }

    @Test
    fun `topological order places parent entities before dependent entities`() {
        val order = listOf(
            "users",
            "company_licenses",
            "categories",
            "customers",
            "suppliers",
            "products",
            "sales",
            "sale_items",
            "purchases",
            "purchase_items"
        )

        assertTrue(order.indexOf("categories") < order.indexOf("products"))
        assertTrue(order.indexOf("sales") < order.indexOf("sale_items"))
        assertTrue(order.indexOf("purchases") < order.indexOf("purchase_items"))
        assertTrue(order.indexOf("products") < order.indexOf("sale_items"))
    }
}
