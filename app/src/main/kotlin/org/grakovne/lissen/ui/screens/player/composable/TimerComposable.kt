package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.LibraryType.LIBRARY
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.ui.components.LissenModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerComposable(
  currentOption: TimerOption?,
  libraryType: LibraryType,
  onOptionSelected: (TimerOption?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view = LocalView.current

  val customDuration = (currentOption as? DurationTimerOption)?.duration ?: DEFAULT_CUSTOM_DURATION

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.timer_title),
          style = typography.bodyLarge,
        )

        FlowRow(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OptionPresets.forEach { value ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  onOptionSelected(value)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (currentOption.isSame(value)) {
                      colorScheme.primary
                    } else {
                      colorScheme.surfaceContainer
                    },
                  contentColor =
                    if (currentOption.isSame(value)) {
                      colorScheme.onPrimary
                    } else {
                      colorScheme.onSurfaceVariant
                    },
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              when (value) {
                null -> {
                  val fontSize = typography.labelMedium.fontSize
                  val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.timer_option_disabled),
                    modifier = Modifier.size(iconSize),
                  )
                }

                CurrentEpisodeTimerOption -> {
                  val fontSize = typography.labelMedium.fontSize
                  val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                  Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription =
                      when (libraryType) {
                        LIBRARY -> stringResource(R.string.timer_option_after_current_chapter)
                        else -> stringResource(R.string.timer_option_after_current_episode)
                      },
                    modifier = Modifier.size(iconSize),
                  )
                }

                is DurationTimerOption -> {
                  Text(
                    text = value.duration.toString(),
                    style =
                      if (currentOption.isSame(value)) {
                        typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                      } else {
                        typography.labelMedium
                      },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = stringResource(R.string.timer_option_custom_label),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
          )

          IconButton(
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary),
            enabled = customDuration > CUSTOM_MIN_DURATION,
            onClick = {
              withHaptic(view) {
                val next = (customDuration - CUSTOM_STEP).coerceAtLeast(CUSTOM_MIN_DURATION)
                onOptionSelected(DurationTimerOption(next))
              }
            },
          ) {
            Icon(
              imageVector = Icons.Rounded.Remove,
              contentDescription = stringResource(R.string.timer_option_decrease),
              modifier = Modifier.size(18.dp),
            )
          }

          Text(
            text = stringResource(R.string.timer_option_minutes_short, customDuration),
            style = typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
          )

          IconButton(
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary),
            enabled = customDuration < CUSTOM_MAX_DURATION,
            onClick = {
              withHaptic(view) {
                val next = (customDuration + CUSTOM_STEP).coerceAtMost(CUSTOM_MAX_DURATION)
                onOptionSelected(DurationTimerOption(next))
              }
            },
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = stringResource(R.string.timer_option_increase),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    },
  )
}

private fun TimerOption?.isSame(that: TimerOption?) =
  when (this) {
    CurrentEpisodeTimerOption -> that == CurrentEpisodeTimerOption
    is DurationTimerOption -> that is DurationTimerOption && that.duration == this.duration
    null -> that == null
  }

private val OptionPresets =
  listOf(
    null,
    DurationTimerOption(15),
    DurationTimerOption(30),
    DurationTimerOption(45),
    DurationTimerOption(60),
    CurrentEpisodeTimerOption,
  )

private const val DEFAULT_CUSTOM_DURATION = 15
private const val CUSTOM_MIN_DURATION = 5
private const val CUSTOM_MAX_DURATION = 120
private const val CUSTOM_STEP = 5
