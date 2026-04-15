package com.paul.sprintsync

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.sprintsync.core.theme.InterExtraBoldTabularTypography

@Composable
internal fun DisplayResultsCard(rows: List<DisplayLapRow>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val displayCardBackground = Color(0xFFFFCC00)
        val displayTimeColor = Color(0xFF000000)
        val displayDeviceColor = Color(0xFF000000)
        val density = LocalDensity.current
        val layout = displayLayoutSpecForCount(rows.size)
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "WAITING FOR LAP RESULTS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        val count = rows.size.coerceAtLeast(1)
        val availableHeight = maxHeight.takeIf { it > 0.dp } ?: layout.rowHeight
        val stackVertically = shouldStackDisplayCardsVertically(count)
        val visibleCards = if (stackVertically) 1 else displayHorizontalVisibleCardSlots(count)
        val cardHeight = if (stackVertically) {
            ((availableHeight - layout.dividerWidth) / count).coerceAtLeast(layout.minRowHeight)
        } else {
            availableHeight.coerceAtLeast(layout.minRowHeight)
        }
        val cardWidth = if (stackVertically) {
            maxWidth.coerceAtLeast(layout.minRowHeight)
        } else {
            ((maxWidth - (layout.dividerWidth * (visibleCards - 1))) / visibleCards)
                .coerceAtLeast(layout.minRowHeight)
        }
        val rowContentWidth = (cardWidth - (layout.horizontalPadding * 2)).coerceAtLeast(1.dp)
        val widestLapTimeLabelLength = rows.maxOf { it.lapTimeLabel.length }
        val clampedTimeFont = clampDisplayTimeFont(
            base = layout.timeFont,
            rowHeight = cardHeight,
            rowContentWidth = rowContentWidth,
            maxLabelLength = widestLapTimeLabelLength,
            density = density,
        )
        if (stackVertically) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(rows) { index, row ->
                    Box {
                        val isKnockedOut = row.showLives && row.currentLives <= 0
                        DisplayResultPanel(
                            row = row,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            layout = layout,
                            timeFont = clampedTimeFont,
                            defaultCardBackground = displayCardBackground,
                            defaultTimeColor = displayTimeColor,
                            defaultDeviceColor = displayDeviceColor,
                        )
                        if (!isKnockedOut) {
                            DisplayDirectionArrow(
                                direction = if (index == 0) DisplayArrowDirection.LEFT else DisplayArrowDirection.RIGHT,
                                modifier = Modifier
                                    .align(if (index == 0) Alignment.TopStart else Alignment.TopEnd)
                                    .fillMaxHeight(0.34f)
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (index < rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(layout.dividerWidth)
                                .background(Color.Black),
                        )
                    }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(rows) { index, row ->
                    Row(
                        modifier = Modifier.height(cardHeight),
                    ) {
                        DisplayResultPanel(
                            row = row,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            layout = layout,
                            timeFont = clampedTimeFont,
                            defaultCardBackground = displayCardBackground,
                            defaultTimeColor = displayTimeColor,
                            defaultDeviceColor = displayDeviceColor,
                        )
                        if (index < rows.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(layout.dividerWidth)
                                    .height(cardHeight)
                                    .background(Color.Black),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayResultPanel(
    row: DisplayLapRow,
    cardWidth: Dp,
    cardHeight: Dp,
    layout: DisplayLayoutSpec,
    timeFont: TextUnit,
    defaultCardBackground: Color,
    defaultTimeColor: Color,
    defaultDeviceColor: Color,
) {
    val isKnockedOut = row.showLives && row.currentLives <= 0
    val cardBackground = when {
        isKnockedOut -> Color(0xFFB71C1C)
        row.isOverLimit -> Color(0xFFD32F2F)
        row.isUnderLimit -> Color(0xFF2E7D32)
        else -> defaultCardBackground
    }
    val foregroundColor = if (row.isOverLimit || row.isUnderLimit) Color.White else defaultDeviceColor
    val waitPulseTransition = rememberInfiniteTransition(label = "waitPulse")
    val waitPulseAlpha by waitPulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waitPulseAlpha",
    )
    val lapTextAlpha = if (row.isWaiting) waitPulseAlpha else 1f
    val readyFont = maxOf((timeFont.value * 0.72f), 32f).sp
    val lowLivesPulseTransition = rememberInfiniteTransition(label = "lowLivesPulse")
    val lowLivesScale by lowLivesPulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lowLivesScale",
    )
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(cardBackground),
    ) {
        if (isKnockedOut) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(42.dp),
            ) {
                val stroke = size.minDimension * 0.08f
                drawLine(
                    color = Color.Red,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = Color.Red,
                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = stroke,
                )
            }
            return
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = row.lapTimeLabel,
                style = MaterialTheme.typography.displayLarge.merge(
                    InterExtraBoldTabularTypography.merge(
                        TextStyle(
                            fontSize = if (row.lapTimeLabel == "READY") readyFont else timeFont,
                        ),
                    ),
                ),
                color = if (row.isOverLimit || row.isUnderLimit) Color.White else defaultTimeColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alpha(lapTextAlpha),
            )
            if (row.showLives) {
                val heartSize = 58.dp
                val clampedMaxLives = row.maxLives.coerceAtLeast(1)
                val clampedCurrentLives = row.currentLives.coerceIn(0, clampedMaxLives)
                val showLowLivesPulse = clampedCurrentLives in 1..3
                val pulseHeartSize = 70.dp
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showLowLivesPulse) {
                        repeat(clampedCurrentLives) {
                            Image(
                                painter = painterResource(id = R.drawable.heart_svgrepo_com),
                                contentDescription = "Life",
                                modifier = Modifier
                                    .size(pulseHeartSize)
                                    .scale(lowLivesScale),
                                colorFilter = ColorFilter.tint(Color(0xFFC62828)),
                            )
                        }
                    } else {
                        repeat(clampedMaxLives) { index ->
                            val heartColor = if (index < clampedCurrentLives) {
                                Color(0xFFC62828)
                            } else {
                                Color.Black
                            }
                            Image(
                                painter = painterResource(id = R.drawable.heart_svgrepo_com),
                                contentDescription = "Life",
                                modifier = Modifier.size(heartSize),
                                colorFilter = ColorFilter.tint(heartColor),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class DisplayArrowDirection {
    LEFT,
    RIGHT,
}

@Composable
private fun DisplayDirectionArrow(direction: DisplayArrowDirection, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = if (direction == DisplayArrowDirection.LEFT) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Canvas(modifier = Modifier.size(width = 136.dp, height = 96.dp)) {
            val shaftHeight = size.height * 0.3f
            val headWidth = size.width * 0.38f
            val shaftStartX = if (direction == DisplayArrowDirection.LEFT) headWidth else 0f
            val shaftEndX = if (direction == DisplayArrowDirection.LEFT) size.width else size.width - headWidth
            drawRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = shaftStartX,
                    y = (size.height - shaftHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = shaftEndX - shaftStartX,
                    height = shaftHeight,
                ),
            )
            val headPath = Path().apply {
                if (direction == DisplayArrowDirection.LEFT) {
                    moveTo(headWidth, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(headWidth, size.height)
                } else {
                    moveTo(size.width - headWidth, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width - headWidth, size.height)
                }
                close()
            }
            drawPath(
                path = headPath,
                color = Color.Black,
            )
        }
    }
}

