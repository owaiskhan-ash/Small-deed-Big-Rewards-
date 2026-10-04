package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.HadithsData
import com.example.model.Chapter
import com.example.model.Hadith
import com.example.ui.components.DecorativeDiamond
import com.example.ui.components.IslamicHeaderDecoration
import com.example.ui.components.OrnamentalDivider
import com.example.ui.components.onSwipeGesture
import com.example.viewmodel.ActiveScreen
import com.example.viewmodel.FilterChip
import com.example.viewmodel.HadithViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed

fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounceClick"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

fun Modifier.scalePress(interactionSource: MutableInteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scalePress"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun formatHadithText(translation: String): String {
    val prefix = "The Messenger of Allah peace be upon him said:"
    val trimmed = translation.trim()
    if (trimmed.startsWith(prefix)) {
        return trimmed
    }
    
    // Check if the translation already contains detailed narrator/source introductions
    val lower = trimmed.lowercase()
    if (lower.startsWith("abu umama") ||
        lower.startsWith("anas") ||
        lower.startsWith("haiwah") ||
        lower.startsWith("narrated") ||
        lower.startsWith("it was narrated") ||
        lower.startsWith("part 1") ||
        lower.startsWith("jabir") ||
        lower.contains("i heard the messenger of allah") ||
        lower.contains("allah's messenger (ﷺ) say") ||
        trimmed.startsWith("Based on") ||
        trimmed.startsWith("According to")
    ) {
        return trimmed
    }
    
    if (trimmed.startsWith("•") || trimmed.startsWith("*")) {
        return "$prefix\n$trimmed"
    }
    return "$prefix \"$trimmed\""
}

fun getAccentColor(colorName: String, isDark: Boolean): Color {
    return when (colorName.lowercase()) {
        "emerald" -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        "gold" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        "indigo" -> if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        "rose" -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
        "teal" -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        "slate" -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
        else -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    }
}

// Chapter colour is read straight from HadithsData so the palette declared
// with the content is the palette that renders -- the previous copy-pasted
// when(chapterId) table could silently drift from the data.
fun getChapterColor(chapterId: Int): Color =
    HadithsData.chapters.firstOrNull { it.id == chapterId }
        ?.let { Color(it.colorArgb) }
        ?: Color(0xFF059669)

fun getChapterIcon(chapterId: Int): ImageVector =
    when (HadithsData.chapters.firstOrNull { it.id == chapterId }?.iconName) {
        "salah" -> Icons.Default.Star                 // Prayer
        "dhikr" -> Icons.Default.Favorite             // Dhikr
        "quran" -> Icons.AutoMirrored.Filled.List     // Quran
        "ethics" -> Icons.Default.Person              // Ethics
        "charity" -> Icons.Default.Info               // Fasting & Charity
        "family" -> Icons.Default.Home                // Daily & Family
        "knowledge" -> Icons.Default.Info             // Knowledge & Gatherings
        "final_journey" -> Icons.Default.Star         // Provisions of a believer
        else -> Icons.Default.Star
    }

