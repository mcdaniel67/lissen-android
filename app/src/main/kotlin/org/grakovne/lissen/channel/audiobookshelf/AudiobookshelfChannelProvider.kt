package org.grakovne.lissen.channel.audiobookshelf

import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.ChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfChannelProvider
  @Inject
  constructor(
    private val libraryAudiobookshelfChannel: LibraryAudiobookshelfChannel,
    private val audiobookshelfAuthService: AudiobookshelfAuthService,
  ) : ChannelProvider {
    // Audiobooks-only fork: every library is served by the library channel.
    override fun provideMediaChannel(): MediaChannel = libraryAudiobookshelfChannel

    override fun provideChannelAuth(): ChannelAuthService = audiobookshelfAuthService
  }
