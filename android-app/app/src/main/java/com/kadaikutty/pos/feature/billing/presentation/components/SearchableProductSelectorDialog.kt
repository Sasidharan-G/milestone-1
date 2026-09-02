package com.kadaikutty.pos.feature.billing.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableProductSelectorDialog(
    showDialog: Boolean,
    products: List<ProductEntity>,
    stockMap: Map<String, Long>,
    onProductSelected: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Fuzzy Match Helper (Levenshtein Distance & Substring scoring)
    val (filteredProducts, suggestedProduct) = remember(query, products) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            Pair(products, null)
        } else {
            // 1. Direct / Substring matches
            val directMatches = products.filter { prod ->
                prod.name.lowercase().contains(q) ||
                (prod.barcode != null && prod.barcode.lowercase().contains(q))
            }

            if (directMatches.isNotEmpty()) {
                Pair(directMatches, null)
            } else {
                // 2. Fuzzy Typo Match ("Did you mean...?")
                val scoredProducts = products.map { prod ->
                    val nameLower = prod.name.lowercase()
                    val dist = calculateLevenshteinDistance(q, nameLower)
                    val similarity = calculateSimilarity(q, nameLower)
                    Triple(prod, dist, similarity)
                }.sortedByDescending { it.third }

                val topCandidate = scoredProducts.firstOrNull { it.third >= 0.35 || it.second <= 3 }
                val fuzzyList = scoredProducts.filter { it.third >= 0.25 }.map { it.first }

                Pair(fuzzyList.ifEmpty { products }, topCandidate?.first)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Header & Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search product name, barcode...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // 2. "Did you mean...?" Suggestion Banner for Typos
                if (suggestedProduct != null && query.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onProductSelected(suggestedProduct)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Did you mean?",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = suggestedProduct.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Select ➔",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 3. Products List (Zero Vertical Text Wrapping!)
                Text(
                    text = if (query.isBlank()) "All Products (${products.size})" else "Matching Products (${filteredProducts.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No product found matching \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val pStock = stockMap[product.id] ?: 0L
                            val isDecimal = product.unitType == "KG" || product.unitType == "LITER"
                            val pStockStr = if (isDecimal) {
                                String.format(Locale.US, "%.3f %s", pStock / 1000.0, if (product.unitType == "KG") "Kg" else "Ltr")
                            } else "$pStock Pcs"
                            val isOutOfStock = pStock <= 0L

                            Card(
                                onClick = {
                                    onProductSelected(product)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Row 1: Product Name (Single Line, Ellipsis if too long)
                                    Text(
                                        text = product.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Row 2: Price + Unit + Stock Badge (Clean horizontal alignment!)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${Money(product.salePriceMinorUnits)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF059669)
                                        )

                                        val isLowStock = !isOutOfStock && ((isDecimal && pStock <= 5000L) || (!isDecimal && pStock <= 5L))

                                        Surface(
                                            color = when {
                                                isOutOfStock -> MaterialTheme.colorScheme.errorContainer
                                                isLowStock -> Color(0xFFFEF3C7)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    isOutOfStock -> "Out of Stock"
                                                    isLowStock -> "⚠️ Low Stock: $pStockStr"
                                                    else -> "Stock: $pStockStr"
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isOutOfStock -> MaterialTheme.colorScheme.onErrorContainer
                                                    isLowStock -> Color(0xFFB45309)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Levenshtein Distance Algorithm for Typo Detection
private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length
    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j

    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,       // deletion
                dp[i][j - 1] + 1,       // insertion
                dp[i - 1][j - 1] + cost // substitution
            )
        }
    }
    return dp[m][n]
}

// Similarity Ratio (0.0 to 1.0)
private fun calculateSimilarity(query: String, target: String): Double {
    if (query.isEmpty() || target.isEmpty()) return 0.0
    if (target.contains(query)) return 0.9

    // Check individual words
    val words = target.split(" ")
    var bestWordScore = 0.0
    for (w in words) {
        val dist = calculateLevenshteinDistance(query, w)
        val maxLen = maxOf(query.length, w.length)
        val score = 1.0 - (dist.toDouble() / maxLen.toDouble())
        if (score > bestWordScore) bestWordScore = score
    }

    val totalDist = calculateLevenshteinDistance(query, target)
    val totalMax = maxOf(query.length, target.length)
    val wholeScore = 1.0 - (totalDist.toDouble() / totalMax.toDouble())

    return maxOf(bestWordScore, wholeScore)
}
