package net.pantasystem.milktea.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


suspend fun<T> (suspend () -> T).asLoadingStateFlow(): Flow<ResultState<T>> {

    return flow {
        emit(ResultState.Fixed(StateContent.NotExist()))
        emit(ResultState.Loading(StateContent.NotExist()))
        runCatching {
            this@asLoadingStateFlow.invoke()
        }.onSuccess {
            emit(ResultState.Fixed(StateContent.Exist(it)))
        }.onFailure {
            emit(ResultState.Error(StateContent.NotExist(), it))
        }
    }
}