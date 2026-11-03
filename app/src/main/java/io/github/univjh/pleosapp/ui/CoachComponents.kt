package io.github.univjh.pleosapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal enum class CoachIcon { HOME, BOOK, SETTINGS, ROAD, ARROW, BACK, START, BRAKE, BELT, CHECK }

/** Small vector symbols, always accompanied by a visible label. */
@Composable internal fun Glyph(icon: CoachIcon, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(28.dp)) {
        val u = size.minDimension / 24f
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(tint,
            Offset(x*u,y*u), Offset(xx*u,yy*u), 1.8f*u, StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>) {
            val p = Path().apply { points.forEachIndexed { i, (x,y) -> if(i==0) moveTo(x*u,y*u) else lineTo(x*u,y*u) } }
            drawPath(p, tint, style = Stroke(1.8f*u, cap=StrokeCap.Round))
        }
        when(icon) {
            CoachIcon.HOME -> { path(3f to 11f,12f to 3f,21f to 11f); path(5f to 10f,5f to 21f,10f to 21f,10f to 15f,14f to 15f,14f to 21f,19f to 21f,19f to 10f) }
            CoachIcon.BOOK -> { path(12f to 6f,8f to 4f,3f to 4f,3f to 19f,8f to 19f,12f to 21f,16f to 19f,21f to 19f,21f to 4f,16f to 4f,12f to 6f,12f to 21f) }
            CoachIcon.SETTINGS -> { listOf(6f,12f,18f).forEach { line(3f,it,21f,it) }; listOf(7f to 6f,16f to 12f,9f to 18f).forEach { (x,y) -> drawCircle(tint,2.5f*u,Offset(x*u,y*u)) } }
            CoachIcon.ROAD -> { line(7f,3f,3f,21f); line(17f,3f,21f,21f); line(12f,3f,12f,7f); line(12f,11f,12f,14f); line(12f,18f,12f,21f) }
            CoachIcon.ARROW -> { line(4f,12f,20f,12f); path(14f to 6f,20f to 12f,14f to 18f) }
            CoachIcon.BACK -> { line(4f,12f,20f,12f); path(10f to 6f,4f to 12f,10f to 18f) }
            CoachIcon.START -> { path(4f to 18f,10f to 12f,14f to 15f,21f to 5f); path(14f to 5f,21f to 5f,21f to 12f) }
            CoachIcon.BRAKE -> { drawCircle(tint,8f*u,Offset(12f*u,12f*u),style=Stroke(1.8f*u)); line(9f,8f,9f,16f); line(15f,8f,15f,16f) }
            CoachIcon.BELT -> { drawCircle(tint,2.5f*u,Offset(12f*u,5f*u),style=Stroke(1.8f*u)); path(6f to 21f,7f to 12f,17f to 12f,18f to 21f); line(5f,10f,19f,21f); line(7f,20f,17f,11f) }
            CoachIcon.CHECK -> path(5f to 12f,10f to 17f,20f to 6f)
        }
    }
}

@Composable internal fun IconTile(icon: CoachIcon, container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(container), contentAlignment=Alignment.Center) { Glyph(icon, tint=tint) }
}
@Composable internal fun Badge(label: String, container: Color = MaterialTheme.colorScheme.primaryContainer,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Surface(color=container, contentColor=color, shape=RoundedCornerShape(10.dp)) {
        Text(label, Modifier.padding(horizontal=12.dp,vertical=7.dp), style=MaterialTheme.typography.labelMedium)
    }
}
@Composable internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun Sheet(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large, color=color,
        border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(28.dp), verticalArrangement=Arrangement.spacedBy(20.dp), content=content)
    }
}
@Composable internal fun Action(label: String, modifier: Modifier = Modifier, primary: Boolean = false,
    danger: Boolean = false, onClick: () -> Unit) {
    val m = modifier.heightIn(min=60.dp)
    if(primary) Button(onClick, m, shape=MaterialTheme.shapes.medium,
        contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp)) { Text(label) }
    else OutlinedButton(onClick, m, shape=MaterialTheme.shapes.medium,
        colors=ButtonDefaults.outlinedButtonColors(contentColor=if(danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp)) { Text(label) }
}
@Composable internal fun SectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title, style=MaterialTheme.typography.headlineSmall)
        if(detail!=null) Muted(detail)
    }
}
@Composable internal fun Metrics(values: List<Pair<String,String>>) {
    BoxWithConstraints {
        val stacked = maxWidth < 450.dp
        val cell: @Composable (Pair<String,String>, Modifier) -> Unit = { (label,value), modifier ->
            Column(modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Muted(label)
                Text(value, style=MaterialTheme.typography.headlineMedium)
            }
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.medium) {
            if(stacked) Column(Modifier.fillMaxWidth()) { values.forEachIndexed { i,v ->
                if(i>0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                cell(v,Modifier.fillMaxWidth())
            } } else Row(Modifier.fillMaxWidth()) { values.forEach { cell(it,Modifier.weight(1f)) } }
        }
    }
}
@Composable internal fun AdaptivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints {
        if(maxWidth>=840.dp) Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1.65f)) { first() }; Box(Modifier.weight(1f)) { second() }
        } else Column(verticalArrangement=Arrangement.spacedBy(20.dp)) { first(); second() }
    }
}
@Composable internal fun Navigation(page: String, vertical: Boolean, onSelect: (String) -> Unit) {
    val entries = listOf(Triple("홈","주행 돌아보기",CoachIcon.HOME),Triple("학습·추이","학습·개선 추이",CoachIcon.BOOK),Triple("설정","설정",CoachIcon.SETTINGS))
    val entry: @Composable (Triple<String,String,CoachIcon>, Modifier) -> Unit = { (key,label,icon),modifier ->
        val selected = page==key
        val bg = if(selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        val fg = if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Row(modifier.clip(MaterialTheme.shapes.medium).background(bg)
            .selectable(selected,role=Role.Tab,onClick={onSelect(key)}).heightIn(min=68.dp).padding(horizontal=16.dp,vertical=16.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(vertical) Glyph(icon,tint=fg)
            Text(if(vertical) label else key, color=fg,style=MaterialTheme.typography.labelLarge)
        }
    }
    if(vertical) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { entries.forEach { entry(it,Modifier.fillMaxWidth()) } }
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { entries.forEach { entry(it,Modifier.weight(1f)) } }
}
