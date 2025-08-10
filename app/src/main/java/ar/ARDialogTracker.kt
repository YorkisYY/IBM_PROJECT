package ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.node.ModelNode
import kotlin.math.roundToInt

class ARDialogTracker {
    
    /**
     * Watson Dialog - bound to first cat, wider dialog
     */
    @Composable
    fun WatsonDialogBoundToFirstCat(
        isChatVisible: Boolean,
        chatMessage: String,
        firstCatModel: ModelNode?,
        firstCatDialogPosition: Offset,
        hasFirstCat: Boolean
    ) {
        if (isChatVisible && chatMessage.isNotEmpty() && hasFirstCat && firstCatModel != null) {
            BoxWithConstraints {
                val dialogOffset = with(LocalDensity.current) {
                    val dialogWidth = 340.dp.toPx() // Increase width from 280 to 340
                    val maxDialogHeight = 160.dp.toPx() // Slightly increase height
                    val margin = 20.dp.toPx()
                    
                    // Use maxWidth and maxHeight provided by BoxWithConstraints
                    val screenWidthPx = maxWidth.toPx()
                    val screenHeightPx = maxHeight.toPx()
                    
                    // Calculate safe boundaries
                    val safeMaxX: Float = (screenWidthPx - dialogWidth).coerceAtLeast(margin)
                    val safeMinY: Float = 120.dp.toPx() // Reserve space for control panel
                    
                    // Dialog position: ensure within visible screen range
                    val catScreenY = firstCatDialogPosition.y
                    
                    // Ensure dialog is within visible screen range
                    val dialogTopY = if (catScreenY > 200.dp.toPx()) {
                        // If cat position is low enough, dialog above cat
                        (catScreenY - maxDialogHeight - 20.dp.toPx()).coerceAtLeast(safeMinY)
                    } else {
                        // If cat position is too high, dialog below cat
                        (catScreenY + 50.dp.toPx()).coerceAtMost(screenHeightPx - maxDialogHeight - 50.dp.toPx())
                    }
                    
                    IntOffset(
                        x = (firstCatDialogPosition.x - (dialogWidth / 2)).roundToInt()
                            .coerceIn(margin.roundToInt(), safeMaxX.roundToInt()),
                        y = dialogTopY.roundToInt()
                    )
                }
                
                // Watson Dialog - wider dialog design
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { dialogOffset },
                    contentAlignment = Alignment.TopStart
                ) {
                    Column {
                        // Dialog content - variable height, supports scrolling
                        Box(
                            modifier = Modifier
                                .width(340.dp) // Wider dialog
                                .heightIn(min = 90.dp, max = 160.dp) // Slightly increase min and max height
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .background(
                                    color = Color(0xFF2196F3),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(18.dp), // Increase padding for more comfortable content
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp) // Slightly increase spacing
                            ) {
                                // Display first cat name and position info
                                Text(
                                    text = "${firstCatModel.name ?: "First Cat"} (${firstCatDialogPosition.x.toInt()}, ${firstCatDialogPosition.y.toInt()})",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp, // Slightly smaller for coordinate display
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Dialog content - scrollable area
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f), // Take remaining space
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    item {
                                        Text(
                                            text = chatMessage,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp, // Increase line height for better readability
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Small arrow pointing to cat - close to dialog bottom, adjust position for new width
                        Box(
                            modifier = Modifier
                                .padding(start = (340.dp / 2 - 6.dp)) // Adjust arrow position for new width
                                .size(12.dp)
                                .background(
                                    Color(0xFF2196F3),
                                    RoundedCornerShape(topStart = 6.dp)
                                )
                                .offset(y = (-1).dp)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Watson Dialog - center when no first cat - also supports scrolling and wider design
     */
    @Composable
    fun WatsonDialogCenter(
        isChatVisible: Boolean,
        chatMessage: String,
        hasFirstCat: Boolean
    ) {
        if (isChatVisible && chatMessage.isNotEmpty() && !hasFirstCat) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 120.dp)
                        .widthIn(min = 240.dp, max = 360.dp) // Increase max width
                        .heightIn(min = 70.dp, max = 220.dp) // Increase height range
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .background(
                            color = Color(0xFF2196F3),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp) // Increase padding
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.Top
                    ) {
                        item {
                            Text(
                                text = chatMessage,
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 22.sp, // Increase line height
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}