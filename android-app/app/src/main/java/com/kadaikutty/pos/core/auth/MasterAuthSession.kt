package com.kadaikutty.pos.core.auth

/**
 * In-memory only (process lifetime): the Super Master Control session is never persisted to disk,
 * so a process death always requires re-entering the master PIN. [sessionId] is the device session
 * issued by POST /sessions/register, required as X-Session-Id on every subsequent master API call.
 */
object MasterAuthSession {
    @Volatile var accessToken: String? = null
        private set
    @Volatile var sessionId: String? = null
        private set

    fun save(token: String) { accessToken = token }
    fun saveSession(id: String) { sessionId = id }
    fun clear() { accessToken = null; sessionId = null }
}
