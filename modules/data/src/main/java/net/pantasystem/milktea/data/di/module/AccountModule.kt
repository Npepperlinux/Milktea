package net.pantasystem.milktea.data.di.module

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.pantasystem.milktea.common.getPreferences
import net.pantasystem.milktea.data.infrastructure.DataBase
import net.pantasystem.milktea.data.infrastructure.account.AuthImpl
import net.pantasystem.milktea.data.infrastructure.account.ClientIdRepositoryImpl
import net.pantasystem.milktea.data.infrastructure.account.db.MediatorAccountRepository
import net.pantasystem.milktea.data.infrastructure.account.db.RoomAccountRepository
import net.pantasystem.milktea.model.account.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun accountRepository(
        @ApplicationContext context: Context,
        database: DataBase,
    ): AccountRepository {
        val preferences = context.getPreferences()
        val roomAccountRepository = RoomAccountRepository(database, preferences, database.accountDAO(), database.pageDAO())
        return MediatorAccountRepository(roomAccountRepository)
    }


    @Singleton
    @Provides
    fun provideClientIdRepository(
        @ApplicationContext context: Context,
    ) : ClientIdRepository {
        return ClientIdRepositoryImpl(context.getPreferences())
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun provideAuth(impl: AuthImpl): Auth

    @Binds
    @Singleton
    abstract fun provideAuthById(impl: AuthImpl): AuthById

    @Binds
    @Singleton
    abstract fun provideGetAccount(impl: AuthImpl): GetAccount
}