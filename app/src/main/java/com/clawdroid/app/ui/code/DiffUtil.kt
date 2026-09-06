package com.clawdroid.app.ui.code

enum class DiffKind { SAME, ADD, DEL }

data class DiffLine(
    val oldNo: Int?,
    val newNo: Int?,
    val text: String,
    val kind: DiffKind,
)

data class FileDiff(
    val path: String,
    val lines: List<DiffLine>,
    val added: Int,
    val deleted: Int,
    val truncated: Boolean = false,
)

private const val MAX_DIFF_LINES = 300

/** GitHub-style line diff between old and new text. */
fun diffTexts(path: String, oldText: String, newText: String): FileDiff {
    var old = oldText.lines()
    var new = newText.lines()
    var truncated = false
    if (old.size > MAX_DIFF_LINES || new.size > MAX_DIFF_LINES) {
        old = old.take(MAX_DIFF_LINES)
        new = new.take(MAX_DIFF_LINES)
        truncated = true
    }
    val n = old.size
    val m = new.size
    // LCS lengths (suffix DP for forward walk)
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (old[i] == new[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val lines = mutableListOf<DiffLine>()
    var added = 0
    var deleted = 0
    var i = 0
    var j = 0
    var oldNo = 1
    var newNo = 1
    while (i < n || j < m) {
        when {
            i < n && j < m && old[i] == new[j] -> {
                lines.add(DiffLine(oldNo++, newNo++, old[i], DiffKind.SAME))
                i++
                j++
            }
            j < m && (i >= n || dp[i][j + 1] >= dp[i + 1][j]) -> {
                lines.add(DiffLine(null, newNo++, new[j], DiffKind.ADD))
                added++
                j++
            }
            else -> {
                lines.add(DiffLine(oldNo++, null, old[i], DiffKind.DEL))
                deleted++
                i++
            }
        }
    }
    return FileDiff(path, lines, added, deleted, truncated)
}

/** An edit_file call (search → replace) as a diff. */
fun diffFromEdit(path: String, search: String, replace: String): FileDiff =
    diffTexts(path, search, replace)

/** A write_file call (whole new content) as a diff: every line added. */
fun diffFromWrite(path: String, content: String): FileDiff {
    val kept = content.lines().take(MAX_DIFF_LINES)
    val truncated = content.lines().size > MAX_DIFF_LINES
    var newNo = 1
    return FileDiff(
        path = path,
        lines = kept.map { DiffLine(null, newNo++, it, DiffKind.ADD) },
        added = kept.size,
        deleted = 0,
        truncated = truncated,
    )
}