@Composable
fun MainScreenContainer(viewModel: HadithViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val accentColorName by viewModel.accentColor.collectAsState()
    val isDark = isSystemInDarkThemeCustom(viewModel)

    val activeColor = getAccentColor(accentColorName, isDark)

    // Handle physical/system back gesture or button safely
    if (currentScreen != ActiveScreen.HOME) {
        androidx.activity.compose.BackHandler {
            viewModel.navigateBack()
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen in listOf(ActiveScreen.HOME, ActiveScreen.BROWSE, ActiveScreen.FAVORITES, ActiveScreen.SETTINGS)) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Small Deeds, Big Rewards",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )

                            if (currentScreen != ActiveScreen.BROWSE) {
                                IconButton(
                                    onClick = { viewModel.navigateTo(ActiveScreen.BROWSE) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        IslamicHeaderDecoration(
                            color = activeColor,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Only show bottom navigation on core tabs
            if (currentScreen in listOf(ActiveScreen.HOME, ActiveScreen.BROWSE, ActiveScreen.FAVORITES, ActiveScreen.SETTINGS)) {
                NavigationBar(
                    tonalElevation = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp) },
                        selected = currentScreen == ActiveScreen.HOME,
                        onClick = { viewModel.navigateTo(ActiveScreen.HOME) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            indicatorColor = activeColor.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Browse") },
                        label = { Text("Browse", fontSize = 11.sp) },
                        selected = currentScreen == ActiveScreen.BROWSE,
                        onClick = { viewModel.navigateTo(ActiveScreen.BROWSE) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            indicatorColor = activeColor.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        label = { Text("Favorites", fontSize = 11.sp) },
                        selected = currentScreen == ActiveScreen.FAVORITES,
                        onClick = { viewModel.navigateTo(ActiveScreen.FAVORITES) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            indicatorColor = activeColor.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp) },
                        selected = currentScreen == ActiveScreen.SETTINGS,
                        onClick = { viewModel.navigateTo(ActiveScreen.SETTINGS) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            indicatorColor = activeColor.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    ActiveScreen.HOME -> HomeScreen(viewModel, activeColor)
                    ActiveScreen.BROWSE -> BrowseScreen(viewModel, activeColor)
                    ActiveScreen.FAVORITES -> FavoritesScreen(viewModel, activeColor)
                    ActiveScreen.SETTINGS -> SettingsScreen(viewModel, activeColor)
                    ActiveScreen.HADITH_DETAIL -> HadithDetailScreen(viewModel, activeColor)
                    ActiveScreen.CHAPTER_VIEW -> ChapterViewScreen(viewModel, activeColor)
                }
            }
        }
    }
}

@Composable
fun isSystemInDarkThemeCustom(viewModel: HadithViewModel): Boolean {
    val themeState by viewModel.theme.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (themeState) {
        "dark" -> true
        "light" -> false
        "white" -> false
        else -> isSystemDark
    }
}

// -----------------------------------------------------------------
// 1. HOME SCREEN
// -----------------------------------------------------------------
@Composable
fun HomeScreen(viewModel: HadithViewModel, activeColor: Color) {
    val lastReadId by viewModel.lastReadId.collectAsState()
    val readHadiths by viewModel.readHadithIds.collectAsState()
    val dailyHadith = remember { viewModel.getDailyHadith() }
    val currentStreak by viewModel.currentStreak.collectAsState()

    val lastOpenedHadith = remember(lastReadId) {
        HadithsData.hadithById(lastReadId)
    }

    // Calculate dynamic progress
    val overallProgressText = "${readHadiths.size}/${HadithsData.totalHadiths}"
    val progressFloat = readHadiths.size / HadithsData.totalHadiths.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Beautiful Obsidian Theme-Breaking Primary Continue Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C1917),
                    contentColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = lastOpenedHadith.id) }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTINUE READING",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = activeColor,
                            letterSpacing = 1.5.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentStreak > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$currentStreak-day streak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA8A29E)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(activeColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Hadith ${lastOpenedHadith.id}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = lastOpenedHadith.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = lastOpenedHadith.chapterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA8A29E)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall Progress",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8A29E)
                        )
                        Text(
                            text = overallProgressText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = activeColor,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
            }
        }



        // Daily Hadith Widget (Compact visual layout)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkThemeCustom(viewModel)) MaterialTheme.colorScheme.surface else Color(0xFFFDFCFB)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = dailyHadith.id) }
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(24.dp)
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Insight",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = activeColor,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatHadithText(dailyHadith.translation),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontFamily = FontFamily.Serif
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(activeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Read Daily Insight",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Chapters Section Header
        item {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Chapters in list format instead of cards
        items(HadithsData.chapters, key = { it.id }) { chapter ->
            ChapterRow(chapter, readHadiths, activeColor) {
                viewModel.navigateTo(ActiveScreen.CHAPTER_VIEW, chapterId = chapter.id)
            }
        }
    }
}

