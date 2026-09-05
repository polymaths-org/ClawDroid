package com.clawdroid.app.core.assistant.context

import android.app.assist.AssistStructure
import android.text.InputType

object AssistStructureExtractor {

    data class ExtractedContext(
        val sourcePackage: String?,
        val sourceActivity: String?,
        val visibleText: String,
        val contentDescriptionText: String,
        val focusedText: String?,
        val webUri: String?,
    )

    fun extract(structure: AssistStructure?): ExtractedContext {
        if (structure == null) {
            return ExtractedContext(null, null, "", "", null, null)
        }

        val visibleTextBuilder = StringBuilder()
        val contentDescBuilder = StringBuilder()
        var focusedText: String? = null
        var webUri: String? = null
        val activityComponent = structure.activityComponent

        val windowCount = structure.windowNodeCount
        for (i in 0 until windowCount) {
            val window = structure.getWindowNodeAt(i)
            val rootNode = window.rootViewNode
            walkNode(rootNode, visibleTextBuilder, contentDescBuilder, { text ->
                focusedText = text
            }, { uri ->
                webUri = uri
            })
        }

        return ExtractedContext(
            sourcePackage = activityComponent?.packageName,
            sourceActivity = activityComponent?.className,
            visibleText = visibleTextBuilder.toString().trim(),
            contentDescriptionText = contentDescBuilder.toString().trim(),
            focusedText = focusedText,
            webUri = webUri
        )
    }

    private fun walkNode(
        node: AssistStructure.ViewNode?,
        visibleBuilder: StringBuilder,
        contentDescBuilder: StringBuilder,
        onFocused: (String) -> Unit,
        onWebUri: (String) -> Unit,
    ) {
        if (node == null) return

        val isPassword = isPasswordNode(node)
        val text = if (isPassword) "[REDACTED PASSWORD]" else node.text?.toString()
        val contentDesc = node.contentDescription?.toString()

        if (!text.isNullOrBlank()) {
            visibleBuilder.append(text).append("\n")
            if (node.isFocused) {
                onFocused(text)
            }
        }

        if (!contentDesc.isNullOrBlank()) {
            contentDescBuilder.append(contentDesc).append("\n")
        }

        // Try to capture Web URI (commonly exposed as node text or resource identifier by webviews)
        val webScheme = node.webScheme
        val webDomain = node.webDomain
        if (!webScheme.isNullOrBlank() && !webDomain.isNullOrBlank()) {
            onWebUri("$webScheme://$webDomain")
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            walkNode(node.getChildAt(i), visibleBuilder, contentDescBuilder, onFocused, onWebUri)
        }
    }

    private fun isPasswordNode(node: AssistStructure.ViewNode): Boolean {
        // Safe runtime check for password indicators
        val inputType = node.inputType
        val isPasswordInput = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
                ((inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)

        val isNumericPassword = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER &&
                (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val classNameLower = node.className?.lowercase().orEmpty()
        val idLower = node.idEntry?.lowercase().orEmpty()
        val hintLower = node.hint?.lowercase().orEmpty()

        return isPasswordInput || isNumericPassword ||
                classNameLower.contains("password") || classNameLower.contains("pin") ||
                idLower.contains("password") || idLower.contains("pin") ||
                hintLower.contains("password") || hintLower.contains("pin")
    }
}
