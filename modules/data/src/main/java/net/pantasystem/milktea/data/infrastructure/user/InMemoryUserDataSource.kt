package net.pantasystem.milktea.data.infrastructure.user


import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.UserDataSource
import net.pantasystem.milktea.model.user.UserNotFoundException
import net.pantasystem.milktea.model.user.UsersState
import javax.inject.Inject

class InMemoryUserDataSource @Inject constructor() : UserDataSource {

    private var userMap = mapOf<User.Id, User>()

    private val usersLock = Mutex()

    private val _state = MutableStateFlow(UsersState())
    val state: StateFlow<UsersState>
        get() = _state




    override suspend fun add(user: User): Result<AddResult> = runCatching {
        return@runCatching createOrUpdate(user).also {
            publish()
        }

    }

    override suspend fun addAll(users: List<User>): Result<List<AddResult>> = runCatching {
        users.map {
            add(it).getOrElse {
                AddResult.Canceled
            }
        }
    }

    override suspend fun get(userId: User.Id): Result<User> = runCatching {
        usersLock.withLock {
            userMap[userId]
        } ?: throw UserNotFoundException(userId)
    }

    override suspend fun getIn(accountId: Long, serverIds: List<String>): Result<List<User>> {
        val userIds = serverIds.map {
            User.Id(accountId, it)
        }
        return Result.success(usersLock.withLock {
            userIds.mapNotNull {
                userMap[it]
            }
        })
    }

    override suspend fun get(accountId: Long, userName: String, host: String?): Result<User> = runCatching {
        usersLock.withLock {
            userMap.filterKeys {
                it.accountId == accountId
            }.map {
                it.value
            }.firstOrNull {
                it.userName == userName && (it.host == host || host.isNullOrBlank())
            } ?: throw UserNotFoundException(null)
        }
    }

    override suspend fun remove(user: User): Result<Boolean> = runCatching {
        usersLock.withLock {
            val map = userMap.toMutableMap()
            val result = map.remove(user.id)
            userMap = map
            result
        }?.also {
            publish()
        } != null


    }

    private suspend fun createOrUpdate(user: User): AddResult {
        usersLock.withLock {
            val u = userMap[user.id]
            if (u == null) {
                userMap = userMap.toMutableMap().also { map ->
                    map[user.id] = user
                }
                return AddResult.Created
            }
            when {
                user is User.Detail -> {
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = user
                    }
                }
                u is User.Detail -> {
                    // RepositoryのUserがDetailで与えられたUserがSimpleの時Simpleと一致する部分のみ更新する
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = u.copy(
                            name = user.name,
                            userName = user.userName,
                            avatarUrl = user.avatarUrl,
                            emojis = user.emojis,
                            isCat = user.isCat,
                            isBot = user.isBot,
                            host = user.host
                        )
                    }
                }
                else -> {
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = user
                    }
                }
            }

            return AddResult.Updated
        }
    }

    fun all(): List<User> {
        return userMap.values.toList()
    }

    override fun observe(userId: User.Id): Flow<User> {
        return _state.map {
            it.get(userId)
        }.filterNotNull()
    }

    override fun observeIn(accountId: Long, serverIds: List<String>): Flow<List<User>> {
        val userIds = serverIds.map {
            User.Id(accountId, it)
        }
        return _state.map { state ->
            userIds.mapNotNull {
                state.get(it)
            }
        }
    }

    override fun observe(acct: String): Flow<User> {
        return _state.mapNotNull {
            it.get(acct)
        }
    }

    override suspend fun searchByName(accountId: Long, name: String): List<User> {
        return all().filter {
            it.id.accountId == accountId
        }.filter {
            it.displayName.startsWith(name)
        }
    }

    override fun observe(userName: String, host: String?, accountId: Long?): Flow<User?> {
        return state.map { state ->
            state.usersMap.values.filter { user ->
                accountId == null || accountId == user.id.accountId
            }.firstOrNull {
                it.userName == userName && it.host == host
            }
        }
    }

    private fun publish() {
        _state.value = _state.value.copy(
            usersMap = userMap
        )
    }
}