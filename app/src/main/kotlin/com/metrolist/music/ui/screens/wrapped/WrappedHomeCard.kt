package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

@Composable
internal fun WrappedHomeCard(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = stringResource(R.string.wrapped_recap_view)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WrappedCoral)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$action, $year"
            },
    ) {
        // Same shape language as the stories, so the banner reads as their cover.
        Canvas(Modifier.matchParentSize()) {
            val unit = size.height
            drawCircle(WrappedPeach, unit * 0.62f, Offset(size.width - unit * 0.18f, unit * 0.08f))
            drawCircle(WrappedPeach, unit * 0.2f, Offset(size.width * 0.58f, size.height * 1.02f), style = Stroke(unit * 0.07f))
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.wrapped_recap_title, year),
                    color = WrappedInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    year.toString(),
                    color = WrappedInk,
                    fontFamily = WrappedDisplayFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                )
                Text(
                    stringResource(R.string.wrapped_recap_open),
                    color = WrappedInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.wrapped_recap_home_subtitle),
                    color = WrappedInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(WrappedInk),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.arrow_forward), null, tint = WrappedCoral, modifier = Modifier.size(24.dp))
            }
        }
    }
}
