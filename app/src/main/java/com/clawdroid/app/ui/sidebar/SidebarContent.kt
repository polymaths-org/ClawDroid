package com.clawdroid.app.ui.sidebar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add

import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SidebarContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("Quick Actions")
        DrawerAction("Automations", Icons.Rounded.Timer)
        DrawerAction("Connected Services", Icons.Rounded.History)
        DrawerAction("Settings", Icons.Rounded.Settings)

        SectionDivider()
        SectionHeader("Chats", actionContentDescription = "New chat")
        DrawerTextItem("💬 Quick task")
        DrawerTextItem("💬 Model smoke notes")
        DrawerTextItem("💬 Runtime setup")
        DrawerTextItem("Show more…", selected = false)

        SectionDivider()
        SectionHeader("Projects", actionContentDescription = "New project")
        DrawerTextItem("📁 Default Project", selected = true)
        DrawerTextItem("   + New Thread")
        DrawerTextItem("   💬 Current agent")
        DrawerTextItem("📁 Video Tools")
        DrawerTextItem("📁 Research")
        DrawerTextItem("Show more…", selected = false)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionContentDescription: String? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionContentDescription != null) {
            IconButton(onClick = { }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = actionContentDescription)
            }
        }
    }
}

@Composable
private fun DrawerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = { },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun DrawerTextItem(label: String, selected: Boolean = false) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = { },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        thickness = DividerDefaults.Thickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
