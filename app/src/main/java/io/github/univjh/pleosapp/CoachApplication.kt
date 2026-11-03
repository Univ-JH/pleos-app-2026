package io.github.univjh.pleosapp

import android.app.Application
import androidx.room.Room
import io.github.univjh.pleosapp.data.*
import io.github.univjh.pleosapp.vehicle.PleosSignalSource
import kotlinx.coroutines.flow.MutableStateFlow

class CoachApplication : Application() {
    @Volatile var visible = false
    val recordingMutex = kotlinx.coroutines.sync.Mutex()
    val database by lazy { Room.databaseBuilder(this, CoachDatabase::class.java, "driving-coach.db").build() }
    val repository by lazy { CoachRepository(database) }
    val settings by lazy { CoachSettings(this) }
    val source by lazy { PleosSignalSource(this) }
    val recorderStatus = MutableStateFlow("자동 기록 꺼짐")
    val serviceRunning = MutableStateFlow(false)
}