@Composable
fun ChapterRow(chapter: Chapter, readHadiths: Set<Int>, globalAccent: Color, onClick: () -> Unit) {
    val countRead = chapter.rangeIds.count { readHadiths.contains(it) }
    val total = chapter.rangeIds.count()
    val progress = if (total > 0) countRead.toFloat() / total else 0f
    val displayColor = getChapterColor(chapter.id)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left Chapter Icon Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(displayColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getChapterIcon(chapter.id),
                        contentDescription = chapter.name,
                        tint = displayColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Middle Text Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHAPTER ${chapter.id}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = displayColor
                        )
                        Text(
                            text = "$countRead / $total read",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = chapter.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = displayColor,
                trackColor = displayColor.copy(alpha = 0.12f)
            )
        }
    }
}


// -----------------------------------------------------------------
// 2. BROWSE SCREEN
// -----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(viewModel: HadithViewModel, activeColor: Color) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterChip by viewModel.selectedFilterChip.collectAsState()
    val filterChapterId by viewModel.selectedFilterChapterId.collectAsState()
    val favoriteHadiths by viewModel.favoriteHadithIds.collectAsState()
    val readHadiths by viewModel.readHadithIds.collectAsState()

    var dropdownExpanded by remember { mutableStateOf(false) }

    // Filter logic
    val filteredHadiths = remember(searchQuery, filterChip, filterChapterId, favoriteHadiths, readHadiths) {
        HadithsData.hadiths.filter { hadith ->
            // Search Query
            val query = searchQuery.trim()
            val matchesSearch = query.isEmpty() ||
                    hadith.title.contains(query, ignoreCase = true) ||
                    hadith.translation.contains(query, ignoreCase = true) ||
                    hadith.narrator.contains(query, ignoreCase = true) ||
                    hadith.description.contains(query, ignoreCase = true) ||
                    hadith.reference.contains(query, ignoreCase = true) ||
                    hadith.chapterName.contains(query, ignoreCase = true) ||
                    hadith.id.toString().startsWith(query)

            // Filter Chip
            val matchesChip = when (filterChip) {
                FilterChip.ALL -> true
                FilterChip.UNREAD -> !readHadiths.contains(hadith.id)
                FilterChip.FAVORITES -> favoriteHadiths.contains(hadith.id)
            }

            // Chapter Filter
            val matchesChapter = if (filterChapterId == null) true else hadith.chapter == filterChapterId

            matchesSearch && matchesChip && matchesChapter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search bar
        Box(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search hadiths, narrators, translations...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = activeColor) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Inline filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip.entries.forEach { chip ->
                val selected = filterChip == chip
                SuggestionChip(
                    onClick = { viewModel.updateFilterChip(chip) },
                    label = {
                        Text(
                            text = when (chip) {
                                FilterChip.ALL -> "All"
                                FilterChip.UNREAD -> "Unread"
                                FilterChip.FAVORITES -> "Favorites"
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (selected) activeColor else Color.Transparent,
                        labelColor = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }

            // Space and Chapter dropdown
            Spacer(modifier = Modifier.weight(1f))

            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeColor.copy(alpha = 0.12f),
                        contentColor = activeColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = if (filterChapterId != null) "Ch $filterChapterId" else "All Chapters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown indicator",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Chapters") },
                        onClick = {
                            viewModel.updateFilterChapterId(null)
                            dropdownExpanded = false
                        }
                    )
                    HadithsData.chapters.forEach { chapter ->
                        DropdownMenuItem(
                            text = { Text("Chapter ${chapter.id}: ${chapter.name}") },
                            onClick = {
                                viewModel.updateFilterChapterId(chapter.id)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // List display
        if (filteredHadiths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty icon",
                        tint = activeColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No hadiths found matching guidelines.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredHadiths, key = { it.id }) { hadith ->
                    HadithRowItem(hadith, favoriteHadiths, readHadiths, activeColor) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id)
                    }
                }
            }
        }
    }
}

@Composable
fun HadithRowItem(
    hadith: Hadith,
    favorites: Set<Int>,
    readHadiths: Set<Int>,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isFav = favorites.contains(hadith.id)
    val isRead = readHadiths.contains(hadith.id)
    val chapColor = getChapterColor(hadith.chapter)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Big Hadith Number
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(chapColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hadith.id.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = chapColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text layout
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hadith.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                    if (isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Read status",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Chapter tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chapColor.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = hadith.chapterName,
                        fontSize = 10.sp,
                        color = chapColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatHadithText(hadith.translation),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Favorited heartbeat icon representation
            Icon(
                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite status",
                tint = if (isFav) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


// -----------------------------------------------------------------
// 3. HADITH DETAIL SCREEN
// -----------------------------------------------------------------
@Suppress("DEPRECATION")
@Composable
fun HadithDetailScreen(viewModel: HadithViewModel, activeColor: Color) {
    val activeId by viewModel.detailHadithId.collectAsState()
    val favorites by viewModel.favoriteHadithIds.collectAsState()
    val readStates by viewModel.readHadithIds.collectAsState()

    // Config options from viewmodel
    val fontSizeOpt by viewModel.fontSize.collectAsState()
    val arabicSizeOpt by viewModel.arabicSize.collectAsState()
    val fontStyleOpt by viewModel.fontStyle.collectAsState()
    val lineHeightOpt by viewModel.lineHeight.collectAsState()
    val showArabic by viewModel.showArabicEnabled.collectAsState()
    val showNarrator by viewModel.showNarratorEnabled.collectAsState()
    val isAutoRead by viewModel.autoReadEnabled.collectAsState()

    val context = LocalContext.current

    val hadith = remember(activeId) {
        HadithsData.hadithById(activeId)
    }

    val isFav = favorites.contains(hadith.id)
    val isRead = readStates.contains(hadith.id)

    // Tactile Favorite icon elastic scale animation
    val favScale by animateFloatAsState(
        targetValue = if (isFav) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "favoriteScalePulse"
    )

    // Automatically mark read if option enabled on display
    // Keyed on both the hadith and the setting so switching auto-read on while
    // a hadith is open marks it immediately, rather than only on next open.
    LaunchedEffect(hadith.id, isAutoRead) {
        if (isAutoRead) {
            viewModel.markAsRead(hadith.id)
        }
    }

    val hadithFontFamily = if (fontStyleOpt == "serif") FontFamily.Serif else FontFamily.SansSerif
    val translationLineHeight = when (lineHeightOpt) {
        "compact" -> 22.sp
        "normal" -> 28.sp
        "relaxed" -> 34.sp
        else -> 28.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSwipeGesture(
                onSwipeLeft = {
                    if (hadith.id < HadithsData.totalHadiths) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id + 1)
                    }
                },
                onSwipeRight = {
                    if (hadith.id > 1) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id - 1)
                    }
                }
            )
    ) {
        // App top control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${hadith.id} / ${HadithsData.totalHadiths}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleFavorite(hadith.id) }) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle favorite",
                    tint = if (isFav) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.scale(favScale)
                )
            }
        }

        // Reading Content body
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                // Chapter color tag pill
                val chapColor = getChapterColor(hadith.chapter)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chapColor.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DecorativeDiamond(color = chapColor, modifier = Modifier.size(8.dp))
                        Text(
                            text = hadith.chapterName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = chapColor,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hadith Title
                Text(
                    text = hadith.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Arabic section hidden toggles
                if (showArabic && hadith.arabicText.isNotEmpty()) {
                    Text(
                        text = hadith.arabicText,
                        fontSize = arabicSizeOpt.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = (arabicSizeOpt * 1.8f).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )

                    OrnamentalDivider(color = chapColor, modifier = Modifier.padding(bottom = 24.dp))
                }

                // Translation
                Text(
                    text = formatHadithText(hadith.translation),
                    fontSize = fontSizeOpt.sp,
                    fontFamily = hadithFontFamily,
                    lineHeight = translationLineHeight,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Optional Narrator
                if (showNarrator && hadith.narrator.isNotEmpty()) {
                    Text(
                        text = "Narrated by ${hadith.narrator}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reference
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Book icon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hadith.reference,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                val markReadInteractionSource = remember { MutableInteractionSource() }
                val shareInteractionSource = remember { MutableInteractionSource() }

                // Action buttons: Copy / Share, Mark Read
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mark read
                    Button(
                        onClick = { viewModel.toggleRead(hadith.id) },
                        interactionSource = markReadInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRead) Color(0xFF059669).copy(alpha = 0.15f) else activeColor,
                            contentColor = if (isRead) Color(0xFF059669) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .scalePress(markReadInteractionSource)
                            .weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = "Read mark"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRead) "Read" else "Mark as Read",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Share button copies to clipboard and opens system share dialog
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val formattedText = """
                            |✨ SMALL DEEDS, BIG REWARDS ✨
                            |Hadith #${hadith.id}: ${hadith.title}
                            |Chapter: ${hadith.chapterName}
                            |
                            |${if (hadith.arabicText.isNotEmpty()) "${hadith.arabicText}\n" else ""}
                            |${formatHadithText(hadith.translation)}
                            |
                            |— Narrated by ${hadith.narrator}
                            |📚 Source: ${hadith.reference}
                            """.trimMargin()
                            val clip = ClipData.newPlainText("hadith_share", formattedText)
                            clipboard.setPrimaryClip(clip)

                            viewModel.triggerHaptic()

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, formattedText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Hadith #${hadith.id}")
                            try {
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Hadith text copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        interactionSource = shareInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.scalePress(shareInteractionSource)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }

                // Feature 4: Interactive Dhikr Practice Counter for specific user-requested Hadiths
                if (hadith.id in listOf(20, 21, 26, 27, 29, 31, 37, 39, 41)) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DhikrPracticeCounter(hadithId = hadith.id, viewModel = viewModel, activeColor = activeColor)
                }
            }
        }

        // Bottom next/prev pager trigger buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    if (hadith.id > 1) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id - 1)
                    }
                },
                enabled = hadith.id > 1
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Previous", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = {
                    if (hadith.id < HadithsData.totalHadiths) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id + 1)
                    }
                },
                enabled = hadith.id < HadithsData.totalHadiths
            ) {
                Text("Next", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// -----------------------------------------------------------------
// FEATURE 4: INTERACTIVE DHIKR/TASBIH PRACTICE COUNTER
// -----------------------------------------------------------------
@Composable
fun DhikrPracticeCounter(
    hadithId: Int,
    viewModel: HadithViewModel,
    activeColor: Color
) {
    val totalCompletionsMap by viewModel.totalCompletionsMap.collectAsState()
    val totalCompletions = totalCompletionsMap[hadithId] ?: 0

    // Load initial counts from dynamic device cache SharedPreferences
    var currentCount by remember(hadithId) { 
        mutableStateOf(viewModel.getSessionCount(hadithId)) 
    }
    
    // Target counts for repeating practices (e.g. 3x, 10x, 33x, 100x)
    val targetOptions = listOf(3, 10, 33, 100)
    var selectedTargetIndex by remember(hadithId) { 
        mutableStateOf(viewModel.getSessionTargetIndex(hadithId)) 
    }
    val activeTarget = targetOptions.getOrElse(selectedTargetIndex) { targetOptions[0] }

    val progressRaw = if (currentCount >= activeTarget) 1.0f else currentCount.toFloat() / activeTarget
    val progress by animateFloatAsState(
        targetValue = progressRaw,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "subhaProgress animate"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkThemeCustom(viewModel)) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color(0xFFF9F7F5)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Practice Action Counter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Completed: $totalCompletions times",
                        fontSize = 11.sp,
                        color = activeColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentCount > 0) {
                    TextButton(
                        onClick = {
                            currentCount = 0
                            viewModel.saveSessionCount(hadithId, 0)
                            viewModel.triggerHaptic()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Icon", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selector row for goal targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deed Target:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                targetOptions.forEachIndexed { index, option ->
                    val isSelected = selectedTargetIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedTargetIndex = index
                                currentCount = 0
                                viewModel.saveSessionTargetIndex(hadithId, index)
                                viewModel.saveSessionCount(hadithId, 0)
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${option}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subha Circle Activator Button
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        if (currentCount >= activeTarget) activeColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .bounceClick {
                        if (currentCount < activeTarget) {
                            val nextVal = currentCount + 1
                            currentCount = nextVal
                            viewModel.saveSessionCount(hadithId, nextVal)
                            viewModel.triggerHaptic()
                            if (nextVal == activeTarget) {
                                viewModel.incrementPracticeCount(hadithId)
                            }
                        } else {
                            currentCount = 1
                            viewModel.saveSessionCount(hadithId, 1)
                            viewModel.triggerHaptic()
                        }
                    }
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(118.dp),
                    color = activeColor,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentCount >= activeTarget) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success tick",
                            tint = activeColor,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeColor
                        )
                    } else {
                        Text(
                            text = "$currentCount",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "/ $activeTarget",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (currentCount >= activeTarget) "Target reached! Click to repeat this session." else "Tap inside the circle to mark each recitation.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }
    }
}


// -----------------------------------------------------------------
// 4. CHAPTER VIEW SCREEN
// -----------------------------------------------------------------
@Composable
fun ChapterViewScreen(viewModel: HadithViewModel, activeColor: Color) {
    val chapterId by viewModel.selectedChapterId.collectAsState()
    val favorites by viewModel.favoriteHadithIds.collectAsState()
    val readHadiths by viewModel.readHadithIds.collectAsState()

    val chapter = remember(chapterId) {
        HadithsData.chapterById(chapterId)
    }

    val chapterColor = getChapterColor(chapter.id)

    val hadithsInChapter = remember(chapter) {
        HadithsData.hadiths.filter { it.chapter == chapter.id }
    }

    val countRead = chapter.rangeIds.count { readHadiths.contains(it) }
    val totalCount = chapter.rangeIds.count()
    val progress = if (totalCount > 0) countRead.toFloat() / totalCount else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Chapter ${chapter.id}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Card displaying chapter summary progress
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = chapterColor.copy(alpha = 0.08f)),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(chapterColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getChapterIcon(chapter.id),
                        contentDescription = chapter.name,
                        tint = chapterColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = chapter.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$countRead / $totalCount completed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = chapterColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = chapterColor,
                        trackColor = chapterColor.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Hadiths list in chapter
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hadithsInChapter, key = { it.id }) { hadith ->
                HadithRowItem(hadith, favorites, readHadiths, activeColor) {
                    viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id)
                }
            }
        }
    }
}


