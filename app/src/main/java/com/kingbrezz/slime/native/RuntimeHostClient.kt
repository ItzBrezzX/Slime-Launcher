package com.kingbrezz.slime.native

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** IPC client for SlimeRuntimeHostService. Call from a worker thread. */
object RuntimeHostClient {
    data class Snapshot(
        val ok: Boolean = false,
        val libraryLoaded: Boolean = false,
        val abi: String = "unknown",
        val libraryDirectory: String = "",
        val libraries: List<String> = emptyList(),
        val libraryVersion: String = "unavailable",
        val phase: String = "Error",
        val versionId: String = "",
        val message: String = "",
        val sigResolved: Int = 0,
        val sigTotal: Int = 0,
        val hooksActive: Int = 0,
        val hooksPending: Int = 0,
        val installedHooks: Int = 0,
        val canInject: Boolean = false,
        val targetFound: Boolean = false,
        val targetBase: String = "0x0",
        val error: String? = null,
    )

    fun start(
        context: Context,
        versionId: String,
        moduleFlags: Map<String, Boolean>,
        moduleValues: Map<String, Double> = emptyMap(),
        signatureOverrides: Map<String, String> = emptyMap(),
        offsetOverrides: Map<String, Int> = emptyMap(),
    ): Snapshot {
        val input = Bundle().apply {
            putString(RuntimeHostProtocol.VERSION, versionId)
            putStringArrayList(RuntimeHostProtocol.MODULE_IDS, ArrayList(moduleFlags.keys))
            putBooleanArray(RuntimeHostProtocol.MODULE_FLAGS, moduleFlags.values.toBooleanArray())
            putStringArrayList(RuntimeHostProtocol.VALUE_IDS, ArrayList(moduleValues.keys))
            putDoubleArray(RuntimeHostProtocol.VALUE_NUMBERS, moduleValues.values.toDoubleArray())
            putStringArrayList(RuntimeHostProtocol.SIGNATURE_NAMES, ArrayList(signatureOverrides.keys))
            putStringArrayList(RuntimeHostProtocol.SIGNATURE_PATTERNS, ArrayList(signatureOverrides.values))
            putStringArrayList(RuntimeHostProtocol.OFFSET_NAMES, ArrayList(offsetOverrides.keys))
            putIntArray(RuntimeHostProtocol.OFFSET_VALUES, offsetOverrides.values.toIntArray())
        }
        return transact(context, RuntimeHostProtocol.START, input)
    }

    fun installHooks(context: Context): Snapshot = transact(context, RuntimeHostProtocol.INSTALL_HOOKS, Bundle())

    fun shutdown(context: Context): Snapshot = transact(context, RuntimeHostProtocol.SHUTDOWN, Bundle())

    private fun transact(context: Context, command: Int, input: Bundle): Snapshot {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return Snapshot(error = "Runtime host IPC must run off the main thread")
        }

        val app = context.applicationContext
        val connected = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val remote = AtomicReference<Messenger?>()
        val response = AtomicReference<Bundle?>()
        val error = AtomicReference<String?>()
        val replyThread = HandlerThread("SlimeRuntimeReply").also { it.start() }
        val replyMessenger = Messenger(Handler(replyThread.looper) { message ->
            response.set(message.data ?: Bundle())
            completed.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) error.set("Runtime host returned no binder")
                else remote.set(Messenger(service))
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                error.compareAndSet(null, "Runtime host disconnected")
                connected.countDown()
                completed.countDown()
            }
        }

        var bound = false
        try {
            bound = app.bindService(Intent(app, RuntimeHostService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) return Snapshot(error = "Unable to bind Slime runtime host")
            if (!connected.await(10, TimeUnit.SECONDS)) return Snapshot(error = "Timed out connecting to runtime host")
            val target = remote.get() ?: return Snapshot(error = error.get() ?: "Runtime host binder unavailable")
            val request = Message.obtain(null, command).apply {
                data = input
                replyTo = replyMessenger
            }
            target.send(request)
            if (!completed.await(30, TimeUnit.SECONDS)) return Snapshot(error = "Runtime host command timed out")
            val data = response.get() ?: return Snapshot(error = error.get() ?: "Runtime host returned no response")
            return Snapshot(
                ok = data.getBoolean("ok"),
                libraryLoaded = data.getBoolean("libraryLoaded"),
                abi = data.getString("abi") ?: "unknown",
                libraryDirectory = data.getString("libraryDirectory").orEmpty(),
                libraries = data.getStringArrayList("libraries") ?: emptyList(),
                libraryVersion = data.getString("libraryVersion") ?: "unavailable",
                phase = data.getString("phase") ?: "Error",
                versionId = data.getString("versionId").orEmpty(),
                message = data.getString("message").orEmpty(),
                sigResolved = data.getInt("sigResolved"),
                sigTotal = data.getInt("sigTotal"),
                hooksActive = data.getInt("hooksActive"),
                hooksPending = data.getInt("hooksPending"),
                installedHooks = data.getInt("installedHooks"),
                canInject = data.getBoolean("canInject"),
                targetFound = data.getBoolean("targetFound"),
                targetBase = data.getString("targetBase") ?: "0x0",
                error = data.getString("error"),
            )
        } catch (t: Throwable) {
            return Snapshot(error = t.message ?: t.javaClass.simpleName)
        } finally {
            if (bound) try { app.unbindService(connection) } catch (_: Throwable) { }
            replyThread.quitSafely()
        }
    }
}