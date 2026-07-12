package org.grakovne.lissen.ui.screens.player.composable.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.R

@Composable
fun BookCoverPlaceholder(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .shimmer()
        .background(Color.Gray),
  )
}

@Composable
fun ChapterNumberPlaceholder(modifier: Modifier = Modifier) {
  Text(
    text = stringResource(R.string.player_screen_now_playing_title_chapter_of, 100, "1000"),
    style = typography.bodyMedium,
    color = Color.Transparent,
    textAlign = TextAlign.Center,
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .shimmer()
        .background(Color.Gray),
  )
}
