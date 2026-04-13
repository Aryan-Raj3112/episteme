// src\oss
package com.aryan.reader

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


class AuthRepository(private val applicationContext: Context) {

    fun getSignedInUser(): UserData? {
        return null
    }

    suspend fun signIn(activityContext: Context): UserData? {
        return null
    }

    fun signOut() {
    }

    fun observeAuthState(): Flow<UserData?> {
        return flowOf(null)
    }

    suspend fun getIdToken(): String? = null
}