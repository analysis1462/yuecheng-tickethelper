package com.yuecheng.ticket.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * runCatching 的挂起安全版:界面/Worker 里包裹挂起调用请用它替代 runCatching。
 * CancellationException 原样重抛(协程取消不被吞,不会在页面销毁后继续走 onFailure),
 * 其余异常照常包成 Result.failure。
 */
suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
