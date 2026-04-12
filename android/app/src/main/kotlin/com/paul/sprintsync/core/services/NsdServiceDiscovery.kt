package com.paul.sprintsync.core.services

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class NsdServiceDiscovery(private val context: Context) {
    companion object {
        const val SERVICE_TYPE = "_sprintsync._tcp."
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(
        serviceName: String,
        port: Int,
        onRegistered: (Result<Unit>) -> Unit,
    ) {
        stopRegistrationSafely()
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                try {
                    onRegistered(Result.success(Unit))
                } catch (_: Throwable) {
                    // Ignore callback consumer failures.
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (registrationListener === this) {
                    registrationListener = null
                }
                try {
                    onRegistered(
                        Result.failure(
                            IllegalStateException(
                                "NSD registration failed ($errorCode) for ${serviceInfo.serviceName}",
                            ),
                        ),
                    )
                } catch (_: Throwable) {
                    // Ignore callback consumer failures.
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                try {
                    if (registrationListener === this) {
                        registrationListener = null
                    }
                } catch (_: Throwable) {
                    // Ignore callback failures.
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                try {
                    if (registrationListener === this) {
                        registrationListener = null
                    }
                } catch (_: Throwable) {
                    // Ignore callback failures.
                }
            }
        }
        registrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Throwable) {
            registrationListener = null
            try {
                onRegistered(Result.failure(error))
            } catch (_: Throwable) {
                // Ignore callback consumer failures.
            }
        }
    }

    fun discoverServices(
        onServiceFound: (hostAddress: String, port: Int, serviceName: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stopDiscoverySafely()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try {
                    stopDiscoverySafely()
                    onError("NSD start discovery failed ($errorCode) for $serviceType")
                } catch (_: Throwable) {
                    // Ignore callback consumer failures.
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try {
                    stopDiscoverySafely()
                    onError("NSD stop discovery failed ($errorCode) for $serviceType")
                } catch (_: Throwable) {
                    // Ignore callback consumer failures.
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                try {
                    // No-op.
                } catch (_: Throwable) {
                    // Ignore callback failures.
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                try {
                    if (discoveryListener === this) {
                        discoveryListener = null
                    }
                } catch (_: Throwable) {
                    // Ignore callback failures.
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    if (serviceInfo.serviceType != SERVICE_TYPE) {
                        return
                    }
                    resolveService(serviceInfo, onServiceFound, onError)
                } catch (error: Throwable) {
                    try {
                        onError("NSD service-found callback failed: ${error.localizedMessage ?: "unknown"}")
                    } catch (_: Throwable) {
                        // Ignore callback consumer failures.
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                try {
                    // No-op.
                } catch (_: Throwable) {
                    // Ignore callback failures.
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Throwable) {
            discoveryListener = null
            try {
                onError("NSD discoverServices failed: ${error.localizedMessage ?: "unknown"}")
            } catch (_: Throwable) {
                // Ignore callback consumer failures.
            }
        }
    }

    fun stopAll() {
        stopDiscoverySafely()
        stopRegistrationSafely()
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onServiceFound: (hostAddress: String, port: Int, serviceName: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                try {
                    onError("NSD resolve failed ($errorCode) for ${serviceInfo.serviceName}")
                } catch (_: Throwable) {
                    // Ignore callback consumer failures.
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                try {
                    val hostAddress = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    if (hostAddress.isNullOrBlank() || port <= 0) {
                        onError("NSD resolved invalid endpoint for ${serviceInfo.serviceName}")
                        return
                    }
                    onServiceFound(hostAddress, port, serviceInfo.serviceName)
                } catch (error: Throwable) {
                    try {
                        onError("NSD resolve callback failed: ${error.localizedMessage ?: "unknown"}")
                    } catch (_: Throwable) {
                        // Ignore callback consumer failures.
                    }
                }
            }
        }
        try {
            nsdManager.resolveService(serviceInfo, listener)
        } catch (error: Throwable) {
            try {
                onError("NSD resolveService failed: ${error.localizedMessage ?: "unknown"}")
            } catch (_: Throwable) {
                // Ignore callback consumer failures.
            }
        }
    }

    private fun stopRegistrationSafely() {
        val listener = registrationListener ?: return
        registrationListener = null
        try {
            nsdManager.unregisterService(listener)
        } catch (_: IllegalArgumentException) {
            // Safe to ignore when registration was already cleaned up by Android.
        } catch (_: Throwable) {
            // Ignore platform-level NSD cleanup failures.
        }
    }

    private fun stopDiscoverySafely() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Safe to ignore when discovery was already cleaned up by Android.
        } catch (_: Throwable) {
            // Ignore platform-level NSD cleanup failures.
        }
    }
}
