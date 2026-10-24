package com.voiceludo.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ============================================================================
// Sirf yeh batata hai ke PHONE ka internet (WiFi/mobile data) on hai ya nahi —
// yeh alag hai BackendClient.state se, jo batata hai ke hamare GAME-SERVER se
// connection bana hai ya nahi. Dono ko alag rakhne se sahi message dikha sakte
// hain: "internet band hai" vs "internet to hai lekin server tak nahi pohanch
// rahe" (ConnectionStatusOverlay isi farak se sahi wording chunta hai).
// ============================================================================
object NetworkMonitor {
    private val _hasInternet = MutableStateFlow(true)
    val hasInternet: StateFlow<Boolean> = _hasInternet

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _hasInternet.value = isCurrentlyConnected(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _hasInternet.value = true
                // Net wapis aaya aur game-server se connection toota hua hai to turant dobara jodo
                BackendClient.onNetworkBack()
            }
            override fun onLost(network: Network) {
                _hasInternet.value = isCurrentlyConnected(cm)
            }
            override fun onUnavailable() {
                _hasInternet.value = false
            }
        })
    }

    private fun isCurrentlyConnected(cm: ConnectivityManager): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
