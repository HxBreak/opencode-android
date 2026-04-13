package me.xiaok.opencode.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

class CoroutineTestRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : TestRule {
    val testScope = TestScope(testDispatcher)
    private val timeoutRule = Timeout(timeoutMs, TimeUnit.MILLISECONDS)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun apply(base: Statement, description: org.junit.runner.Description): Statement {
        val withTimeout = timeoutRule.apply(base, description)
        return object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    withTimeout.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

class TimeoutRule(
    timeoutMs: Long = CoroutineTestRule.DEFAULT_TIMEOUT_MS,
) : TestRule {
    private val delegate = Timeout(timeoutMs, TimeUnit.MILLISECONDS)

    override fun apply(base: Statement, description: org.junit.runner.Description): Statement {
        return delegate.apply(base, description)
    }
}
