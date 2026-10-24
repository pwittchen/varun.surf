package surf.varun.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/** The current time, ticking every [periodMs] so "3 min ago" labels stay right. */
@Composable
fun rememberNow(periodMs: Long = 30_000): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = ZonedDateTime.now()
        }
    }
    return now
}