// -----------------------------------------------------------------
// 5. FAVORITES SCREEN
// -----------------------------------------------------------------
@Composable
fun FavoritesScreen(viewModel: HadithViewModel, activeColor: Color) {
    val favorites by viewModel.favoriteHadithIds.collectAsState()
    val readHadiths by viewModel.readHadithIds.collectAsState()

    val favHadiths = remember(favorites) {
        HadithsData.hadiths.filter { favorites.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBarLabelHeader(title = "Your Favorites", color = activeColor)

        if (favHadiths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Empty Favorites",
                        tint = activeColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No favorites yet.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the ♥ on any hadith to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favHadiths, key = { it.id }) { hadith ->
                    HadithRowItem(hadith, favorites, readHadiths, activeColor) {
                        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadith.id)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBarLabelHeader(title: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}


// -----------------------------------------------------------------
// 6. SETTINGS SCREEN
// -----------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: HadithViewModel, activeColor: Color) {
    val fontSizeOpt by viewModel.fontSize.collectAsState()
    val arabicSizeOpt by viewModel.arabicSize.collectAsState()
    val fontStyleOpt by viewModel.fontStyle.collectAsState()
    val lineHeightOpt by viewModel.lineHeight.collectAsState()

    val currentTheme by viewModel.theme.collectAsState()
    val accentColorName by viewModel.accentColor.collectAsState()

    val hapticOn by viewModel.hapticEnabled.collectAsState()
    val autoReadOn by viewModel.autoReadEnabled.collectAsState()
    val showArabicOn by viewModel.showArabicEnabled.collectAsState()
    val showNarratorOn by viewModel.showNarratorEnabled.collectAsState()
    val dailyNotifOn by viewModel.dailyNotificationsEnabled.collectAsState()

    val readHadiths by viewModel.readHadithIds.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var isProgressExpanded by remember { mutableStateOf(false) }

    val overallTotal = readHadiths.size
    val overallPercent =
        if (HadithsData.totalHadiths > 0) (overallTotal * 100) / HadithsData.totalHadiths else 0

    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.triggerTestNotification()
            Toast.makeText(context, "Notification permission granted! Sending notification...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied. Please allow notifications in device Settings.", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            SearchBarLabelHeader(title = "Settings", color = activeColor)
        }

        // Circular progress ring showing read count inside an elegant dropdown card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .border(1.dp, activeColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .clickable { isProgressExpanded = !isProgressExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "OVERALL PROGRESS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$overallPercent%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = activeColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Icon(
                                imageVector = if (isProgressExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isProgressExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (isProgressExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(110.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { overallTotal / HadithsData.totalHadiths.toFloat() },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 10.dp,
                                    color = activeColor,
                                    trackColor = activeColor.copy(alpha = 0.12f)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$overallPercent%",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "$overallTotal / ${HadithsData.totalHadiths}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Chapter-by-chapter progress bars
                            Text(
                                text = "Chapter Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            HadithsData.chapters.forEach { chapter ->
                                val readCount = chapter.rangeIds.count { readHadiths.contains(it) }
                                val totalInChap = chapter.rangeIds.count()
                                val chapColor = getChapterColor(chapter.id)

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ch ${chapter.id}: ${chapter.name}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "$readCount/$totalInChap",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = chapColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { readCount.toFloat() / totalInChap },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = chapColor,
                                    trackColor = chapColor.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reading preferences section
        item {
            PreferenceHeaderTitle("Reading Preferences")
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Translation Font Size", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        val label = when (fontSizeOpt) {
                            14 -> "Small"
                            16 -> "Medium"
                            18 -> "Large"
                            22 -> "XL"
                            else -> "Medium"
                        }
                        Text(label, fontWeight = FontWeight.Bold, color = activeColor)
                    }
                    Slider(
                        value = if (fontSizeOpt == 14) 0f else if (fontSizeOpt == 16) 1f else if (fontSizeOpt == 18) 2f else 3f,
                        onValueChange = {
                            val targetSize = when (it.toInt()) {
                                0 -> 14
                                1 -> 16
                                2 -> 18
                                else -> 22
                            }
                            viewModel.updateFontSize(targetSize)
                        },
                        steps = 2,
                        valueRange = 0f..3f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = activeColor,
                            thumbColor = activeColor
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Arabic Slider (18 to 32)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Arabic Font Size", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${arabicSizeOpt}px", fontWeight = FontWeight.Bold, color = activeColor)
                    }
                    Slider(
                        value = arabicSizeOpt.toFloat(),
                        onValueChange = { viewModel.updateArabicSize(it.toInt()) },
                        valueRange = 18f..32f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = activeColor,
                            thumbColor = activeColor
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Font Family toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Translation Font Style", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (fontStyleOpt == "sans") activeColor else Color.Transparent)
                                    .bounceClick { viewModel.updateFontStyle("sans") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "Sans-serif",
                                    fontSize = 11.sp,
                                    color = if (fontStyleOpt == "sans") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (fontStyleOpt == "serif") activeColor else Color.Transparent)
                                    .bounceClick { viewModel.updateFontStyle("serif") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "Serif",
                                    fontSize = 11.sp,
                                    color = if (fontStyleOpt == "serif") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Spacing row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Translation Line Height", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("compact", "normal", "relaxed").forEach { mode ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (lineHeightOpt == mode) activeColor else Color.Transparent)
                                        .bounceClick { viewModel.updateLineHeight(mode) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                        fontSize = 10.sp,
                                        color = if (lineHeightOpt == mode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live preview section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Live Preview:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (showArabicOn) {
                                Text(
                                    text = "مَنْ تَبَسَّمَ فِي وَجْهِ أَخِيهِ صَدَقَةٌ",
                                    fontSize = arabicSizeOpt.sp,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = (arabicSizeOpt * 1.5f).sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = "Your smiling in the face of your brother is a form of charity for you.",
                                fontSize = fontSizeOpt.sp,
                                fontFamily = if (fontStyleOpt == "serif") FontFamily.Serif else FontFamily.SansSerif,
                                lineHeight = when (lineHeightOpt) {
                                    "compact" -> 22.sp
                                    "normal" -> 28.sp
                                    "relaxed" -> 34.sp
                                    else -> 28.sp
                                }
                            )
                        }
                    }
                }
            }
        }

        // Appearance options (Theme/Accent Swatches)
        item {
            PreferenceHeaderTitle("Appearance")
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Theme toggler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Theme", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("light", "dark", "white", "system").forEach { t ->
                                val displayName = when (t) {
                                    "light" -> "Cream"
                                    "dark" -> "Dark"
                                    "white" -> "White"
                                    "system" -> "System"
                                    else -> t.replaceFirstChar { it.uppercase() }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (currentTheme == t) activeColor else Color.Transparent)
                                        .clickable { viewModel.updateTheme(t) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        fontSize = 11.sp,
                                        color = if (currentTheme == t) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Accent Colors Row Swatches
                    Text("App Accent Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))

                    val colorOptions = listOf(
                        "emerald" to Color(0xFF059669),
                        "gold" to Color(0xFFD97706),
                        "indigo" to Color(0xFF4F46E5),
                        "rose" to Color(0xFFE11D48),
                        "teal" to Color(0xFF0D9488),
                        "slate" to Color(0xFF475569)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOptions.forEach { (name, colorValue) ->
                            val isSelected = accentColorName == name
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorValue)
                                    .clickable { viewModel.updateAccentColor(name) }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reading experience toggles
        item {
            PreferenceHeaderTitle("Reading Experience")
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    TriggerOptionSwitchRow("Haptic Feedback (Vibrate)", hapticOn) {
                        viewModel.updateHaptic(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TriggerOptionSwitchRow("Auto-mark as Read on Open", autoReadOn) {
                        viewModel.updateAutoRead(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TriggerOptionSwitchRow("Show Arabic text globally", showArabicOn) {
                        viewModel.updateShowArabic(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TriggerOptionSwitchRow("Show narrator name", showNarratorOn) {
                        viewModel.updateShowNarrator(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TriggerOptionSwitchRow("Daily Hadith Notification (8:00 AM)", dailyNotifOn) { enabled ->
                        viewModel.updateDailyNotifications(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                    if (dailyNotifOn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.triggerTestNotification()
                                        Toast.makeText(context, "Notification sent! Check your notification bar.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Test Notification", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Reset and danger actions
        item {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset All Study Progress", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // About the references
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Small Deeds, Big Rewards ${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Based on authentic Ahadith graded Sahih/Hasan by Shaykh al-Albani and other major Islamic scholars.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Modal Confirmation prompt dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Study Progress?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently erase all favorited items, bookmark records, read tracking, and progress metrics. This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetProgress()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Progress", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PreferenceHeaderTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun TriggerOptionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
