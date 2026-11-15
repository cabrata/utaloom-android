package com.metrolist.music.ui.screens.wrapped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.metrolist.music.R
import java.text.NumberFormat

internal val WrappedInk = Color(0xFF121212)
internal val WrappedPaper = Color(0xFFF4F4F4)
internal val WrappedOrange = Color(0xFFFF4632)
internal val WrappedLime = Color(0xFFCDF564)
internal val WrappedPink = Color(0xFFF573A0)
internal val WrappedBlue = Color(0xFF2D46B9)
internal val WrappedYellow = Color(0xFFFFC862)

@Composable
internal fun WrappedSummaryCard(state: WrappedState, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val context = LocalContext.current
    val artwork = state.topSongs.firstOrNull()?.thumbnailUrl ?: state.topArtists.firstOrNull()?.thumbnailUrl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WrappedBlue)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(WrappedPink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.music_note), null, Modifier.size(48.dp), tint = WrappedInk)
                // Software bitmaps: the share flow copies this card into a GraphicsLayer.
                AsyncImage(
                    ImageRequest.Builder(context).data(artwork).allowHardware(false).build(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.wrapped_recap_brand),
                    color = WrappedLime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = stringResource(R.string.wrapped_recap_title, state.year),
                    modifier = Modifier.semantics { heading() },
                    color = WrappedPaper,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WrappedColumnList(
                title = stringResource(R.string.wrapped_recap_top_artists),
                items = state.topArtists.take(5).map { it.title },
                numbers = numbers,
                modifier = Modifier.weight(1f),
            )
            WrappedColumnList(
                title = stringResource(R.string.wrapped_recap_top_songs),
                items = state.topSongs.take(5).map { it.title },
                numbers = numbers,
                modifier = Modifier.weight(1f),
            )
        }

        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(R.string.wrapped_recap_minutes),
                color = WrappedPaper,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = numbers.format(state.totalMinutes),
                color = WrappedLime,
                fontFamily = FontFamily(Font(R.font.bbh_bartle_regular)),
                fontSize = 44.sp,
                lineHeight = 48.sp,
                maxLines = 1,
            )
        }

        Text(
            text = stringResource(R.string.wrapped_recap_source),
            color = WrappedPaper,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun WrappedColumnList(title: String, items: List<String>, numbers: NumberFormat, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = WrappedPaper,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        items.forEachIndexed { index, item ->
            Text(
                text = "${numbers.format(index + 1)}  $item",
                color = WrappedPaper,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
