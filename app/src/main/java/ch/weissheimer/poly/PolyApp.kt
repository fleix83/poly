package ch.weissheimer.poly

import android.app.Application
import android.content.Context
import androidx.room.Room
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.RecentsRepository
import ch.weissheimer.poly.data.db.PolyDatabase

class PolyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: one lazily created instance per app-scoped dependency. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: PolyDatabase by lazy {
        Room.databaseBuilder(appContext, PolyDatabase::class.java, "poly.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val fileRepository: FileRepository by lazy { FileRepository(appContext) }

    val recentsRepository: RecentsRepository by lazy { RecentsRepository(database.recentFileDao()) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PolyApp).container
