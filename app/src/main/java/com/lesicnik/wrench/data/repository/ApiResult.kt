package com.lesicnik.wrench.data.repository

enum class ApiErrorType { OTHER, NETWORK, AUTHENTICATION }

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val type: ApiErrorType = ApiErrorType.OTHER) : ApiResult<Nothing>()
}

