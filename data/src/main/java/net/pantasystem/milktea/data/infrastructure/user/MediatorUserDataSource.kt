package net.pantasystem.milktea.data.infrastructure.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.data.infrastructure.user.db.*
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.UserDataSource
import net.pantasystem.milktea.model.user.UserNotFoundException
import javax.inject.Inject

class MediatorUserDataSource @Inject constructor(
    private val userDao: UserDao,
    private val inMem: InMemoryUserDataSource,

) : UserDataSource {
    override fun addEventListener(listener: UserDataSource.Listener) {
        inMem.addEventListener(listener)
    }

    override fun removeEventListener(listener: UserDataSource.Listener) {
        inMem.removeEventListener(listener)
    }

    override suspend fun get(userId: User.Id): User {
        return withContext(Dispatchers.IO) {
            runCatching {
                inMem.get(userId)
            }.getOrNull()
                ?: (userDao.get(userId.accountId, userId.id)?.toModel()?.also {
                    inMem.add(it)
                } ?: throw UserNotFoundException(userId))
        }
    }

    override suspend fun get(accountId: Long, userName: String, host: String?): User {
        return withContext(Dispatchers.IO) {
            runCatching {
                inMem.get(accountId, userName, host)
            }.getOrNull()
                ?: (if (host == null) {
                    userDao.getByUserName(accountId, userName)
                } else {
                    userDao.getByUserName(accountId, userName, host)
                })?.toModel()?.also {
                    inMem.add(it)
                } ?: throw UserNotFoundException(userName = userName, host = host, userId = null)
        }
    }

    override suspend fun getIn(accountId: Long, serverIds: List<String>): List<User> {

        return withContext(Dispatchers.IO) {

            userDao.getInServerIds(accountId, serverIds).map {
                it.toModel()
            }.also {
                inMem.addAll(it)
            }
        }
    }

    override suspend fun add(user: User): AddResult {
        return withContext(Dispatchers.IO) {
            val existsUserInMemory = runCatching {
                inMem.get(user.id)
            }.getOrNull()
            if (existsUserInMemory == user) {
                return@withContext AddResult.Canceled
            }

            val result = inMem.add(user)

            if (result == AddResult.Canceled) {
                return@withContext result
            }
            val newRecord = UserRecord(
                accountId = user.id.accountId,
                serverId = user.id.id,
                avatarUrl = user.avatarUrl,
                host = user.host,
                isBot = user.isBot,
                isCat = user.isCat,
                isSameHost = user.isSameHost,
                name = user.name,
                userName = user.userName,
            )
            when (result) {
                AddResult.Canceled -> {
                    return@withContext result
                }
                else -> {
                    val record = userDao.get(user.id.accountId, user.id.id)
                    val dbId = if (record == null) {
                        userDao.insert(newRecord)
                    } else {
                        userDao.update(newRecord.copy(id = record.user.id))
                        record.user.id
                    }

                    // NOTE: 新たに追加される予定のオブジェクトと既にキャッシュしているオブジェクトの絵文字リストを比較している
                    // NOTE: 比較した上で同一でなければキャッシュの更新処理を行う
                    if (record?.toModel()?.emojis?.toSet() != user.emojis.toSet()) {
                        // NOTE: 既にキャッシュに存在していた場合一度全て剥がす
                        if (record != null) {
                            userDao.detachAllUserEmojis(dbId)
                        }
                        userDao.insertEmojis(
                            user.emojis.map {
                                UserEmojiRecord(
                                    userId = dbId,
                                    name = it.name,
                                    uri = it.uri,
                                    url = it.url,
                                )
                            }
                        )
                    }

                    if (user is User.Detail) {
                        userDao.insert(
                            UserDetailedStateRecord(
                                bannerUrl = user.bannerUrl,
                                isMuting = user.isMuting,
                                isBlocking = user.isBlocking,
                                isLocked = user.isLocked,
                                isFollower = user.isFollower,
                                isFollowing = user.isFollowing,
                                description = user.description,
                                followersCount = user.followersCount,
                                followingCount = user.followingCount,
                                hasPendingFollowRequestToYou = user.hasPendingFollowRequestToYou,
                                hasPendingFollowRequestFromYou = user.hasPendingFollowRequestFromYou,
                                hostLower = user.hostLower,
                                notesCount = user.notesCount,
                                url = user.url,
                                userId = dbId
                            )
                        )

                        // NOTE: 更新の必要性を判定
                        if ((record?.toModel() as? User.Detail?)?.pinnedNoteIds?.toSet() != user.pinnedNoteIds?.toSet()) {
                            // NOTE: 更新系の場合は一度削除する
                            if (record != null) {
                                userDao.detachAllPinnedNoteIds(dbId)
                            }

                            if (!user.pinnedNoteIds.isNullOrEmpty()) {
                                userDao.insertPinnedNoteIds(user.pinnedNoteIds!!.map {
                                    PinnedNoteIdRecord(it.noteId, userId = dbId, 0L)
                                })
                            }

                        }
                    }
                }

            }
            return@withContext result
        }


    }

    override suspend fun addAll(users: List<User>): List<AddResult> {
        return users.map {
            add(it)
        }
    }

    override suspend fun remove(user: User): Boolean {
        return runCatching {
            inMem.remove(user)
            userDao.delete(user.id.accountId, user.id.id)
            true
        }.getOrElse {
            false
        }
    }


    override fun observeIn(accountId: Long, serverIds: List<String>): Flow<List<User>> {
        return userDao.observeInServerIds(accountId, serverIds).map { list ->
            list.map { user ->
                user.toModel()
            }
        }.flowOn(Dispatchers.IO).onEach {
            inMem.addAll(it)
        }.distinctUntilChanged()
    }

    override fun observe(userId: User.Id): Flow<User> {
        return userDao.observe(userId.accountId, userId.id).mapNotNull {
            it?.toModel()
        }.onEach {
            inMem.add(it)
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observe(acct: String): Flow<User> {
        val userNameAndHost = acct.split("@").filter { it.isNotBlank() }
        val userName = userNameAndHost[0]
        val host = userNameAndHost.getOrNull(1)
        return userDao.let {
            if(host == null) {
                it.observeByUserName(userName).filterNotNull()
            } else {
                it.observeByUserName(userName, host).filterNotNull()
            }
        }.map {
            it.toModel()
        }.onEach {
            inMem.add(it)
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observe(userName: String, host: String?, accountId: Long?): Flow<User?> {
        return inMem.observe(userName, host, accountId).distinctUntilChanged()
    }

    override suspend fun searchByName(accountId: Long, name: String): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.searchByName(accountId, "%$name").map {
                it.toModel()
            }.also {
                inMem.addAll(it)
            }
        }
    }


}