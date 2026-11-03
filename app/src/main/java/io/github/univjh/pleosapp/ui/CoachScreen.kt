@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.univjh.pleosapp.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.univjh.pleosapp.data.*
import io.github.univjh.pleosapp.domain.*
import io.github.univjh.pleosapp.vehicle.SourceStatus
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun date(time: Long, pattern: String = "M월 d일 · HH:mm") = SimpleDateFormat(pattern, Locale.KOREA).format(Date(time))
private fun number(n: Double) = String.format(Locale.KOREA, "%.1f", n)
private fun kind(e: DrivingEvent) = EventKind.valueOf(e.kind)
private fun EventKind.icon() = when(this) { EventKind.RAPID_START -> CoachIcon.START; EventKind.HARD_BRAKING -> CoachIcon.BRAKE; EventKind.SEAT_BELT -> CoachIcon.BELT }
private fun tripState(t: Trip) = when(t.status) { "COMPLETE" -> "주차 완료"; "INTERRUPTED" -> "중단된 기록"; else -> "기록 중" }
private fun missing(t: Trip): String = (t.validMs+t.missingMs).let { if(it>0) "${number(t.missingMs*100.0/it)}%" else "계산 대기" }

@Composable
fun CoachScreen(onPermissions: () -> Unit, vm: CoachViewModel = viewModel()) {
    val source by vm.source.collectAsStateWithLifecycle()
    val status by vm.recording.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("홈") }
    var tripId by rememberSaveable { mutableStateOf<String?>(null) }
    var eventId by rememberSaveable { mutableStateOf<String?>(null) }
    var lessonId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { tick=SystemClock.elapsedRealtime(); delay(250) } }
    val currentElapsed=maxOf(tick,SystemClock.elapsedRealtime())
    val review=source.parkedConfirmed && canReview(source.frame,source.uxAllowed) &&
        source.frame?.let { currentElapsed-it.monotonicMs in 0..500 }==true
    LaunchedEffect(review) { if(!review) { page="홈"; tripId=null; eventId=null; lessonId=null; deleteId=null } }
    val selectedTrip=trips.find { it.id==tripId }
    val selectedEvent=events.find { it.id==eventId }
    val selectedLesson=lessons.find { it.id==lessonId }
    val navigate: (String) -> Unit = { page=it; tripId=null; eventId=null; lessonId=null }
    val back: () -> Unit = { when { lessonId!=null -> lessonId=null; eventId!=null -> eventId=null; else -> tripId=null } }
    BackHandler(review && (tripId!=null || eventId!=null || lessonId!=null),onBack=back)
    val listState=rememberLazyListState()
    LaunchedEffect(page,tripId,eventId,lessonId,review) { listState.scrollToItem(0) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val fontScale=LocalDensity.current.fontScale
            val wide=maxWidth>=1000.dp && fontScale<1.4f
            val showSidebarFooter=maxHeight>=900.dp && fontScale<=1.15f
            val columns=if(maxWidth>=1300.dp) 3 else if(maxWidth>=650.dp) 2 else 1
            Row(Modifier.fillMaxSize()) {
                if(wide && review) Surface(Modifier.width(250.dp).fillMaxHeight(),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(32.dp)) {
                        Brand(true)
                        Navigation(page,true,navigate)
                        Spacer(Modifier.weight(1f))
                        if(showSidebarFooter) {
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Glyph(CoachIcon.ROAD)
                            Text("다음 운전이\n더 편안해지도록",style=MaterialTheme.typography.titleMedium)
                            Muted("기록은 이 기기에만\n저장됩니다.")
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Surface(color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(horizontal=if(wide) 36.dp else 24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                if(!wide || !review) Brand(false) else Text("주행 후 안전운전 코치",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(review && wide) Badge("주차 확인됨")
                            }
                            if(review && !wide) Navigation(page,false,navigate)
                        }
                    }
                    LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(
                        start=if(wide) 36.dp else 24.dp,end=if(wide) 36.dp else 24.dp,top=8.dp,bottom=36.dp),
                        verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        if(!review) item { DrivingStatus(source,status,consent) }
                        else {
                            if(message!=null) item { Sheet(color=MaterialTheme.colorScheme.secondaryContainer) {
                                Text(message!!); Action("닫기") { vm.message.value=null }
                            } }
                            if(selectedLesson!=null || selectedEvent!=null || selectedTrip!=null) item {
                                TextButton(onClick=back,modifier=Modifier.heightIn(min=60.dp)) {
                                    Glyph(CoachIcon.BACK); Spacer(Modifier.width(12.dp)); Text("돌아가기")
                                }
                            }
                            when {
                                selectedLesson!=null -> item { LessonDetail(selectedLesson,goal) { vm.setGoal(selectedLesson.id); lessonId=null } }
                                selectedEvent!=null -> item { EventDetail(selectedEvent,feedback.find { it.eventId==selectedEvent.id },vm) { lessonId=kind(selectedEvent).lessonId } }
                                selectedTrip!=null -> {
                                    val tripEvents=events.filter { it.tripId==selectedTrip.id }
                                    item { SectionTitle("주행 리포트",date(selectedTrip.startedAt)) }
                                    item { Sheet {
                                        TripBadges(selectedTrip)
                                        Metrics(listOf("기록 거리" to "${number(selectedTrip.distanceMeters/1000)} km","돌아볼 장면" to "${tripEvents.size}개","신호 누락" to missing(selectedTrip)))
                                        if(selectedTrip.status=="INTERRUPTED") Muted("마지막 저장 지점까지 보존했어요. 중단 이후 구간은 분석하지 않았어요.")
                                    } }
                                    item { SectionTitle("먼저 돌아볼 장면","한 번에 하나씩, 다음 운전에 도움이 되는 장면을 확인하세요.") }
                                    if(tripEvents.isEmpty()) item { EmptyState("감지된 장면이 없어요","관측 범위 밖의 위험까지 없었다는 뜻은 아니에요.") }
                                    val priority=tripEvents.sortedBy { if(it.kind==EventKind.SEAT_BELT.name) 0 else 1 }.take(3)
                                    items(priority.chunked(columns)) { row -> CardGrid(row,columns) { e -> EventCard(e,feedback.any { it.eventId==e.id && !it.includeInStats }) { eventId=e.id } } }
                                    if(tripEvents.isNotEmpty()) {
                                        item { SectionTitle("전체 장면","${tripEvents.size}개의 관측 기록") }
                                        items(tripEvents,key={it.id}) { e -> EventRow(e,feedback.any { it.eventId==e.id && !it.includeInStats }) { eventId=e.id } }
                                    }
                                    if(selectedTrip.status!="RECORDING") item { Action("이 주행 삭제",danger=true) { deleteId=selectedTrip.id } }
                                }
                                page=="학습·추이" -> {
                                    item { SectionTitle("작은 실천이 만드는 변화","나에게 필요한 안전 지식을 익히고, 운전 습관을 돌아보세요.") }
                                    item { Trends(trips,events,feedback,goal) }
                                    item { SectionTitle("안전 지식 24가지","관측 가능한 장면과, 미리 알아두면 좋은 운전 지식") }
                                    items(lessons.chunked(columns)) { row -> CardGrid(row,columns) { lesson -> LessonCard(lesson,goal==lesson.id) { lessonId=lesson.id } } }
                                }
                                page=="설정" -> {
                                    item { SectionTitle("내 기록, 내 선택","자동 기록과 보관 설정을 확인하세요.") }
                                    item { Sheet {
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                            IconTile(CoachIcon.ROAD); Column { Text("자동 주행 기록",style=MaterialTheme.typography.titleLarge); Muted(if(consent) "켜짐 · ${if(running) "기록 준비 완료" else "연결 확인 중"}" else "꺼짐") }
                                        }
                                        Muted("동의하면 속도·기어·안전벨트 경고를 저장해요. 앱을 닫거나 차량이 재시작되어도 기록을 준비합니다.")
                                        Action(if(consent) "동의 철회 · 자동 기록 끄기" else "동의하고 자동 기록 켜기",primary=!consent) { vm.setConsent(!consent) }
                                        Muted("끄면 새 기록의 저장이 중단됩니다. 기존 기록은 따로 삭제할 수 있어요.")
                                    } }
                                    item { Sheet {
                                        SectionTitle("연결 상태")
                                        Text(source.message,style=MaterialTheme.typography.titleMedium)
                                        Muted(source.beltMessage)
                                        Action("차량·알림 권한 확인",onClick=onPermissions)
                                    } }
                                    item { Sheet {
                                        SectionTitle("보관과 삭제")
                                        Metrics(listOf("원시 신호" to "7일","리포트·장면 근거" to "90일"))
                                        Muted("이 기기에만 저장하며, 보관 기간이 지나면 앱이나 기록 서비스 실행 시 삭제해요.")
                                        Action("전체 기록 삭제",danger=true) { deleteId="ALL" }
                                        Muted("전체 삭제 전에는 자동 기록을 꺼주세요.")
                                    } }
                                    item { Muted("분석 기준 experimental-1 · 초기 실험값\n법규 위반을 확정하거나 운전자의 신원을 식별하지 않아요.") }
                                }
                                else -> {
                                    item { SectionTitle("오늘의 운전을 돌아보세요","중요한 장면을 확인하고, 다음 운전을 더 편안하게.") }
                                    item { AdaptivePair(first={
                                        HomeHero(consent,status,source, trips.firstOrNull { it.status!="RECORDING" },
                                            onConsent={vm.setConsent(true)},onTrip={tripId=it})
                                    },second={ GoalCard(goal) { lessonId=goal } }) }
                                    item { SectionTitle("최근 주행", "${trips.size}개의 기록 · 주차 후 언제든 다시 볼 수 있어요") }
                                    if(trips.isEmpty()) item { EmptyState("첫 주행을 기다리고 있어요","자동 기록을 켠 뒤 D 또는 R로 전환하면 기록이 시작됩니다.") }
                                    items(trips,key={it.id}) { t -> TripRow(t,events.count { it.tripId==t.id }) { tripId=t.id } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(deleteId!=null && review) AlertDialog(onDismissRequest={deleteId=null},
        title={Text("기록을 삭제할까요?")},
        text={Text(if(deleteId=="ALL") "저장한 모든 주행과 장면·상황 설명을 삭제해요. 자동 기록을 먼저 꺼주세요." else "이 주행과 연결된 장면·상황 설명을 삭제해요. 되돌릴 수 없어요.")},
        confirmButton={TextButton(onClick={ if(deleteId=="ALL") vm.deleteAll() else { vm.deleteTrip(deleteId!!); tripId=null }; deleteId=null },modifier=Modifier.heightIn(min=60.dp)) { Text("삭제",color=MaterialTheme.colorScheme.error) }},
        dismissButton={TextButton(onClick={deleteId=null},modifier=Modifier.heightIn(min=60.dp)) { Text("취소") }})
}

@Composable private fun Brand(vertical: Boolean) {
    if(vertical) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconTile(CoachIcon.ROAD); Text("운전\n돌아보기",style=MaterialTheme.typography.headlineMedium)
    } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Glyph(CoachIcon.ROAD); Text("운전 돌아보기",style=MaterialTheme.typography.titleLarge)
    }
}
@Composable private fun DrivingStatus(source: SourceStatus,status: String,consent: Boolean) {
    val parked=source.frame?.gear==Gear.PARK
    Sheet(color=MaterialTheme.colorScheme.primaryContainer) {
        Spacer(Modifier.height(24.dp))
        Glyph(CoachIcon.ROAD,Modifier.size(72.dp),MaterialTheme.colorScheme.onPrimaryContainer)
        Text(if(parked) "주차 상태를\n확인하고 있어요" else "지금은 운전에\n집중하세요",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
        Badge(if(consent) status else "자동 기록 설정 전",MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.onSurface)
        Text("P 상태로 30초 유지하고 차량의 화면 사용이 허용되면\n리포트와 설정을 열 수 있어요.",color=MaterialTheme.colorScheme.onPrimaryContainer)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Muted(source.message)
        if(source.frame==null) Muted("정차 후 차량 권한과 연결 상태를 확인해 주세요.")
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun HomeHero(consent: Boolean,status: String,source: SourceStatus,latest: Trip?,onConsent: ()->Unit,onTrip: (String)->Unit) {
    Surface(color=MaterialTheme.colorScheme.inverseSurface,contentColor=MaterialTheme.colorScheme.inverseOnSurface,shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Glyph(CoachIcon.ROAD,tint=MaterialTheme.colorScheme.inversePrimary)
                Text(if(consent) status else "자동 기록을 시작해 보세요",style=MaterialTheme.typography.titleMedium)
            }
            Text(if(consent) "운전에 집중하세요.\n기록은 맡겨주세요." else "한 번의 동의로,\n매일의 운전을 기록해요.",style=MaterialTheme.typography.headlineMedium)
            Text(if(consent) "주행 중에는 자동으로 기록하고,\n주차 후 필요한 장면만 함께 돌아봐요." else "속도·기어·안전벨트 경고를 이 기기에 저장합니다.\n앱을 닫아도 기록하며, 설정에서 언제든 끌 수 있어요.",style=MaterialTheme.typography.bodyLarge)
            if(!consent) Button(onConsent,Modifier.heightIn(min=60.dp),shape=MaterialTheme.shapes.medium,
                colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.inverseOnSurface,contentColor=MaterialTheme.colorScheme.inverseSurface)) { Text("동의하고 자동 기록 시작") }
            else if(latest!=null) Button({onTrip(latest.id)},Modifier.heightIn(min=60.dp),shape=MaterialTheme.shapes.medium,
                colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.inverseOnSurface,contentColor=MaterialTheme.colorScheme.inverseSurface)) { Text("최근 리포트 보기"); Spacer(Modifier.width(16.dp)); Glyph(CoachIcon.ARROW,tint=MaterialTheme.colorScheme.inverseSurface) }
            Text(source.message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.inversePrimary)
        }
    }
}
@Composable private fun GoalCard(goal: Int,onClick: ()->Unit) {
    Sheet {
        IconTile(EventKind.entries.first { it.lessonId==goal }.icon())
        Muted("이번 운전의 작은 목표")
        Text(lessons.first { it.id==goal }.title,style=MaterialTheme.typography.headlineMedium)
        Text(when(goal) { 3 -> "앞의 흐름을 살피고,\n여유 있게 감속해 보세요."; 5 -> "출발 전에 한 번,\n안전벨트를 확인하세요."; else -> "출발할 때 조금 더\n부드럽게 밟아 보세요." })
        Action("목표 자세히 보기",onClick=onClick)
    }
}
@Composable private fun EmptyState(title: String,description: String) { Sheet {
    IconTile(CoachIcon.ROAD); Text(title,style=MaterialTheme.typography.titleLarge); Muted(description)
} }
@Composable private fun TripBadges(t: Trip) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Badge(tripState(t))
        Badge(if(t.simulated) "테스트 데이터" else "차량 신호",MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun TripRow(t: Trip,count: Int,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                IconTile(CoachIcon.ROAD)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(date(t.startedAt),style=MaterialTheme.typography.titleLarge)
                    Muted("${number(t.distanceMeters/1000)} km   ·   돌아볼 장면 ${count}개")
                }
                Glyph(CoachIcon.ARROW)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TripBadges(t); Text("신호 누락 ${missing(t)}",Modifier.padding(vertical=7.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(t.status=="INTERRUPTED") Muted("마지막 저장 지점까지 보존한 기록이에요.")
        }
    }
}
@Composable private fun <T> CardGrid(values: List<T>,columns: Int,card: @Composable (T)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        values.forEach { Box(Modifier.weight(1f)) { card(it) } }
        repeat(columns-values.size) { Spacer(Modifier.weight(1f)) }
    }
}
@Composable private fun EventSymbol(e: DrivingEvent) {
    val c=MaterialTheme.colorScheme
    val colors=when(kind(e)) { EventKind.RAPID_START -> c.primaryContainer to c.onPrimaryContainer; EventKind.HARD_BRAKING -> c.secondaryContainer to c.onSecondaryContainer; EventKind.SEAT_BELT -> c.tertiaryContainer to c.onTertiaryContainer }
    IconTile(kind(e).icon(),colors.first,colors.second)
}
@Composable private fun observation(e: DrivingEvent) = if(e.kind==EventKind.SEAT_BELT.name) "경고 당시 ${number(e.observed)} km/h" else "${number(e.observed)} m/s² 관측"
@Composable private fun EventCard(e: DrivingEvent,excluded: Boolean,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            EventSymbol(e); Muted(date(e.timeMs,"HH:mm:ss"))
            Text(kind(e).title,style=MaterialTheme.typography.titleLarge)
            Text(observation(e))
            Badge(if(excluded) "통계 제외" else "통계 포함",MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text("장면 살펴보기",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge); Glyph(CoachIcon.ARROW) }
        }
    }
}
@Composable private fun EventRow(e: DrivingEvent,excluded: Boolean,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),shape=MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            EventSymbol(e)
            Column(Modifier.weight(1f)) { Text(kind(e).title,style=MaterialTheme.typography.titleMedium); Muted("${date(e.timeMs,"HH:mm:ss")}${if(excluded) " · 통계 제외" else ""}") }
            Glyph(CoachIcon.ARROW)
        }
    }
}
@Composable private fun LessonCard(lesson: Lesson,selected: Boolean,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(lesson.id.toString().padStart(2,'0'),style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
            Text(lesson.title,style=MaterialTheme.typography.titleLarge)
            Badge(if(selected) "나의 목표" else if(lesson.phase=="P1") "관측 지원" else "학습 전용",
                if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text("읽어보기",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary); Glyph(CoachIcon.ARROW) }
        }
    }
}
@Composable private fun LessonDetail(lesson: Lesson,goal: Int,onGoal: ()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Sheet(color=MaterialTheme.colorScheme.primaryContainer) {
            Badge("안전 지식 ${lesson.id.toString().padStart(2,'0')}",MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.primary)
            Text(lesson.title,style=MaterialTheme.typography.headlineLarge)
            Text(lesson.advice,style=MaterialTheme.typography.titleLarge)
        }
        Sheet {
            Text("이럴 때 떠올려 보세요",style=MaterialTheme.typography.titleLarge)
            Text(lesson.condition)
            Muted(if(lesson.phase=="P1") "차량 신호로 관측 가능한 장면이에요. 실제 지원 여부는 설정의 연결 상태에서 확인하세요." else "미리 읽는 학습 콘텐츠예요. 이 상황의 자동 감지는 데이터 검증 후 제공됩니다.")
            if(lesson.phase=="P1") Action(if(goal==lesson.id) "선택한 개선 목표" else "이번 운전의 개선 목표로 선택",primary=true,onClick=onGoal)
        }
        Sheet {
            Text("참고한 자료",style=MaterialTheme.typography.titleMedium)
            Text(lesson.source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Muted("콘텐츠 검토일 ${lesson.reviewedAt}\n현장의 신호·표지와 실제 상황을 먼저 확인하세요.")
        }
    }
}
@Composable private fun EventDetail(e: DrivingEvent,saved: EventFeedback?,vm: CoachViewModel,onLesson: ()->Unit) {
    var evidence by remember(e.id) { mutableStateOf(emptyList<EventEvidence>()) }
    LaunchedEffect(e.id) { evidence=vm.evidence(e.id) }
    Column(verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            EventSymbol(e); Column { Text(kind(e).title,style=MaterialTheme.typography.headlineMedium); Muted(date(e.timeMs,"M월 d일 · HH:mm:ss")) }
        }
        Sheet {
            Text(observation(e),style=MaterialTheme.typography.headlineMedium)
            if(e.kind==EventKind.SEAT_BELT.name) Muted(e.seat ?: "좌석 정보 없음")
            SpeedChart(evidence,e.timeMs)
            Muted("관측값을 바탕으로 돌아볼 장면을 제안해요. 위법 또는 위험 여부를 확정하지 않아요.")
            Text("${e.quality} · ${e.ruleVersion}\n장면 전 최대 10초, 후 최대 5초의 수신 신호",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Sheet {
            SectionTitle("당시 상황은 어땠나요?","상황 설명을 남겨 나에게 맞는 기록으로 관리하세요.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf("긴급 회피","교통 상황","기록 오류","별도 설명 없음").forEach { reason ->
                    FilterChip(selected=saved?.reason==reason,onClick={vm.saveFeedback(e.id,reason,saved?.includeInStats ?: true)},
                        label={Text(reason)},modifier=Modifier.heightIn(min=60.dp),leadingIcon=if(saved?.reason==reason) ({Glyph(CoachIcon.CHECK)}) else null)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            val include=saved?.includeInStats!=false
            Row(Modifier.fillMaxWidth().toggleable(include,role=Role.Switch,onValueChange={vm.saveFeedback(e.id,saved?.reason ?: "별도 설명 없음",it)}).heightIn(min=64.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Text("개선 통계에 포함",style=MaterialTheme.typography.titleMedium); Muted(if(include) "현재 추이에 반영하고 있어요" else "이 장면은 추이에서 제외했어요") }
                Switch(checked=include,onCheckedChange=null)
            }
            Muted("설명과 통계 포함 여부를 바꿔도 원본 관측은 보존됩니다.")
            Action("관련 안전 지식 보기",primary=true,onClick=onLesson)
        }
    }
}
private data class ChartPoint(val mono: Long,val wall: Long,val speed: Double)
@Composable private fun SpeedChart(evidence: List<EventEvidence>,eventTime: Long) {
    val points=remember(evidence) { evidence.mapNotNull { row -> runCatching {
        val j=JSONObject(row.payload)
        if(!j.getBoolean("valid") || j.isNull("speedMps")) null else ChartPoint(row.monotonicMs,j.getLong("timeMs"),abs(j.getDouble("speedMps"))*3.6)
    }.getOrNull() } }
    if(points.size<2) { Muted("표시할 연속 신호가 충분하지 않아요."); return }
    val color=MaterialTheme.colorScheme.primary
    val grid=MaterialTheme.colorScheme.outlineVariant
    val marker=MaterialTheme.colorScheme.secondary
    val start=points.first().mono
    val span=(points.last().mono-start).coerceAtLeast(1)
    val peak=points.maxOf { it.speed }.coerceAtLeast(1.0)
    val event=points.minBy { abs(it.wall-eventTime) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("속도 변화",style=MaterialTheme.typography.titleLarge)
        Muted("최대 ${number(peak)} km/h · 관측 ${number(span/1000.0)}초")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.height(180.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(number(peak),style=MaterialTheme.typography.bodySmall); Text("0",style=MaterialTheme.typography.bodySmall)
            }
            Canvas(Modifier.weight(1f).height(180.dp).semantics { contentDescription="속도 변화 그래프. 최대 ${number(peak)} km/h. 세로 점선은 장면 감지 시점." }) {
                listOf(0f,.5f,1f).forEach { drawLine(grid,Offset(0f,it*size.height),Offset(size.width,it*size.height),1.dp.toPx()) }
                val x=(event.mono-start).toFloat()/span*size.width
                drawLine(marker,Offset(x,0f),Offset(x,size.height),2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,6f)))
                points.zipWithNext().forEach { (a,b) -> if(b.mono-a.mono<=250) drawLine(color,
                    Offset((a.mono-start).toFloat()/span*size.width,size.height-(a.speed/peak*size.height).toFloat()),
                    Offset((b.mono-start).toFloat()/span*size.width,size.height-(b.speed/peak*size.height).toFloat()),3.dp.toPx()) }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Muted("관측 시작"); Text("점선: 감지 시점",style=MaterialTheme.typography.bodySmall,color=marker); Muted("${number(span/1000.0)}초") }
    }
}
@Composable private fun Trends(trips: List<Trip>,events: List<DrivingEvent>,feedback: List<EventFeedback>,goal: Int) {
    val finished=trips.filter { it.status=="COMPLETE" && !it.simulated }
    val eventKind=EventKind.entries.first { it.lessonId==goal }
    fun trend(group: List<Trip>): Trend {
        val ids=group.map { it.id }.toSet(); val excluded=feedback.filter { !it.includeInStats }.map { it.eventId }.toSet()
        return calculateTrend(group.map { TrendTrip(it.id,it.distanceMeters,it.validMs,it.missingMs) }, events.count { it.tripId in ids && it.kind==eventKind.name && it.id !in excluded })
    }
    val recent=trend(finished.take(5)); val past=trend(finished.drop(5).take(5))
    Sheet {
        Badge("나의 목표 · ${lessons.first { it.id==goal }.title}")
        Metrics(listOf("최근 ${finished.take(5).size}회 · 100 km당" to (recent.ratePer100Km?.let { "${number(it)}회" } ?: "기록 대기"),
            "유효한 신호 비율" to (recent.coverage?.let { "${number(it*100)}%" } ?: "계산 대기")))
        if(recent.enough && past.enough) {
            val delta=recent.ratePer100Km!!-past.ratePer100Km!!
            Text(if(delta==0.0) "이전 5회와 같은 수준이에요" else "이전 5회보다 100 km당 ${number(abs(delta))}회 ${if(delta<0) "줄었어요" else "늘었어요"}",style=MaterialTheme.typography.titleLarge)
        } else {
            Text("아직은 알아가는 중이에요",style=MaterialTheme.typography.titleLarge)
            Muted("변화를 비교하려면 두 구간에 각각 3회·10 km 이상, 유효한 신호 90% 이상이 필요해요.")
        }
        Muted("완료된 차량 신호 기록만 비교합니다. 테스트·중단 기록과 통계에서 제외한 장면은 포함하지 않아요.")
    }
}
