package tw.nekomimi.nekogram.helpers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.telegram.messenger.FileLog
import org.telegram.messenger.SharedConfig
import tw.nekomimi.nekogram.utils.HttpClient
import java.util.*

object FreeProxyManager {
    private val MIRRORS_PROXIES = listOf(
        "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/socks5/data.json",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.json",
        "https://proxifly.dev/proxies/protocols/socks5/data.json"
    )
    private val MIRRORS_META = listOf(
        "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/meta/data.json",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/meta/data.json",
        "https://proxifly.dev/proxies/meta/data.json"
    )

    private val gson = Gson()
    private var cachedProxies: List<FreeProxy> = emptyList()
    private var cachedMeta: FreeProxyMeta? = null
    private var lastFetchTime: Long = 0

    data class FreeProxy(
        val proxy: String,
        val protocol: String,
        val ip: String,
        val port: Int,
        val https: Boolean,
        val anonymity: String,
        val score: Int,
        val geolocation: Geolocation
    )

    data class Geolocation(
        val country: String,
        val city: String
    )

    data class FreeProxyMeta(
        val timestamp: String,
        val totals: Totals
    )

    data class Totals(
        val all: Int,
        val protocols: Map<String, Int>,
        val countries: Map<String, Int>
    )

    suspend fun fetchProxies(force: Boolean = false): List<FreeProxy> = withContext(Dispatchers.IO) {
        if (!force && cachedProxies.isNotEmpty() && System.currentTimeMillis() - lastFetchTime < 300000) {
            return@withContext cachedProxies
        }

        for (url in MIRRORS_PROXIES) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                val response = HttpClient.instance.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (json != null) {
                        val type = object : TypeToken<List<FreeProxy>>() {}.type
                        cachedProxies = gson.fromJson(json, type)
                        lastFetchTime = System.currentTimeMillis()
                        return@withContext cachedProxies
                    }
                }
            } catch (e: Exception) {
                FileLog.e("FreeProxyManager: Proxy mirror failed: $url", e)
            }
        }
        cachedProxies
    }

    suspend fun fetchMeta(): FreeProxyMeta? = withContext(Dispatchers.IO) {
        if (cachedMeta != null && System.currentTimeMillis() - lastFetchTime < 300000) {
            return@withContext cachedMeta
        }

        for (url in MIRRORS_META) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                val response = HttpClient.instance.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (json != null) {
                        cachedMeta = gson.fromJson(json, FreeProxyMeta::class.java)
                        return@withContext cachedMeta
                    }
                }
            } catch (e: Exception) {
                FileLog.e("FreeProxyManager: Meta mirror failed: $url", e)
            }
        }
        cachedMeta
    }

    fun getAutoProxy(): FreeProxy? {
        return cachedProxies.maxByOrNull { it.score }
    }

    fun applyProxy(proxy: FreeProxy) {
        val info = SharedConfig.ProxyInfo(
            proxy.ip,
            proxy.port,
            "",
            "",
            ""
        )
        // Set proper protocol
        // SharedConfig.ProxyInfo doesn't explicitly store protocol as string in the constructor
        // but it's handled by how it's added.
        // Actually, Telegram's ProxyInfo handles SOCKS5 vs MTProxy via secret.
        // For HTTP we might need to check how it's stored.
        // In Telegram, ProxyInfo is mostly for SOCKS5 or MTProxy.
        
        // Let's check how ProxyListActivity adds different types.
        SharedConfig.addProxy(info)
        SharedConfig.setCurrentProxy(info)
        SharedConfig.setProxyEnable(true)
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged)
    }
}
