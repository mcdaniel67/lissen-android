package org.grakovne.lissen.ui.screens.library

import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.ui.components.withScrollbar
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.common.RequestLocalNetworkPermission
import org.grakovne.lissen.ui.screens.common.RequestNotificationPermissions
import org.grakovne.lissen.ui.screens.library.composables.AuthorComposable
import org.grakovne.lissen.ui.screens.library.composables.BookComposable
import org.grakovne.lissen.ui.screens.library.composables.DefaultActionComposable
import org.grakovne.lissen.ui.screens.library.composables.FolderComposable
import org.grakovne.lissen.ui.screens.library.composables.LibrarySearchActionComposable
import org.grakovne.lissen.ui.screens.library.composables.LibrarySwitchComposable
import org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable
import org.grakovne.lissen.ui.screens.library.composables.QuickSettingsComposable
import org.grakovne.lissen.ui.screens.library.composables.RecentBooksComposable
import org.grakovne.lissen.ui.screens.library.composables.SeriesComposable
import org.grakovne.lissen.ui.screens.library.composables.fallback.LibraryFallbackComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.LibraryPlaceholderComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.RecentBooksPlaceholderComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LibraryScreen(
  navController: AppNavigationService,
  libraryViewModel: LibraryViewModel = hiltViewModel(),
  playerViewModel: PlayerViewModel = hiltViewModel(),
  settingsViewModel: SettingsViewModel = hiltViewModel(),
  cachingModelView: CachingModelView = hiltViewModel(),
  imageLoader: ImageLoader,
  networkService: NetworkService,
  linkedSearchToken: String? = null,
) {
  val view: View = LocalView.current
  val coroutineScope = rememberCoroutineScope()

  val activity = LocalActivity.current
  val recentBooks: List<RecentBook> by libraryViewModel.recentBooks.collectAsState()

  var currentLibraryId by rememberSaveable { mutableStateOf("") }
  var localCacheUpdatedAt by rememberSaveable { mutableStateOf(0L) }
  var currentOrdering by rememberSaveable(stateSaver = LibraryOrderingConfiguration.saver) {
    mutableStateOf(LibraryOrderingConfiguration.default)
  }
  var pullRefreshing by remember { mutableStateOf(false) }
  val recentBookRefreshing by libraryViewModel.recentBookUpdating.collectAsState()
  val searchRequested by libraryViewModel.searchRequested.collectAsState()
  val searchToken by libraryViewModel.searchToken.collectAsState()
  val preparingError by playerViewModel.preparingError.collectAsState()

  val preferredLibrary by settingsViewModel.preferredLibrary.collectAsState()
  val libraries by settingsViewModel.libraries.collectAsState()

  var preferredLibraryExpanded by remember { mutableStateOf(false) }
  var preferencesExpanded by remember { mutableStateOf(false) }

  val library = libraryViewModel.getPager(searchRequested).collectAsLazyPagingItems()
  val libraryCount by libraryViewModel.totalCount.collectAsState()
  val selectionActive by libraryViewModel.selectionActive.collectAsState()
  val selectedBookIds by libraryViewModel.selectedBookIds.collectAsState()
  val downloadedIds by cachingModelView.cachedBookIds.collectAsState()
  val runningDownloads by cachingModelView.runningDownloads.collectAsState()
  val resolveDownloadState = { bookId: String -> cachingModelView.downloadStateOf(bookId, downloadedIds, runningDownloads) }
  val folders by libraryViewModel.folders.collectAsState()
  var showCreateFolder by remember { mutableStateOf(false) }
  var showAddToFolder by remember { mutableStateOf(false) }
  var folderPendingDelete by remember { mutableStateOf<LibraryEntry.FolderEntry?>(null) }
  val expandedGroups by libraryViewModel.expandedGroups.collectAsState()
  val groupBooks by libraryViewModel.groupBooks.collectAsState()
  val groupLoading by libraryViewModel.groupLoading.collectAsState()
  val libraryGrouping by settingsViewModel.libraryGrouping.collectAsState(LibraryGrouping.NONE)

  val libraryListState = rememberLazyGridState()

  BackHandler {
    when {
      selectionActive -> libraryViewModel.clearSelection()
      searchRequested && linkedSearchToken != null -> navController.goBack()
      searchRequested -> libraryViewModel.dismissSearch()
      else -> activity?.moveTaskToBack(true)
    }
  }

  fun refreshContent(showPullRefreshing: Boolean) {
    coroutineScope.launch {
      if (settingsViewModel.hasCredentials().not()) {
        navController.showLogin()
        return@launch
      }

      if (showPullRefreshing) {
        pullRefreshing = true
      }

      val minimumTime =
        when (showPullRefreshing) {
          true -> 500L
          false -> 0L
        }

      withMinimumTime(minimumTime) {
        listOf(
          async { settingsViewModel.fetchLibraries() },
          async { libraryViewModel.refreshLibrary() },
          async { libraryViewModel.fetchRecentListening() },
        ).awaitAll()
      }

      pullRefreshing = false
    }
  }

  RequestNotificationPermissions()

  RequestLocalNetworkPermission(
    onGranted = { refreshContent(showPullRefreshing = false) },
  )

  val isPlaceholderRequired by remember(library) {
    derivedStateOf {
      if (searchRequested) {
        return@derivedStateOf false
      }

      pullRefreshing || recentBookRefreshing || library.loadState.refresh is LoadState.Loading
    }
  }

  LaunchedEffect(preparingError) {
    if (preparingError) {
      playerViewModel.clearPlayingBook()
    }
  }

  LaunchedEffect(Unit) {
    if (linkedSearchToken != null) {
      libraryViewModel.applyLinkedSearch(linkedSearchToken)
    }
  }

  val pullRefreshState =
    rememberPullRefreshState(
      refreshing = pullRefreshing,
      onRefresh = {
        withHaptic(view) { refreshContent(showPullRefreshing = true) }
      },
    )

  val titleTextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
  val titleHeightDp = with(LocalDensity.current) { titleTextStyle.lineHeight.toPx().toDp() }

  val playingBook by playerViewModel.book.collectAsState()
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    libraryViewModel.markFinishedFailures.collect { failures ->
      Toast
        .makeText(
          context,
          context.getString(R.string.library_selection_mark_finished_error, failures),
          Toast.LENGTH_LONG,
        ).show()
    }
  }

  fun isRecentVisible(): Boolean {
    val fetchAvailable = networkService.isNetworkAvailable() || cachingModelView.localCacheUsing()
    val hasContent = recentBooks.isEmpty().not()

    return searchRequested.not() && hasContent && fetchAvailable
  }

  LaunchedEffect(Unit) {
    val emptyContent = library.itemCount == 0
    val libraryChanged = currentLibraryId != settingsViewModel.fetchPreferredLibraryId()
    val orderingChanged = currentOrdering != settingsViewModel.fetchLibraryOrdering()

    val localCacheUsing = cachingModelView.localCacheUsing()
    val localCacheUpdated = cachingModelView.fetchLatestUpdate(currentLibraryId)?.let { it > localCacheUpdatedAt } ?: true

    if (emptyContent || libraryChanged || orderingChanged || (localCacheUsing && localCacheUpdated)) {
      libraryViewModel.refreshRecentListening()
      libraryViewModel.refreshLibrary()

      currentLibraryId = settingsViewModel.fetchPreferredLibraryId()
      currentOrdering = settingsViewModel.fetchLibraryOrdering()
      localCacheUpdatedAt = cachingModelView.fetchLatestUpdate(currentLibraryId) ?: 0L
    }

    playerViewModel.updatePlayingItem()
    settingsViewModel.fetchLibraries()

    if (settingsViewModel.hasCredentials().not()) {
      navController.showLogin()
    }
  }

  fun provideLibraryTitle(): String {
    val type = libraryViewModel.fetchPreferredLibraryType()

    return when (type) {
      LibraryType.LIBRARY -> {
        libraryViewModel
          .fetchPreferredLibraryTitle()
          ?: context.getString(R.string.library_screen_library_title)
      }

      else -> {
        ""
      }
    }
  }

  val libraryTitle = remember(preferredLibrary) { provideLibraryTitle() }
  val recentVisible by remember { derivedStateOf { isRecentVisible() } }

  val navBarTitle by remember(libraryTitle) {
    derivedStateOf {
      val recentBlockVisible =
        libraryListState.layoutInfo.visibleItemsInfo
          .firstOrNull()
          ?.key == "recent_books"

      when {
        isPlaceholderRequired -> context.getString(R.string.library_screen_continue_listening_title)
        recentVisible && recentBlockVisible -> context.getString(R.string.library_screen_continue_listening_title)
        else -> libraryTitle
      }
    }
  }

  Scaffold(
    topBar = {
      if (selectionActive) {
        SelectionTopBar(
          count = selectedBookIds.size,
          onClose = { libraryViewModel.clearSelection() },
          onDownload = {
            cachingModelView.cacheByIds(selectedBookIds)
            libraryViewModel.clearSelection()
          },
          onMarkFinished = { libraryViewModel.markSelectionFinished() },
          onAddToFolder = { showAddToFolder = true },
          onCreateFolder = { showCreateFolder = true },
        )
      } else {
        TopAppBar(
          actions = {
            AnimatedContent(
              targetState = searchRequested,
              label = "library_action_animation",
              transitionSpec = {
                fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                  fadeOut(animationSpec = keyframes { durationMillis = 150 })
              },
            ) { isSearchRequested ->
              when (isSearchRequested) {
                true -> {
                  LibrarySearchActionComposable(
                    currentSearchToken = searchToken,
                    autoFocus = linkedSearchToken == null,
                    onSearchDismissed = {
                      when (linkedSearchToken) {
                        null -> libraryViewModel.dismissSearch()
                        else -> navController.goBack()
                      }
                    },
                    onSearchRequested = { libraryViewModel.updateSearch(it) },
                  )
                }

                false -> {
                  DefaultActionComposable(
                    onSearchRequested = { libraryViewModel.requestSearch() },
                    onPreferencesRequested = { preferencesExpanded = true },
                  )
                }
              }
            }
          },
          title = {
            if (!searchRequested) {
              Row(
                modifier =
                  when (navBarTitle) {
                    libraryTitle -> {
                      Modifier
                        .clickable(
                          interactionSource = remember { MutableInteractionSource() },
                          indication = null,
                        ) { preferredLibraryExpanded = true }
                        .fillMaxWidth()
                    }

                    else -> {
                      Modifier.fillMaxWidth()
                    }
                  },
              ) {
                Text(
                  text = navBarTitle,
                  style = titleTextStyle,
                  maxLines = 1,
                  modifier =
                    Modifier
                      .testTag("libraryNavBarTitle")
                      .semantics { heading() },
                )

                if (navBarTitle == libraryTitle) {
                  LibrarySwitchComposable { preferredLibraryExpanded = true }
                }
              }
            }
          },
          modifier = Modifier.systemBarsPadding(),
        )
      }
    },
    bottomBar = {
      playingBook?.let {
        Surface(shadowElevation = 4.dp) {
          MiniPlayerComposable(
            navController = navController,
            book = it,
            imageLoader = imageLoader,
            playerViewModel = playerViewModel,
            libraryType = preferredLibrary?.type,
          )
        }
      }
    },
    modifier =
      Modifier
        .testTag("libraryScreen")
        .systemBarsPadding()
        .fillMaxSize(),
    content = { innerPadding ->
      Box(
        modifier =
          Modifier
            .padding(innerPadding)
            .pullRefresh(pullRefreshState)
            .fillMaxSize(),
      ) {
        val showScrollbar by remember {
          derivedStateOf {
            val scrolledDown = libraryListState.firstVisibleItemIndex > 0 || libraryListState.firstVisibleItemScrollOffset > 0
            libraryListState.isScrollInProgress && scrolledDown
          }
        }

        val scrollbarAlpha by animateFloatAsState(
          targetValue = if (showScrollbar) 1f else 0f,
          animationSpec = tween(durationMillis = 300),
        )

        LazyVerticalGrid(
          columns = GridCells.Fixed(1),
          state = libraryListState,
          modifier =
            Modifier
              .testTag("libraryGrid")
              .fillMaxSize()
              .imePadding()
              .withScrollbar(
                state = libraryListState,
                color = colorScheme.onBackground.copy(alpha = scrollbarAlpha),
                totalItems = libraryCount,
                ignoreItems = listOf("recent_books", "library_title"),
              ),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item(key = "recent_books", span = { GridItemSpan(maxLineSpan) }) {
            val showRecent = recentVisible

            Column(modifier = Modifier.fillMaxWidth()) {
              when {
                isPlaceholderRequired -> {
                  RecentBooksPlaceholderComposable(
                    libraryViewModel = libraryViewModel,
                  )

                  Spacer(modifier = Modifier.height(RECENT_SECTION_SPACING))
                }

                showRecent -> {
                  RecentBooksComposable(
                    navController = navController,
                    recentBooks = recentBooks,
                    imageLoader = imageLoader,
                    libraryViewModel = libraryViewModel,
                    resolveDownloadState = resolveDownloadState,
                  )

                  Spacer(modifier = Modifier.height(RECENT_SECTION_SPACING))
                }
              }
            }
          }

          item(key = "library_title", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
              if (!searchRequested && recentVisible && isPlaceholderRequired.not()) {
                AnimatedContent(
                  targetState = navBarTitle,
                  transitionSpec = {
                    fadeIn(
                      animationSpec =
                        tween(300),
                    ) togetherWith
                      fadeOut(
                        animationSpec =
                          tween(
                            300,
                          ),
                      )
                  },
                  label = "library_header_fade",
                ) {
                  when {
                    it == libraryTitle -> {
                      Spacer(
                        modifier =
                          Modifier
                            .fillMaxWidth()
                            .height(titleHeightDp),
                      )
                    }

                    else -> {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                          Modifier
                            .clickable(
                              interactionSource = remember { MutableInteractionSource() },
                              indication = null,
                            ) { preferredLibraryExpanded = true }
                            .fillMaxWidth(),
                      ) {
                        Text(
                          style = titleTextStyle,
                          text = libraryTitle,
                        )

                        LibrarySwitchComposable { preferredLibraryExpanded = true }
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
            }
          }

          if (!searchRequested && !isPlaceholderRequired && folders.isNotEmpty()) {
            items(count = folders.size, key = { "folder_${folders[it].id}" }) { index ->
              val folder = folders[index]
              FolderComposable(
                folder = folder,
                expanded = folder.id in expandedGroups,
                loading = folder.id in groupLoading,
                books = groupBooks[folder.id].orEmpty(),
                imageLoader = imageLoader,
                navController = navController,
                onToggle = { libraryViewModel.toggleGroup(folder) },
                onLongClick = { folderPendingDelete = folder },
                resolveDownloadState = resolveDownloadState,
              )
            }
          }

          when {
            isPlaceholderRequired -> {
              item(span = { GridItemSpan(maxLineSpan) }) { LibraryPlaceholderComposable() }
            }

            library.itemCount == 0 -> {
              item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFallbackComposable(
                  searchRequested = searchRequested,
                  contentCachingModelView = cachingModelView,
                  networkService = networkService,
                  libraryViewModel = libraryViewModel,
                )
              }
            }

            else -> {
              items(count = library.itemCount, key = { "library_item_$it" }) {
                when (val entry = library[it] ?: return@items) {
                  is LibraryEntry.BookEntry -> {
                    BookComposable(
                      book = entry.book,
                      imageLoader = imageLoader,
                      navController = navController,
                      grouping = libraryGrouping,
                      downloadState = resolveDownloadState(entry.book.id),
                      selectionMode = selectionActive,
                      selected = entry.book.id in selectedBookIds,
                      onSelectToggle = { libraryViewModel.toggleSelection(entry.book) },
                      onLongClick = { libraryViewModel.toggleSelection(entry.book) },
                    )
                  }

                  is LibraryEntry.SeriesEntry -> {
                    SeriesComposable(
                      series = entry,
                      expanded = entry.id in expandedGroups,
                      loading = entry.id in groupLoading,
                      books = groupBooks[entry.id].orEmpty(),
                      imageLoader = imageLoader,
                      navController = navController,
                      onToggle = { libraryViewModel.toggleGroup(entry) },
                      onPrefetch = { libraryViewModel.prefetchGroup(entry) },
                      resolveDownloadState = resolveDownloadState,
                    )
                  }

                  is LibraryEntry.AuthorEntry -> {
                    AuthorComposable(
                      author = entry,
                      expanded = entry.id in expandedGroups,
                      loading = entry.id in groupLoading,
                      books = groupBooks[entry.id].orEmpty(),
                      imageLoader = imageLoader,
                      navController = navController,
                      onToggle = { libraryViewModel.toggleGroup(entry) },
                      onPrefetch = { libraryViewModel.prefetchGroup(entry) },
                      resolveDownloadState = resolveDownloadState,
                    )
                  }

                  is LibraryEntry.FolderEntry -> {
                    Unit
                  }
                }
              }
            }
          }
        }

        if (!searchRequested) {
          PullRefreshIndicator(
            refreshing = pullRefreshing,
            state = pullRefreshState,
            contentColor = colorScheme.primary,
            backgroundColor = colorScheme.surfaceContainer,
            modifier = Modifier.align(Alignment.TopCenter),
          )
        }
      }
    },
  )

  if (preferredLibraryExpanded) {
    PreferredLibrarySettingComposable(
      libraries = libraries,
      preferredLibrary = preferredLibrary,
      onDismissRequest = { preferredLibraryExpanded = false },
      onItemSelected = {
        settingsViewModel.preferLibrary(it)
        currentLibraryId = settingsViewModel.fetchPreferredLibraryId()
        refreshContent(false)

        playerViewModel.updatePlayingItem()
        preferredLibraryExpanded = false
      },
    )
  }

  if (preferencesExpanded) {
    QuickSettingsComposable(
      navController = navController,
      onDismissRequest = { preferencesExpanded = false },
      onForceLocalToggled = {
        cachingModelView.toggleCacheForce()
        playerViewModel.book.value?.let { playerViewModel.preparePlayback(it.id) }
        libraryViewModel.resetGroupExpansion()
        refreshContent(showPullRefreshing = false)
        coroutineScope.launch { libraryListState.scrollToItem(0) }
      },
      onHideCompletedToggled = {
        settingsViewModel.toggleHideCompleted()
        playerViewModel.book.value?.let { playerViewModel.preparePlayback(it.id) }
        libraryViewModel.resetGroupExpansion()
        refreshContent(showPullRefreshing = false)
        coroutineScope.launch { libraryListState.scrollToItem(0) }
      },
      onGroupingSelected = { grouping ->
        settingsViewModel.preferLibraryGrouping(grouping)
        libraryViewModel.resetGroupExpansion()
        refreshContent(showPullRefreshing = false)
        coroutineScope.launch { libraryListState.scrollToItem(0) }
      },
      onSortingChanged = {
        refreshContent(showPullRefreshing = false)
        coroutineScope.launch { libraryListState.scrollToItem(0) }
      },
    )
  }

  if (showCreateFolder) {
    CreateFolderDialog(
      count = selectedBookIds.size,
      onDismiss = { showCreateFolder = false },
      onConfirm = { name ->
        libraryViewModel.createFolder(name)
        showCreateFolder = false
      },
    )
  }

  if (showAddToFolder) {
    AddToFolderDialog(
      folders = folders,
      onDismiss = { showAddToFolder = false },
      onFolderSelected = { folderId ->
        libraryViewModel.addSelectionToFolder(folderId)
        showAddToFolder = false
      },
      onCreateNew = {
        showAddToFolder = false
        showCreateFolder = true
      },
    )
  }

  folderPendingDelete?.let { folder ->
    AlertDialog(
      onDismissRequest = { folderPendingDelete = null },
      title = { Text(stringResource(R.string.library_folder_delete_title)) },
      text = { Text(stringResource(R.string.library_folder_delete_message, folder.name)) },
      confirmButton = {
        TextButton(onClick = {
          libraryViewModel.deleteFolder(folder.id)
          folderPendingDelete = null
        }) {
          Text(stringResource(R.string.library_folder_delete_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { folderPendingDelete = null }) {
          Text(stringResource(R.string.library_folder_dialog_cancel))
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
  count: Int,
  onClose: () -> Unit,
  onDownload: () -> Unit,
  onMarkFinished: () -> Unit,
  onAddToFolder: () -> Unit,
  onCreateFolder: () -> Unit,
) {
  TopAppBar(
    navigationIcon = {
      IconButton(onClick = onClose) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(R.string.library_selection_clear),
        )
      }
    },
    title = {
      Text(
        text = stringResource(R.string.library_selection_count, count),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
      )
    },
    actions = {
      IconButton(
        onClick = onDownload,
        enabled = count > 0,
        modifier = Modifier.testTag("selectionDownload"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Download,
          contentDescription = stringResource(R.string.library_selection_download),
        )
      }
      IconButton(
        onClick = onMarkFinished,
        enabled = count > 0,
        modifier = Modifier.testTag("selectionMarkFinished"),
      ) {
        Icon(
          imageVector = Icons.Outlined.CheckCircle,
          contentDescription = stringResource(R.string.library_selection_mark_finished),
        )
      }
      IconButton(
        onClick = onAddToFolder,
        enabled = count > 0,
        modifier = Modifier.testTag("selectionAddToFolder"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Folder,
          contentDescription = stringResource(R.string.library_selection_add_to_folder),
        )
      }
      IconButton(
        onClick = onCreateFolder,
        enabled = count > 0,
        modifier = Modifier.testTag("selectionCreateFolder"),
      ) {
        Icon(
          imageVector = Icons.Outlined.CreateNewFolder,
          contentDescription = stringResource(R.string.library_selection_create_folder),
        )
      }
    },
    modifier = Modifier.systemBarsPadding(),
  )
}

@Composable
private fun CreateFolderDialog(
  count: Int,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_folder_create_title)) },
    text = {
      Column {
        Text(
          text = stringResource(R.string.library_folder_create_message, count),
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text(stringResource(R.string.library_folder_create_hint)) },
          modifier = Modifier.fillMaxWidth().testTag("createFolderNameField"),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(name) },
        enabled = name.isNotBlank(),
      ) {
        Text(stringResource(R.string.library_folder_create_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.library_folder_dialog_cancel))
      }
    },
  )
}

@Composable
private fun AddToFolderDialog(
  folders: List<LibraryEntry.FolderEntry>,
  onDismiss: () -> Unit,
  onFolderSelected: (String) -> Unit,
  onCreateNew: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_folder_add_title)) },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { onCreateNew() }
              .padding(vertical = 12.dp)
              .testTag("addToFolderNew"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.CreateNewFolder,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp),
          )
          Text(
            text = stringResource(R.string.library_folder_add_new),
            style = MaterialTheme.typography.bodyLarge,
          )
        }

        if (folders.isEmpty()) {
          Text(
            text = stringResource(R.string.library_folder_add_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
          )
        } else {
          folders.forEach { folder ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onFolderSelected(folder.id) }
                  .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
              )
              Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.library_folder_dialog_cancel))
      }
    },
  )
}

private val RECENT_SECTION_SPACING = 2.dp
