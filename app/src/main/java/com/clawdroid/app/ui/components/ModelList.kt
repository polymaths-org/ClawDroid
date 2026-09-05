package com.clawdroid.app.ui.components

data class PartitionedModels(
    val free: List<String>,
    val paid: List<String>,
)

fun partitionZenModels(models: List<String>, isFree: (String) -> Boolean, paidLimit: Int = 60): PartitionedModels {
    val free = models.filter(isFree)
    val paid = models.filterNot(isFree).take(paidLimit.coerceAtLeast(0))
    return PartitionedModels(free = free, paid = paid)
}

fun visibleModelOptions(all: List<String>, query: String, limit: Int = 60): List<String> {
    val q = query.trim().lowercase()
    val filtered = if (q.isBlank()) all else all.filter { it.lowercase().contains(q) }
    return filtered.take(limit.coerceAtLeast(0))
}
