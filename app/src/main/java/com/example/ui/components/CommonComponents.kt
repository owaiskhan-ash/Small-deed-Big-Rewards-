package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun DecorativeDiamond(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        try {
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(w / 2f, h)
                    lineTo(0f, h / 2f)
                    close()
                }
                drawPath(path, color)
            }
        } catch (e: Exception) {
            // Ignore potential headless or unsupported hardware-accelerated draw crashes silently
        }
    }
}

@Composable
fun OrnamentalDivider(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.25f))
        )
        Spacer(modifier = Modifier.width(16.dp))
        DecorativeDiamond(color = color, modifier = Modifier.size(10.dp))
        Spacer(modifier = Modifier.width(8.dp))
        DecorativeDiamond(color = color.copy(alpha = 0.5f), modifier = Modifier.size(6.dp))
        Spacer(modifier = Modifier.width(8.dp))
        DecorativeDiamond(color = color, modifier = Modifier.size(10.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.25f))
        )
    }
}

@Composable
fun IslamicHeaderDecoration(color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(modifier = Modifier.width(40.dp).height(1.dp).background(color.copy(alpha = 0.4f)))
            Spacer(modifier = Modifier.width(8.dp))
            DecorativeDiamond(color = color, modifier = Modifier.size(8.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(40.dp).height(1.dp).background(color.copy(alpha = 0.4f)))
        }
    }
}

fun Modifier.onSwipeGesture(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    return this.pointerInput(onSwipeLeft, onSwipeRight) {
        var offsetX = 0f
        detectHorizontalDragGestures(
            onDragEnd = {
                if (offsetX < -120f) {
                    onSwipeLeft()
                } else if (offsetX > 120f) {
                    onSwipeRight()
                }
                offsetX = 0f
            },
            onDragCancel = {
                offsetX = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                offsetX += dragAmount
            }
        )
    }
}
