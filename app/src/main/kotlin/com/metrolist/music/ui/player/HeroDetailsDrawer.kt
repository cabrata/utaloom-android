/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.glass.backdrop.Backdrop
import com.metrolist.music.ui.utils.MediaInfoRow
import com.metrolist.music.ui.utils.copyMediaInfo
import com.metrolist.music.ui.utils.rememberMediaInfoSections

private val DetailsRowShape = RoundedCornerShape(16.dp)

/** The song's details ("Rincian" in the "…" menu) as a glass drawer; tapping a row copies its value. */
@Composable
internal fun HeroDetailsDrawer(
    videoId: String?,
    visible: Boolean,
    thumbnailUrl: String?,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
) {
    HeroDrawer(
        visible = visible,
        title = stringResource(R.string.details),
        thumbnailUrl = thumbnailUrl,
        backdrop = backdrop,
        onDismiss = onDismiss,
    ) {
        // Composed only while open, so the details are not fetched for a song nobody asked about.
        if (visible && !videoId.isNullOrBlank()) DetailsBody(videoId)
    }
}

@Composable
private fun DetailsBody(videoId: String) {
    val context = LocalContext.current
    val sections = rememberMediaInfoSections(videoId)
    val unknown = stringResource(R.string.unknown)
    if (!sections.loaded) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        DetailsGroup(stringResource(R.string.general), sections.general, unknown) { copyMediaInfo(context, it) }
        DetailsGroup(stringResource(R.string.information), sections.information, unknown) { copyMediaInfo(context, it) }
        val description = sections.description ?: unknown
        DetailsLabel(stringResource(R.string.description))
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(DetailsRowShape)
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(role = Role.Button) { copyMediaInfo(context, description) }
                .padding(14.dp),
        )
    }
}

@Composable
private fun DetailsLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun DetailsGroup(title: String, rows: List<MediaInfoRow>, unknown: String, onCopy: (String) -> Unit) {
    if (rows.isEmpty()) return
    DetailsLabel(title)
    rows.forEach { row ->
        val value = row.value ?: unknown
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(DetailsRowShape)
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(role = Role.Button) { onCopy(value) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
            ) {
                Icon(painterResource(row.icon), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(row.label, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 19.sp)
            }
        }
    }
}
