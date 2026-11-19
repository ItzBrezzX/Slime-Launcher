package com.kingbrezz.slime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.nav.Routes
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Faint
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Line
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.Surface
import com.kingbrezz.slime.ui.theme.Surface2
import com.kingbrezz.slime.ui.theme.Warn

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun Badge(text: String, good: Boolean = false, warn: Boolean = false) {
    val bg = when {
        good -> SlimeGreen
        warn -> Warn.copy(alpha = 0.2f)
        else -> Surface2
    }
    val fg = when {
        good -> Ink
        warn -> Warn
        else -> Muted
    }
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (good) SlimeGreen else Line, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun SectionTitle(eyebrow: String, title: String) {
    Column {
        Text(eyebrow.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Text(title, color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SlimeSwitch(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) SlimeGreen else Surface2)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Ink else Fg))
    }
}

@Composable
fun BottomNav(current: String, onNavigate: (String) -> Unit) {
    data class Item(val route: String, val label: String, val icon: @Composable (Color) -> Unit)
    val items = listOf(
        Item(Routes.HOME, "Beranda") { c -> SIcon.Home(c) },
        Item(Routes.VERSIONS, "Versi") { c -> SIcon.Layers(c) },
        Item(Routes.MODULES, "Modul") { c -> SIcon.Modules(c) },
        Item(Routes.CONFIGS, "Config") { c -> SIcon.Config(c) },
        Item(Routes.LIBRARY, "Pustaka") { c -> SIcon.Library(c) },
        Item(Routes.SETTINGS, "Setelan") { c -> SIcon.Settings(c) },
    )
    Row(
        Modifier.fillMaxWidth().background(Bg).border(1.dp, Line).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            val active = current == item.route
            val tint = if (active) SlimeGreen else Muted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onNavigate(item.route) }.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                item.icon(tint)
                Text(item.label, color = if (active) SlimeGreen else Faint, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun Mono(text: String, color: Color = Fg, size: Int = 14) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp)
}

@Composable
fun BrandMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(SlimeGreen),
        contentAlignment = Alignment.Center,
    ) {
        SIcon.Droplet(Ink, size * 0.65f)
    }
}
