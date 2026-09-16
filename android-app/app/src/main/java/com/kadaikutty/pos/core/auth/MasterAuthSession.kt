package com.kadaikutty.pos.core.auth

object MasterAuthSession {
    @Volatile var accessToken: String? = null
        private set

    fun save(token: String) { accessToken = token }
    fun clear() { accessToken = null }
}

