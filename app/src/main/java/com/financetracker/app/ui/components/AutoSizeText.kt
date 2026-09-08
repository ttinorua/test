package com.financetracker.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A single-line [Text] that shrinks its font size (down to [minFontSize]) until it fits
 * the available width, instead of wrapping or clipping. Useful for currency amounts whose
 * length varies with the value shown.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyleDefault(),
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minFontSize: TextUnit = 11.sp
) {
    var fontSize by remember(text, style) { mutableStateOf(style.fontSize) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        style = style.copy(fontSize = fontSize),
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > minFontSize) {
                fontSize = (fontSize.value - 1).sp
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
private fun LocalTextStyleDefault(): TextStyle = MaterialTheme.typography.titleLarge
