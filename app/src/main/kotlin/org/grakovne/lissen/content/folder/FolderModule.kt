package org.grakovne.lissen.content.folder

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FolderModule {
  @Binds
  @Singleton
  abstract fun bindFolderRepository(impl: LocalFolderRepository): FolderRepository
}
