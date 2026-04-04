package me.xiaok.opencode.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    // CredentialStore has @Inject constructor with @ApplicationContext.
    // Hilt provides it automatically — no explicit @Provides needed.
}
