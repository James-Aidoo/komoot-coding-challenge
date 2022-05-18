package com.questdev.komootchallenge.di

import com.questdev.komootchallenge.data.Repository
import com.questdev.komootchallenge.data.remote.RemoteDataClient
import com.questdev.komootchallenge.data.remote.RemoteDataService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {
    @Provides
    fun provides(remoteDataService: RemoteDataService) = Repository(remoteDataService)
}

@Module
@InstallIn(SingletonComponent::class)
class RemoteDataServiceModule {
    @Provides
    fun provides(): RemoteDataService =
        RemoteDataClient.retrofit.create(RemoteDataService::class.java)
}
