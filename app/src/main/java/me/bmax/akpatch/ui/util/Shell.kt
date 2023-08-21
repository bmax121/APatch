package me.bmax.akpatch.ui.util

/*
 * Copyright (C) 2021 Jared Rummler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern


//https://github.com/jaredrummler/KtSh

/** Environment variable. */
typealias Variable = String
/** Environment variable value. */
typealias Value = String
/** A [Map] for the environment variables used in the shell. */
typealias EnvironmentMap = Map<Variable, Value>

/**
 * A shell starts a [Process] with the provided shell and additional/optional environment variables.
 * The shell handles maintaining the [Process] and reads standard output and standard error streams,
 * returning stdout, stderr, and the last exit code as a [Command.Result] when a command is complete.
 *
 * Example usage:
 *
 *     val sh = Shell("sh")
 *     val result = sh.run("echo 'Hello, World!'")
 *     assert(result.isSuccess)
 *     assert(result.stdout() == "Hello, World")
 *
 * @property path        The path to the shell to start.
 * @property environment Map of all environment variables to include with the system environment.
 *                       Default value is an empty map.
 * @throws Shell.NotFoundException If the shell cannot be opened this runtime exception is thrown.
 * @author Jared Rummler (jaredrummler@gmail.com)
 * @since 05-05-2021
 */
