package com.kadaikutty.pos.core.auth

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE companyId = :companyId")
    fun getUsersFlowByCompany(companyId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username OR username = :cleanUsername OR username = '+91' || :cleanUsername OR username = '+91' || :username LIMIT 1")
    suspend fun getUserByUsername(username: String, cleanUsername: String = username.replace("[^0-9]".toRegex(), "").takeLast(10)): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)
}
