package com.kingbrezz.slime.native

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

/**
 * Dedicated Slime-owned runtime process. The native library is loaded in this
 * process, not in the launcher process and not in stock Minecraft.
 */
class RuntimeHostService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("SlimeRuntimeHost").also { it.start() }
        messenger = Messenger(Handler(worker.looper) { message ->
            handleMessage(message)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        if (::worker.isInitialized) worker.quitSafely()
        super.onDestroy()
    }

    private fun handleMessage(message: Message) {
        val replyTo = message.replyTo ?: return
        val result = try {
            when (message.what) {
                RuntimeHostProtocol.START -> startRuntime(message.data ?: Bundle())
                RuntimeHostProtocol.SHUTDOWN -> shutdownRuntime()
                RuntimeHostProtocol.INSTALL_HOOKS -> installHooks()
                else -> Bundle().apply { putString("error", "Unknown runtime command: ${message.what}") }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Runtime host command failed", t)
            Bundle().apply { putString("error", t.message ?: t.javaClass.simpleName) }
        }
        try {
            replyTo.send(Message.obtain(null, RuntimeHostProtocol.RESULT).apply { data = result })
        } catch (t: RemoteException) {
            Log.w(TAG, "Runtime client disconnected before reply", t)
        }
    }

    private fun startRuntime(input: Bundle): Bundle {
        val runtime = NativeRuntimeLoader.load(this)
        if (!runtime.preloaderLoaded) {
            return resultBundle(runtime, false, PreloaderNative.emptyStatus(runtime.error ?: "native load failed"))
        }

        val version = input.getString(RuntimeHostProtocol.VERSION).orEmpty()
        val initialized = PreloaderNative.init(version)

        val moduleIds = input.getStringArrayList(RuntimeHostProtocol.MODULE_IDS).orEmpty()
        val moduleFlags = input.getBooleanArray(RuntimeHostProtocol.MODULE_FLAGS) ?: booleanArrayOf()
        moduleIds.forEachIndexed { index, id ->
            PreloaderNative.setModule(id, moduleFlags.getOrElse(index) { false })
        }

        val valueIds = input.getStringArrayList(RuntimeHostProtocol.VALUE_IDS).orEmpty()
        val valueNumbers = input.getDoubleArray(RuntimeHostProtocol.VALUE_NUMBERS) ?: doubleArrayOf()
        valueIds.forEachIndexed { index, id ->
            val split = id.indexOf('.')
            if (split > 0) PreloaderNative.setModuleValue(id.substring(0, split), id.substring(split + 1), valueNumbers.getOrElse(index) { 0.0 })
        }

        val signatureNames = input.getStringArrayList(RuntimeHostProtocol.SIGNATURE_NAMES).orEmpty()
        val signaturePatterns = input.getStringArrayList(RuntimeHostProtocol.SIGNATURE_PATTERNS).orEmpty()
        signatureNames.forEachIndexed { index, name ->
            PreloaderNative.setSignature(name, signaturePatterns.getOrElse(index) { "" })
        }

        val offsetNames = input.getStringArrayList(RuntimeHostProtocol.OFFSET_NAMES).orEmpty()
        val offsetValues = input.getIntArray(RuntimeHostProtocol.OFFSET_VALUES) ?: intArrayOf()
        offsetNames.forEachIndexed { index, name ->
            offsetValues.getOrNull(index)?.let { PreloaderNative.setOffset(name, it) }
        }

        return resultBundle(runtime, initialized, PreloaderNative.status()).apply {
            putString("libraryVersion", PreloaderNative.libraryVersion())
        }
    }

    private fun installHooks(): Bundle {
        if (!PreloaderNative.libraryLoaded) {
            return Bundle().apply { putBoolean("ok", false); putString("error", "native library not loaded") }
        }
        val count = PreloaderNative.installAllHooks()
        val status = PreloaderNative.status()
        return Bundle().apply {
            putBoolean("ok", count > 0)
            putBoolean("libraryLoaded", true)
            putInt("installedHooks", count)
            putString("phase", status.phase)
            putString("message", status.message)
            putInt("sigResolved", status.sigResolved)
            putInt("sigTotal", status.sigTotal)
            putInt("hooksActive", status.hooksActive)
            putInt("hooksPending", status.hooksPending)
            putBoolean("canInject", status.canInject)
            putBoolean("targetFound", status.targetFound)
            putString("targetBase", status.targetBase)
        }
    }

    private fun shutdownRuntime(): Bundle {
        PreloaderNative.shutdown()
        val status = PreloaderNative.status()
        return Bundle().apply {
            putBoolean("libraryLoaded", PreloaderNative.libraryLoaded)
            putBoolean("ok", true)
            putString("phase", status.phase)
            putString("message", status.message)
        }
    }

    private fun resultBundle(
        runtime: NativeRuntimeLoader.Result,
        initialized: Boolean,
        status: PreloaderNative.Status,
    ): Bundle = Bundle().apply {
        putBoolean("ok", initialized && runtime.preloaderLoaded)
        putBoolean("libraryLoaded", runtime.preloaderLoaded)
        putString("abi", runtime.abi)
        putString("libraryDirectory", runtime.libraryDirectory)
        putStringArrayList("libraries", ArrayList(runtime.libraries.map {
            "${it.name}|${it.sizeBytes}|${it.elfClass ?: 0}|${it.machine ?: 0}|${it.compatible}"
        }))
        putString("libraryVersion", if (runtime.preloaderLoaded) PreloaderNative.libraryVersion() else "unavailable")
        putString("phase", status.phase)
        putString("versionId", status.versionId)
        putString("message", status.message)
        putInt("sigResolved", status.sigResolved)
        putInt("sigTotal", status.sigTotal)
        putInt("hooksActive", status.hooksActive)
        putInt("hooksPending", status.hooksPending)
        putBoolean("canInject", status.canInject)
        putBoolean("targetFound", status.targetFound)
        putString("targetBase", status.targetBase)
        runtime.error?.let { putString("error", it) }
    }

    companion object {
        private const val TAG = "SlimeRuntimeHost"
    }
}