class Shell @Throws(NotFoundException::class) @JvmOverloads constructor(
    val path: String,
    val environment: EnvironmentMap = emptyMap()
) {

    /**
     * Construct a new [Shell] with optional environment variable arguments as a [Pair].
     *
     * @param shell       The path to the shell to start.
     * @param environment varargs of all environment variables as a [Pair] which are included
     *                    with the system environment.
     */
    constructor(shell: String, vararg environment: Pair<Variable, Value>) :
            this(shell, environment.toEnvironmentMap())

    /**
     * Construct a new [Shell] with optional environment variable arguments as an array.
     *
     * @param shell       The path to the shell to start.
     * @param environment varargs of all environment variables as a [Pair] which are included
     *                    with the system environment.
     */
    constructor(shell: String, environment: Array<String>) :
            this(shell, environment.toEnvironmentMap())

    /**
     * Get the current state of the shell
     */
    var state: State = State.Idle
        private set

    private val onResultListeners = mutableSetOf<OnCommandResultListener>()
    private val onStdOutListeners = mutableSetOf<OnLineListener>()

    private val onStdErrListeners = mutableSetOf<OnLineListener>()
    private val stdin: StandardInputStream
    private val stdoutReader: StreamReader
    private val stderrReader: StreamReader
    private var watchdog: Watchdog? = null

    private val process: Process

    init {
        try {
            process = runWithEnv(path, environment)
            stdin = StandardInputStream(process.outputStream)
            stdoutReader = StreamReader.createAndStart(THREAD_NAME_STDOUT, process.inputStream)
            stderrReader = StreamReader.createAndStart(THREAD_NAME_STDERR, process.errorStream)
        } catch (cause: Exception) {
            throw NotFoundException(String.format(EXCEPTION_SHELL_CANNOT_OPEN, path), cause)
        }
    }

    /**
     * Add a listener that will be invoked each time a command finishes.
     *
     * @param listener The listener to receive callbacks when commands finish executing.
     * @return This shell instance for chaining calls.
     */
    fun addOnCommandResultListener(listener: OnCommandResultListener) = apply {
        onResultListeners.add(listener)
    }

    /**
     * Remove a listener previously added to stop receiving callbacks when commands finish.
     *
     * @param listener The listener registered via [addOnCommandResultListener].
     * @return This shell instance for chaining calls.
     */
    fun removeOnCommandResultListener(listener: OnCommandResultListener) = apply {
        onResultListeners.remove(listener)
    }

    /**
     * Add a listener that will be invoked each time the STDOUT stream reads a new line.
     *
     * @param listener The listener to receive callbacks when the STDOUT stream reads a line.
     * @return This shell instance for chaining calls.
     */
    fun addOnStdoutLineListener(listener: OnLineListener) = apply {
        onStdOutListeners.add(listener)
    }

    /**
     * Remove a listener previously added to stop receiving callbacks for STDOUT read lines.
     *
     * @param listener The listener registered via [addOnStdoutLineListener].
     * @return This shell instance for chaining calls.
     */
    fun removeOnStdoutLineListener(listener: OnLineListener) = apply {
        onStdOutListeners.remove(listener)
    }

    /**
     * Add a listener that will be invoked each time the STDERR stream reads a new line.
     *
     * @param listener The listener to receive callbacks when the STDERR stream reads a line.
     * @return This shell instance for chaining calls.
     */
    fun addOnStderrLineListener(listener: OnLineListener) = apply {
        onStdErrListeners.add(listener)
    }

    /**
     * Remove a listener previously added to stop receiving callbacks for STDERR read lines.
     *
     * @param listener The listener registered via [addOnStderrLineListener].
     * @return This shell instance for chaining calls.
     */
    fun removeOnStderrLineListener(listener: OnLineListener) = apply {
        onStdErrListeners.remove(listener)
    }

    /**
     * Run a command in the current shell and return its [result][Command.Result].
     *
     * @param command The command to execute.
     * @param config The [options][Command.Config] to set when running the command.
     * @return The [result][Command.Result] containing stdout, stderr, status of running the command.
     * @throws ClosedException if the shell was closed prior to running the command.
     * @see shutdown
     * @see run
     */
    @Throws(ClosedException::class)
    @Synchronized
    fun run(
        command: String,
        config: Command.Config.Builder.() -> Unit,
    ) = run(command, Command.Config.Builder().apply(config).create())

    /**
     * Run a command in the current shell and return its [result][Command.Result].
     *
     * @param command The command to execute.
     * @param config The [options][Command.Config] to set when running the command.
     * @return The [result][Command.Result] containing stdout, stderr, status of running the command.
     * @throws ClosedException if the shell was closed prior to running the command.
     * @see shutdown
     * @see run
     */
    @Throws(ClosedException::class)
    @Synchronized
    @JvmOverloads
    fun run(
        command: String,
        config: Command.Config = Command.Config.default(),
    ): Command.Result {
        // If the shell is shutdown, throw a ShellClosedException.
        if (state == State.Shutdown) throw ClosedException(EXCEPTION_SHELL_SHUTDOWN)

        val stdout = Collections.synchronizedList(mutableListOf<String>())
        val stderr = Collections.synchronizedList(mutableListOf<String>())

        val watchdog = Watchdog().also { watchdog = it }
        var exitCode = Command.Status.INVALID
        val uuid = config.uuid

        val onComplete = { marker: Command.Marker ->
            when (marker.uuid) {
                uuid ->
                    try { // Reached the end of reading the stream for the command.
                        if (marker.status != Command.Status.INVALID) {
                            exitCode = marker.status
                        }
                    } finally {
                        watchdog.signal()
                    }
            }
        }

        val lock = ReentrantLock()
        val output = Collections.synchronizedList(mutableListOf<String>())

        // Function to process stderr and stdout streams.
        fun onLine(
            buffer: MutableList<String>,
            listeners: Set<OnLineListener>,
            onLine: (line: String) -> Unit,
        ) = { line: String ->
            try {
                lock.lock()
                if (config.notify) {
                    listeners.forEach { listener -> listener.onLine(line) }
                }
                buffer.add(line)
                output.add(line)
                onLine(line)
            } finally {
                lock.unlock()
            }
        }

        stdoutReader.onComplete = onComplete
        stderrReader.onComplete = onComplete
        stdoutReader.onReadLine = onLine(stdout, onStdOutListeners, config.onStdOut)
        stderrReader.onReadLine = when (config.redirectStdErr) {
            true -> onLine(stdout, onStdOutListeners, config.onStdOut)
            else -> onLine(stderr, onStdErrListeners, config.onStdErr)
        }

        val startTime = System.currentTimeMillis()
        try {
            state = State.Running
            // Write the command and command end marker to stdin.
            write(command, "echo '$uuid' $?", "echo '$uuid' >&2")
            // Wait for the result with a timeout, if provided.
            if (!watchdog.await(config.timeout)) {
                exitCode = Command.Status.TIMEOUT
                config.onTimeout()
            }
        } catch (e: InterruptedException) {
            exitCode = Command.Status.TERMINATED
            config.onCancelled()
        } finally {
            this.watchdog = null
            state = State.Idle
        }

        if (exitCode != Command.Status.SUCCESS) {
            // Exit with the error code in a subshell
            // This is necessary because we send commands to signal a command was completed
            write("$(exit $exitCode)")
        }

        // Create the result from running the command.
        val result = Command.Result.create(
            uuid,
            command,
            stdout,
            stderr,
            output,
            exitCode,
            startTime
        )

        if (config.notify) {
            onResultListeners.forEach { listener ->
                listener.onResult(result)
            }
        }

        return result
    }

    /**
     * Check if the shell is idle.
     *
     * @return True if the shell is open but not running any commands.
     */
    fun isIdle() = state is State.Idle

    /**
     * Check if the shell is running a command.
     *
     * @return True if the shell is executing a command.
     */
    fun isRunning() = state is State.Running

    /**
     * Check if the shell is shutdown.
     *
     * @return True if the shell is closed.
     * @see shutdown
     */
    fun isShutdown() = state is State.Shutdown

    /**
     * Check if the shell is alive and able to execute commands.
     *
     * @return True if the shell is running or idle.
     */
    fun isAlive() = try {
        process.exitValue(); false
    } catch (e: IllegalThreadStateException) {
        true
    }

    /**
     * Interrupt waiting for a command to complete.
     */
    fun interrupt() {
        watchdog?.abort()
    }

    /**
     * Shutdown the shell instance. After a shell is shutdown it can no longer execute commands
     * and should be garbage collected.
     */
    @Synchronized
    fun shutdown() {
        try {
            write("exit")
            process.waitFor()
            stdin.closeQuietly()
            onStdOutListeners.clear()
            onStdErrListeners.clear()
            stdoutReader.join()
            stderrReader.join()
            process.destroy()
        } catch (ignored: IOException) {
        } finally {
            state = State.Shutdown
        }
    }

    private fun write(vararg commands: String) = try {
        commands.forEach { command -> stdin.write(command) }
        stdin.flush()
    } catch (ignored: IOException) {
    }

    private fun DataOutputStream.closeQuietly() = try {
        close()
    } catch (ignored: IOException) {
    }

    /**
     * Contains data classes used for running commands in a [Shell].
     *
     * @see Command.Result
     * @see Command.Config
     * @see Command.Status
     */
    object Command {

        /**
         * The result of running a command in a shell.
         *
         * @property stdout A list of lines read from the standard input stream.
         * @property stderr A list of lines read from the standard error stream.
         * @property exitCode The status code of running the command.
         * @property details Additional command result details.
         */
        data class Result(
            val stdout: List<String>,
            val stderr: List<String>,
            val output: List<String>,
            val exitCode: Int,
            val details: Details
        ) {

            /**
             * True when the exit code is equal to 0.
             */
            val isSuccess: Boolean get() = exitCode == Status.SUCCESS

            /**
             * Get [stdout] and [stderr] as a string, separated by new lines.
             *
             * @return The output of running the command in a shell.
             */
            fun output(): String = output.joinToString("\n")

            /**
             * Get [stdout] as a string, separated by new lines.
             *
             * @return The standard ouput string.
             */
            fun stdout(): String = stdout.joinToString("\n")

            /**
             * Get [stdout] as a string, separated by new lines.
             *
             * @return The standard ouput string.
             */
            fun stderr(): String = stderr.joinToString("\n")

            /**
             * Additional details pertaining to running a command in a shell.
             *
             * @property uuid      The unique identifier associated with the command.
             * @property command   The command sent to the shell to execute.
             * @property startTime The time—in milliseconds since January 1, 1970, 00:00:00 GMT—when
             *                     the command started execution.
             * @property endTime   The time—in milliseconds since January 1, 1970, 00:00:00 GMT—when
             *                     the command completed execution.
             * @property elapsed   The number of milliseconds it took to execute the command.
             */
            data class Details internal constructor(
                val uuid: UUID,
                val command: String,
                val startTime: Long,
                val endTime: Long,
                val elapsed: Long = endTime - startTime
            )

            companion object {
                internal fun create(
                    uuid: UUID,
                    command: String,
                    stdout: List<String>,
                    stderr: List<String>,
                    output: List<String>,
                    exitCode: Int,
                    startTime: Long,
                    endTime: Long = System.currentTimeMillis(),
                ) = Result(
                    stdout,
                    stderr,
                    output,
                    exitCode,
                    Details(uuid, command, startTime, endTime)
                )
            }
        }

        /**
         * Optional configuration settings when running a command in a [shell][Shell].
         *
         * @property uuid The unique identifier associated with the command.
         * @property redirectStdErr True to redirect STDERR to STDOUT.
         * @property onStdOut Callback that is invoked when reading a line from stdout.
         * @property onStdErr Callback that is invoked when reading a line from stderr.
         * @property onCancelled Callback that is invoked when the command is interrupted.
         * @property onTimeout Callback that is invoked when the command timed-out.
         * @property timeout The time to wait before killing the command.
         * @property notify True to notify any [OnLineListener] and [OnCommandResultListener] of the command.
         */
        class Config private constructor(
            val uuid: UUID = UUID.randomUUID(),
            val redirectStdErr: Boolean = false,
            val onStdOut: (line: String) -> Unit = {},
            val onStdErr: (line: String) -> Unit = {},
            val onCancelled: () -> Unit = {},
            val onTimeout: () -> Unit = {},
            val timeout: Timeout? = null,
            val notify: Boolean = true
        ) {

            /**
             * Optional configuration settings when running a command in a [shell][Shell].
             *
             * @property uuid The unique identifier associated with the command.
             * @property redirectErrorStream True to redirect STDERR to STDOUT.
             * @property onStdOut Callback that is invoked when reading a line from stdout.
             * @property onStdErr Callback that is invoked when reading a line from stderr.
             * @property onCancelled Callback that is invoked when the command is interrupted.
             * @property onTimeout Callback that is invoked when the command timed-out.
             * @property timeout The time to wait before killing the command.
             * @property notify True to notify any [OnLineListener] and [OnCommandResultListener] of the command.
             */
            class Builder {
                var uuid: UUID = UUID.randomUUID()
                var redirectErrorStream = false
                var onStdOut: (line: String) -> Unit = {}
                var onStdErr: (line: String) -> Unit = {}
                var onCancelled: () -> Unit = {}
                var onTimeout: () -> Unit = {}
                var timeout: Timeout? = null
                var notify = true

                /**
                 * Create the [Config] from this builder.
                 *
                 * @return A new [Config] for a command.
                 */
                fun create() =
                    Config(
                        uuid,
                        redirectErrorStream,
                        onStdOut,
                        onStdErr,
                        onCancelled,
                        onTimeout,
                        timeout,
                        notify
                    )
            }

            companion object {

                /**
                 * The default configuration for running a command in a shell.
                 *
                 * @return The default config.
                 */
                fun default(): Config = Builder().create()

                /**
                 * Config that doesn't invoke callbacks for line and command complete listeners.
                 */
                fun silent(): Config = Builder().apply { notify = false }.create()
            }
        }

        /**
         * The command marker to process standard input/error streams.
         *
         * @property uuid The unique ID for a command.
         * @property status the exit code for the last run command.
         */
        internal data class Marker(val uuid: UUID, val status: Int)

        /** Exit codes */
        object Status {
            /** OK exit code value */
            const val SUCCESS = 0

            /** Command timeout exit status */
            const val TIMEOUT = 124

            /** Command failed exit status */
            const val COMMAND_FAILED = 125

            /** Command not executable exit status */
            const val NOT_EXECUTABLE = 126

            /** Command not found exit status */
            const val NOT_FOUND = 127

            /** Command terminated exit status. */
            const val TERMINATED = 128 + 30
            internal const val INVALID = 0x100
        }
    }

    /**
     * Interface to receive a callback when reading a line from standard output/error streams.
     */
    interface OnLineListener {

        /**
         * Called when a line was read from standard output/error streams
         *
         * @param line The string that was read.
         */
        fun onLine(line: String)
    }

    /**
     * Interface to receive a callback when a command completes.
     */
    interface OnCommandResultListener {

        /**
         * Called when a command finishes running.
         *
         * @param result The result of running the command in a shell.
         */
        fun onResult(result: Command.Result)
    }

    /**
     * A timeout used when running a command in a shell.
     *
     * @property value The value of the time based on the [unit].
     * @property unit The time unit for the [value].
     */
    data class Timeout(val value: Long, val unit: TimeUnit)

    /**
     * The exception thrown when a command is passed to a closed shell.
     */
    class ClosedException(message: String) : IOException(message)

    /**
     * The exception thrown when the shell could not be opened.
     */
    class NotFoundException(message: String, cause: Throwable) : RuntimeException(message, cause)

    /**
     * Represents the possible states of the shell.
     */
    sealed class State {
        /** The shell is idle; no commands are in progress. */
        object Idle : State()

        /** The shell is currently running a command. */
        object Running : State()

        /** The shell has been shutdown. */
        object Shutdown : State()
    }

    /**
     * A class to cause the current thread to wait until a command completes or is aborted.
     */
    private class Watchdog : CountDownLatch(STREAM_READER_COUNT) {

        private var aborted = false

        /**
         * Releases the thread immediately instead of waiting for [signal] to be invoked twice.
         */
        fun abort() {
            if (count == 0L) return
            aborted = true
            while (count > 0) countDown()
        }

        /**
         * Signal that either standard output or standard input streams are finished processing.
         */
        fun signal() = countDown()

        /**
         * Causes the current thread to wait until [signal] is called twice.
         *
         * @param timeout The maximum time to wait before [AbortedException] is thrown.
         * @throws AbortedException if the timeout completes before [signal] is called twice
         *                          or if the thread is interrupted.
         */
        @Throws(AbortedException::class)
        fun await(timeout: Timeout?): Boolean {
            return when (timeout) {
                null -> {
                    await(); true
                }
                else -> await(timeout.value, timeout.unit)
            }
        }

        override fun await() = super.await().also {
            if (aborted) throw AbortedException()
        }

        override fun await(timeout: Long, unit: TimeUnit) = super.await(timeout, unit).also {
            if (aborted) throw AbortedException()
        }

        companion object {
            /**
             * The number of times [signal] should be called to release the latch.
             */
            private const val STREAM_READER_COUNT = 2

            /**
             * The exception thrown when [abort] is called and the [CountDownLatch] has not finished
             */
            class AbortedException : InterruptedException()
        }
    }

    /**
     * The [OutputStream] for writing commands to the shell.
     */
    private class StandardInputStream(stream: OutputStream) : DataOutputStream(stream) {

        /**
         * The helper function to write commands to the stream with an appended new line character.
         *
         * @param command The command to write.
         */
        fun write(command: String) = write("$command\n".toByteArray(Charsets.UTF_8))
    }

    /**
     * A thread that parses the standard/error streams for the shell.
     *
     * @param name The name of the stream. One of: [THREAD_NAME_STDOUT], [THREAD_NAME_STDERR]
     * @param stream Either the [Process.getInputStream] or [Process.getErrorStream]
     */
    private class StreamReader private constructor(
        name: String,
        private val stream: InputStream
    ) : Thread(name) {

        /**
         * The lambda that is invoked when a line is read from the stream.
         */
        var onReadLine: (line: String) -> Unit = {}

        /**
         * The lambda that is invoked when a command completes.
         */
        var onComplete: (marker: Command.Marker) -> Unit = {}

        override fun run() = BufferedReader(InputStreamReader(stream)).forEachLine { line ->
            pattern.matcher(line).let { matcher ->
                if (matcher.matches()) {
                    val uuid = UUID.fromString(matcher.group(GROUP_UUID))
                    onComplete(
                        when (val exitCode = matcher.group(GROUP_CODE)) {
                            null -> Command.Marker(uuid, Command.Status.INVALID)
                            else -> Command.Marker(uuid, exitCode.toInt())
                        }
                    )
                } else {
                    onReadLine(line)
                }
            }
        }

        companion object {

            private const val GROUP_UUID = 1

            private const val GROUP_CODE = 2

            // <UUID><optional space><optional exit status>
            private val pattern: Pattern = Pattern.compile(
                "^([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\\s?([0-9]{1,3})?$",
                Pattern.CASE_INSENSITIVE
            )

            internal fun createAndStart(name: String, stream: InputStream) =
                StreamReader(name, stream).also { reader -> reader.start() }
        }
    }

    companion object {

        private const val THREAD_NAME_STDOUT = "STDOUT"
        private const val THREAD_NAME_STDERR = "STDERR"

        private const val EXCEPTION_SHELL_CANNOT_OPEN = "Error opening shell: '%s'"
        private const val EXCEPTION_SHELL_SHUTDOWN = "The shell is shutdown"

        private val instances by lazy { mutableMapOf<String, Shell>() }

        /**
         * Returns a [Shell] instance using the [path] as the path to the shell/executable.\
         */
        operator fun get(path: String): Shell = instances[path]?.takeIf { shell ->
            shell.isAlive()
        } ?: Shell(path).also { shell ->
            instances[path] = shell
        }

        /** The Bourne shell (sh) */
        val SH: Shell get() = this["sh"]

        /** Switch to root, and run it as a shell */
        val SU: Shell get() = this["su"]

        /**
         * Execute a command with the provided environment.
         *
         * @param command
         *     The name of the program to execute. E.g. "su" or "sh".
         * @param environment
         *     Map of all environment variables to include with the system environment.
         * @return The new [Process] instance.
         * @throws IOException
         *     If the requested program could not be executed.
         */
        @Throws(IOException::class)
        private fun runWithEnv(command: String, environment: EnvironmentMap): Process =
            Runtime.getRuntime().exec(command, (System.getenv() + environment).toArray())

        /**
         * Convert an array to an [EnvironmentMap] with each variable/value separated by '='.
         *
         * @return The array converted to an [EnvironmentMap].
         */
        private fun Array<out String>.toEnvironmentMap(): EnvironmentMap =
            mutableMapOf<Variable, Value>().also { map ->
                forEach { str ->
                    str.split("=").takeIf { arr ->
                        arr.size == 2
                    }?.let { (variable, value) ->
                        map[variable] = value
                    }
                }
            }.toMap()

        /**
         * Convert an array of [Pair] to an [EnvironmentMap].
         *
         * @return The array of variable/value pairs as a new [EnvironmentMap].
         */
        private fun Array<out Pair<Variable, Value>>.toEnvironmentMap(): EnvironmentMap =
            mutableMapOf<Variable, Value>().also { map ->
                forEach { (variable, value) ->
                    map[variable] = value
                }
            }

        /**
         * Converts an [EnvironmentMap] to an array of strings with the variable/value
         * separated by an '=' character.
         *
         * @return An array of environment variables.
         */
        private fun EnvironmentMap.toArray(): Array<String> =
            mutableListOf<String>().also { list ->
                forEach { (variable, value) ->
                    list.add("$variable=$value")
                }
            }.toTypedArray()
    }
}