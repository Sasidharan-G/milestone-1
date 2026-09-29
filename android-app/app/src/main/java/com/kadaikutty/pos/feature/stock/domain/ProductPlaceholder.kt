package com.kadaikutty.pos.feature.stock.domain

import androidx.annotation.DrawableRes
import com.kadaikutty.pos.R

/**
 * A per-category "no photo yet" placeholder, picked automatically so a product without its own
 * photo still shows something relevant instead of a bare grey icon - a blurred flour packet for
 * "Aashirvaad Atta", a blurred milk bottle for a dairy item, and so on.
 *
 * The product's own name is checked first: a brand name is far more specific than its category
 * ("Aashirvaad Atta" names the product; its category might just be "General"). The category name
 * is the fallback for a product whose name carries no recognisable keyword. Everything here is a
 * fixed local table - no network, no AI - so it works offline like the rest of billing.
 *
 * Keywords are plain lowercase English/Tanglish words as they actually appear on packaging and in
 * how a shop types a product name (even a Tamil-speaking shop types "Aashirvaad Atta", not the
 * Tamil script) - matching Tamil script itself is not attempted, since a bare word list can't
 * reliably cover a whole script the way substring matching covers Latin-script brand/product names.
 */
object ProductPlaceholder {
    private data class Category(@DrawableRes val res: Int, val keywords: List<String>)

    // Each list carries both product-name words ("milk", for matching "Amul Milk") and the
    // category's own label words ("dairy", for matching a Category named "Dairy Products") -
    // the same list serves the name-match pass and the category-name fallback pass.
    private val CATEGORIES = listOf(
        Category(R.drawable.placeholder_atta_grains, listOf(
            "atta", "aata", "flour", "maida", "rava", "sooji", "suji", "wheat", "godhumai", "grains")),
        Category(R.drawable.placeholder_rice, listOf(
            "rice", "basmati", "ponni", "arisi", "poha", "seeraga samba")),
        Category(R.drawable.placeholder_dal_pulses, listOf(
            "dal", "dhal", "toor", "moong", "urad", "chana", "rajma", "pulse", "lentil", "paruppu", "pulses")),
        Category(R.drawable.placeholder_dairy, listOf(
            "milk", "paal", "curd", "thayir", "paneer", "cheese", "butter", "yogurt", "dahi", "ghee", "amul", "dairy")),
        Category(R.drawable.placeholder_oil, listOf(
            "sunflower oil", "groundnut oil", "gingelly", "refined oil", "coconut oil", "til oil",
            "vanaspati", "cooking oil", "edible oil", "edible oils", "oil")),
        Category(R.drawable.placeholder_spices, listOf(
            "masala", "chilli", "chili", "milagai", "turmeric", "manjal", "jeera", "cumin",
            "coriander", "pepper", "spice", "haldi", "mirchi", "garam", "spices")),
        Category(R.drawable.placeholder_tea_coffee, listOf(
            "tea", "coffee", "kaapi", "horlicks", "bournvita", "boost", "cocoa", "chai")),
        Category(R.drawable.placeholder_beverages, listOf(
            "juice", "cola", "soda", "soft drink", "mineral water", "sharbat", "lassi", "beverages")),
        Category(R.drawable.placeholder_sugar_salt, listOf(
            "sugar", "sakkarai", "salt", "uppu", "jaggery", "vellam")),
        Category(R.drawable.placeholder_snacks, listOf(
            "biscuit", "cookie", "chips", "namkeen", "snack", "wafer", "murukku", "mixture", "kurkure", "snacks")),
        Category(R.drawable.placeholder_cleaning, listOf(
            "soap", "detergent", "surf", "washing", "dishwash", "cleaner", "phenyl", "bleach",
            "handwash", "sabun", "cleaning", "household")),
        Category(R.drawable.placeholder_personal_care, listOf(
            "shampoo", "toothpaste", "hair oil", "cream", "lotion", "talc", "deo", "perfume", "personal care")),
    )

    val GENERAL: Int get() = R.drawable.placeholder_general

    // Every (keyword, drawable) pair, longest keyword first - so "hair oil" and "sunflower oil"
    // are tried before the bare "oil" catches everything with that word in it.
    private val ranked = CATEGORIES
        .flatMap { category -> category.keywords.map { it to category.res } }
        .sortedByDescending { it.first.length }

    @DrawableRes
    fun forProduct(productName: String, categoryName: String? = null): Int {
        val name = productName.lowercase()
        ranked.firstOrNull { (keyword, _) -> name.contains(keyword) }?.let { return it.second }
        val category = categoryName?.lowercase().orEmpty()
        if (category.isNotBlank()) {
            ranked.firstOrNull { (keyword, _) -> category.contains(keyword) }?.let { return it.second }
        }
        return GENERAL
    }
}
