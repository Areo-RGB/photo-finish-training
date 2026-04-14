package com.paul.sprintsync.feature.motion.data.nativebridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SensorNativeController(
    private val activity: ComponentActivity,
) : ImageAnalysis.Analyzer {
    companion object {
        private const val TAG = "SensorNativeController"
        private const val PREVIEW_REBIND_RETRY_DELAY_MS = 200L
        private const val PREVIEW_REBIND_MAX_ATTEMPTS = 3
        private const val HS120_DOWNGRADE_FPS_THRESHOLD = 80.0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameDiffer = RoiFrameDiffer()
    private val fpsMonitor = SensorNativeFpsMonitor(
        lowFpsThreshold = HS120_DOWNGRADE_FPS_THRESHOLD,
    )
    private val analysisInFlight = AtomicBoolean(false)

    @Volatile
    private var eventListener: ((SensorNativeEvent) -> Unit)? = null

    @Volatile
    private var monitoring = false

    @Volatile
    private var config: NativeMonitoringConfig = NativeMonitoringConfig.defaults()

    @Volatile
    private var streamFrameCount = 0L

    @Volatile
    private var processedFrameCount = 0L

    @Volatile
    private var observedFps: Double? = null

    @Volatile
    private var activeCameraFpsMode: NativeCameraFpsMode = NativeCameraFpsMode.NORMAL

    @Volatile
    private var targetFpsUpper: Int? = null

    private var wasMonitoringBeforePause = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var pendingPreviewRebindRunnable: Runnable? = null
    private var previewRebindAttemptCount = 0
    private val detectionMath = NativeDetectionMath(config)

    private val cameraSession: SensorNativeCameraSession by lazy {
        SensorNativeCameraSession(
            activity = activity,
            mainHandler = mainHandler,
            analyzerExecutor = analyzerExecutor,
            analyzer = this,
            emitError = ::emitError,
        )
    }

    fun setEventListener(listener: ((SensorNativeEvent) -> Unit)?) {
        eventListener = listener
    }

    fun onHostPaused() {
        wasMonitoringBeforePause = monitoring
        if (monitoring) {
            stopNativeMonitoringInternal()
        }
    }

    fun onHostResumed() {
        if (!wasMonitoringBeforePause || monitoring) {
            return
        }
        wasMonitoringBeforePause = false
        startMonitoringBackend(
            onStarted = {
                monitoring = true
                emitState("monitoring")
            },
            onError = { error ->
                emitError("Failed to resume monitoring: $error")
                stopNativeMonitoringInternal()
            },
        )
    }

    fun dispose() {
        cancelPreviewRebindRetries()
        stopNativeMonitoringInternal()
        analyzerExecutor.shutdown()
    }

    fun attachPreviewSurface(targetPreviewView: PreviewView) {
        mainHandler.post {
            previewView = targetPreviewView
            logRuntimeDiagnostic(
                "preview attached: monitoring=$monitoring hasProvider=${cameraProvider != null}",
            )
            rebindCameraUseCasesIfMonitoring()
            schedulePreviewRebindRetriesIfMonitoring()
        }
    }

    fun detachPreviewSurface(targetPreviewView: PreviewView) {
        mainHandler.post {
            if (previewView !== targetPreviewView) {
                return@post
            }
            previewView = null
            logRuntimeDiagnostic(
                "preview detached: monitoring=$monitoring hasProvider=${cameraProvider != null}",
            )
            cancelPreviewRebindRetries()
            rebindCameraUseCasesIfMonitoring()
        }
    }

    fun startNativeMonitoring(monitoringConfig: NativeMonitoringConfig, onComplete: (Result<Unit>) -> Unit) {
        val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            val message = "Camera permission is required before starting native monitoring."
            emitError(message)
            onComplete(Result.failure(IllegalStateException(message)))
            return
        }
        config = monitoringConfig
        detectionMath.updateConfig(config)
        if (monitoring) {
            emitState("monitoring")
            onComplete(Result.success(Unit))
            return
        }
        resetStreamState()
        startMonitoringBackend(
            onStarted = {
                monitoring = true
                emitState("monitoring")
                onComplete(Result.success(Unit))
            },
            onError = { error ->
                stopNativeMonitoringInternal()
                emitError("Failed to initialize native monitoring: $error")
                onComplete(Result.failure(IllegalStateException(error)))
            },
        )
    }

    fun stopNativeMonitoring() {
        stopNativeMonitoringInternal()
    }

    fun updateNativeConfig(monitoringConfig: NativeMonitoringConfig) {
        val previousFacing = config.cameraFacing
        config = monitoringConfig
        detectionMath.updateConfig(config)

        if (monitoring && config.cameraFacing != previousFacing) {
            rebindCameraUseCasesIfMonitoring()
        }
        emitState(if (monitoring) "monitoring" else "idle")
    }

    fun resetNativeRun() {
        resetNativeRunInternal()
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (!monitoring) {
                return
            }
            val frameSensorNanos = image.imageInfo.timestamp
            updateStreamTelemetry(frameSensorNanos)
            val activeConfig = config
            if ((streamFrameCount % activeConfig.processEveryNFrames.toLong()) != 0L) {
                return
            }
            if (!analysisInFlight.compareAndSet(false, true)) {
                return
            }

            val lumaPlane = image.planes[0]
            val rawScore = frameDiffer.scoreLumaPlane(
                lumaBuffer = lumaPlane.buffer,
                rowStride = lumaPlane.rowStride,
                pixelStride = lumaPlane.pixelStride,
                width = image.width,
                height = image.height,
                roiCenterX = activeConfig.roiCenterX,
                roiWidth = activeConfig.roiWidth,
            )
            processedFrameCount += 1
            val stats = detectionMath.process(
                rawScore = rawScore,
                frameSensorNanos = frameSensorNanos,
            )
            emitFrameStats(stats)
            stats.triggerEvent?.let { emitTrigger(it) }
        } catch (error: Exception) {
            emitError("Native frame analysis failed: ${error.localizedMessage ?: "unknown"}")
        } finally {
            analysisInFlight.set(false)
            image.close()
        }
    }

    private fun startMonitoringBackend(onStarted: () -> Unit, onError: (String) -> Unit) {
        startNormalBackend(onStarted = onStarted, onError = onError)
    }

    private fun startNormalBackend(onStarted: () -> Unit, onError: (String) -> Unit) {
        activeCameraFpsMode = NativeCameraFpsMode.NORMAL
        val providerFuture = ProcessCameraProvider.getInstance(activity)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    cameraSession.bindAndConfigure(
                        provider = provider,
                        previewView = previewView,
                        includePreview = true,
                        preferredFacing = config.cameraFacing,
                    )
                    targetFpsUpper = cameraSession.currentTargetFpsUpper()
                    logRuntimeDiagnostic(
                        "normal backend ready: hasPreview=${previewView != null} monitoringBeforeStart=$monitoring",
                    )
                    onStarted()
                    rebindCameraUseCasesIfMonitoring()
                    schedulePreviewRebindRetriesIfMonitoring()
                } catch (error: Exception) {
                    onError(error.localizedMessage ?: "unknown")
                }
            },
            ContextCompat.getMainExecutor(activity),
        )
    }

    private fun stopNativeMonitoringInternal() {
        cancelPreviewRebindRetries()
        monitoring = false
        cameraSession.stop(cameraProvider)
        cameraProvider = null
        resetStreamState()
        activeCameraFpsMode = NativeCameraFpsMode.NORMAL
        targetFpsUpper = null
        emitState("idle")
    }

    private fun restartMonitoringBackend() {
        cameraSession.stop(cameraProvider)
        cameraProvider = null
        analysisInFlight.set(false)
        frameDiffer.reset()
        fpsMonitor.reset()
        observedFps = null
        targetFpsUpper = null

        startMonitoringBackend(
            onStarted = {
                monitoring = true
                emitState("monitoring")
            },
            onError = { error ->
                emitError("Failed to reconfigure camera backend: $error")
                stopNativeMonitoringInternal()
            },
        )
    }

    private fun resetNativeRunInternal() {
        streamFrameCount = 0L
        processedFrameCount = 0L
        detectionMath.resetRun()
        frameDiffer.reset()
        emitState(if (monitoring) "monitoring" else "idle")
    }

    private fun resetStreamState() {
        streamFrameCount = 0L
        processedFrameCount = 0L
        observedFps = null
        analysisInFlight.set(false)
        fpsMonitor.reset()
        detectionMath.resetRun()
        frameDiffer.reset()
    }

    private fun rebindCameraUseCasesIfMonitoring() {
        if (!monitoring) {
            return
        }
        if (!attemptPreviewRebind()) {
            schedulePreviewRebindRetriesIfMonitoring()
        }
    }

    private fun attemptPreviewRebind(): Boolean {
        val provider = cameraProvider ?: return false
        return try {
            cameraSession.bindAndConfigure(
                provider = provider,
                previewView = previewView,
                includePreview = true,
                preferredFacing = config.cameraFacing,
            )
            targetFpsUpper = cameraSession.currentTargetFpsUpper()
            true
        } catch (error: Exception) {
            emitError("Failed to bind preview surface: ${error.localizedMessage ?: "unknown"}")
            false
        }
    }

    private fun schedulePreviewRebindRetriesIfMonitoring() {
        if (
            !shouldSchedulePreviewRebindRetry(
                monitoring = monitoring,
                hasPreviewView = previewView != null,
                hasCameraProvider = cameraProvider != null,
            )
        ) {
            return
        }
        logRuntimeDiagnostic("scheduling preview rebind retries")
        cancelPreviewRebindRetries()
        previewRebindAttemptCount = 0
        val runnable = object : Runnable {
            override fun run() {
                if (
                    !shouldSchedulePreviewRebindRetry(
                        monitoring = monitoring,
                        hasPreviewView = previewView != null,
                        hasCameraProvider = cameraProvider != null,
                    )
                ) {
                    cancelPreviewRebindRetries()
                    return
                }
                previewRebindAttemptCount += 1
                val success = attemptPreviewRebind()
                if (!success) {
                    Log.w(TAG, "Preview rebind attempt $previewRebindAttemptCount failed.")
                } else {
                    logRuntimeDiagnostic("preview rebind attempt $previewRebindAttemptCount succeeded")
                }
                if (previewRebindAttemptCount >= PREVIEW_REBIND_MAX_ATTEMPTS) {
                    cancelPreviewRebindRetries()
                    return
                }
                mainHandler.postDelayed(this, PREVIEW_REBIND_RETRY_DELAY_MS)
            }
        }
        pendingPreviewRebindRunnable = runnable
        mainHandler.postDelayed(runnable, PREVIEW_REBIND_RETRY_DELAY_MS)
    }

    private fun cancelPreviewRebindRetries() {
        pendingPreviewRebindRunnable?.let(mainHandler::removeCallbacks)
        pendingPreviewRebindRunnable = null
        previewRebindAttemptCount = 0
    }

    private fun logRuntimeDiagnostic(message: String) {
        Log.d(TAG, "diag: $message")
    }

    private fun updateStreamTelemetry(frameSensorNanos: Long) {
        streamFrameCount += 1
        val fpsObservation = fpsMonitor.update(
            frameSensorNanos = frameSensorNanos,
            mode = activeCameraFpsMode,
        )
        observedFps = fpsObservation.observedFps
    }

    private fun emitFrameStats(stats: NativeFrameStats) {
        emitEvent(
            SensorNativeEvent.FrameStats(
                stats = stats,
                streamFrameCount = streamFrameCount,
                processedFrameCount = processedFrameCount,
                observedFps = observedFps,
                cameraFpsMode = activeCameraFpsMode,
                targetFpsUpper = targetFpsUpper,
            ),
        )
    }

    private fun emitTrigger(trigger: NativeTriggerEvent) {
        emitEvent(SensorNativeEvent.Trigger(trigger = trigger))
    }

    private fun emitState(state: String) {
        emitEvent(
            SensorNativeEvent.State(
                state = state,
                monitoring = monitoring,
            ),
        )
    }

    private fun emitDiagnostic(message: String) {
        emitEvent(SensorNativeEvent.Diagnostic(message = message))
    }

    private fun emitError(message: String) {
        emitEvent(SensorNativeEvent.Error(message = message))
    }

    private fun emitEvent(event: SensorNativeEvent) {
        val listener = eventListener ?: return
        mainHandler.post { listener(event) }
    }
}

internal fun shouldSchedulePreviewRebindRetry(
    monitoring: Boolean,
    hasPreviewView: Boolean,
    hasCameraProvider: Boolean,
): Boolean {
    return monitoring && hasPreviewView && hasCameraProvider
}
