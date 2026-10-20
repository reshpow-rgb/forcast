@file:OptIn(ExperimentalMaterial3Api::class)

package com.riseup.alarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riseup.alarm.data.Accent
import com.riseup.alarm.data.Backdrop
import com.riseup.alarm.data.SettingsRepository
import com.riseup.alarm.ui.theme.Muted

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val choice by settings.theme.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SettingsTitle("Accent colour")
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Accent.entries.forEach { accent ->
                    val color = Color(if (choice.backdrop.isLight) accent.onLight else accent.onDark)
                    val selected = accent == choice.accent
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                else Modifier,
                            )
                            .padding(4.dp)
                            .background(color, CircleShape)
                            .clickable { settings.setAccent(accent) }
                            .semantics { contentDescription = accent.title },
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = if (choice.backdrop.isLight) Color.White else Color(0xFF0E0E10),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Text(choice.accent.title, color = Muted, modifier = Modifier.padding(top = 8.dp))

            SettingsTitle("Background")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Backdrop.entries.forEach { backdrop ->
                    BackdropCard(
                        backdrop = backdrop,
                        accent = Color(if (backdrop.isLight) choice.accent.onLight else choice.accent.onDark),
                        selected = backdrop == choice.backdrop,
                        onClick = { settings.setBackdrop(backdrop) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
    )
}

/** A tiny mock-up of the alarm list in that background, so the choice is obvious at a glance. */
@Composable
private fun BackdropCard(backdrop: Backdrop, accent: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Surface(
            onClick = onClick,
            color = Color(backdrop.background),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth().height(110.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .height(8.dp)
                                .weight(1f)
                                .background(Color(backdrop.content).copy(alpha = 0.8f), RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(width = 18.dp, height = 10.dp)
                                .background(
                                    if (i == 0) accent else Color(backdrop.outline),
                                    RoundedCornerShape(5.dp),
                                ),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.End).size(16.dp).background(accent, CircleShape))
            }
        }
        Text(backdrop.title, modifier = Modifier.padding(top = 8.dp))
    }
}
