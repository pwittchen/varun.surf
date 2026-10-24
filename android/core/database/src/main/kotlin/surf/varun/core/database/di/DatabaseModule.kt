package surf.varun.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import surf.varun.core.database.VarunDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): VarunDatabase =
        Room.databaseBuilder(context, VarunDatabase::class.java, "varun.db").build()

    @Provides
    fun spotDao(db: VarunDatabase) = db.spotDao()

    @Provides
    fun favouriteDao(db: VarunDatabase) = db.favouriteDao()

    @Provides
    fun alertDao(db: VarunDatabase) = db.alertDao()

    @Provides
    fun recentDao(db: VarunDatabase) = db.recentDao()
}
