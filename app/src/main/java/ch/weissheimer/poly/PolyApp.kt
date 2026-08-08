package ch.weissheimer.poly

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.room.Room
import ch.weissheimer.poly.data.AnnotationRepository
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.RecentsRepository
import ch.weissheimer.poly.data.db.PolyDatabase
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

class PolyApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil image loader with animated GIF support. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
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

    val annotationRepository: AnnotationRepository by lazy { AnnotationRepository(database.annotationDao()) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PolyApp).container
