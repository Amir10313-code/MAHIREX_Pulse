package com.mahirex.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ====================================================================== small HTTP helpers

object Net {
    const val T_NONE = 0
    const val T_WIFI = 1
    const val T_CELL = 2
    const val T_ETH = 3
    const val T_VPN = 4
    const val T_OTHER = 5

    private const val UA = "MAHIREX-Pulse/2.1"

    fun open(url: String, method: String, connectMs: Int, readMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.useCaches = false
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Encoding", "identity")
        c.setRequestProperty("Cache-Control", "no-cache")
        return c
    }

    fun isOnline(ctx: Context): Boolean {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(n) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            return false
        }
    }

    fun transport(ctx: Context): Int {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return T_NONE
            val caps = cm.getNetworkCapabilities(n) ?: return T_NONE
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return T_VPN
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return T_WIFI
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return T_CELL
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return T_ETH
            return T_OTHER
        } catch (e: Exception) {
            return T_NONE
        }
    }

    fun transportName(ctx: Context, t: Int): String = when (t) {
        T_WIFI -> ctx.getString(R.string.sp_net_wifi)
        T_CELL -> ctx.getString(R.string.sp_net_cell)
        T_ETH -> ctx.getString(R.string.sp_net_eth)
        T_VPN -> ctx.getString(R.string.sp_net_vpn)
        T_OTHER -> ctx.getString(R.string.sp_net_other)
        else -> ctx.getString(R.string.sp_net_none)
    }

    private fun drain(c: HttpURLConnection, maxBytes: Int) {
        val ins = c.inputStream
        val buf = ByteArray(1024)
        var left = maxBytes
        while (left > 0) {
            val n = ins.read(buf, 0, Math.min(buf.size, left))
            if (n < 0) break
            left -= n
        }
        ins.close()
    }

    /** One tiny request. Returns the round-trip time in ms, or null when the server did not answer properly. */
    fun pingOnce(s: SpeedServer): Double? {
        var c: HttpURLConnection? = null
        try {
            val t0 = System.nanoTime()
            if (s.type == SrvType.STATIC) {
                c = open(s.pingUrl, "HEAD", 3500, 3500)
                var code = c.responseCode
                if (code >= 400) {
                    c.disconnect()
                    c = open(s.pingUrl, "GET", 3500, 3500)
                    c.setRequestProperty("Range", "bytes=0-0")
                    code = c.responseCode
                    if (code >= 400) return null
                    if (code == 200) {
                        // range ignored: do not read a 100 MB file just to measure latency
                        c.disconnect()
                        return (System.nanoTime() - t0) / 1e6
                    }
                    drain(c, 8)
                } else {
                    c.inputStream.close()
                }
            } else {
                val sep = if (s.pingUrl.contains("?")) "&" else "?"
                c = open(s.pingUrl + sep + "r=" + System.nanoTime(), "GET", 3500, 3500)
                if (c.responseCode >= 400) return null
                drain(c, 4096)
            }
            return (System.nanoTime() - t0) / 1e6
        } catch (e: Exception) {
            return null
        }
    }
}

// ====================================================================== the test itself

class SpeedRunner(context: Context) {
    enum class Phase { IDLE, SELECT, PING, DOWN, UP, DONE }

    interface Listener {
        fun onPhase(p: Phase, server: SpeedServer?)
        fun onPing(ping: Double, jitter: Double)
        fun onLive(p: Phase, mbps: Double)
        fun onPhaseResult(p: Phase, mbps: Double)
        fun onDone(r: SpeedResult)
        fun onError(kind: Int)
    }

    companion object {
        const val ERR_OFFLINE = 1
        const val ERR_SERVER = 2
        const val ERR_DOWNLOAD = 3
        const val ERR_CANCEL = 4

        private const val DOWN_MS = 8000L
        private const val UP_MS = 6000L
        private const val WARM_MS = 1500L
        private const val MAX_DOWN_BYTES = 400L * 1024 * 1024
        private const val MAX_UP_BYTES = 150L * 1024 * 1024
        private const val DOWN_THREADS = 4
        private const val UP_THREADS = 3
        private const val PING_SAMPLES = 10
    }

    private val appCtx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private val conns = Collections.synchronizedList(ArrayList<HttpURLConnection>())
    @Volatile
    private var worker: Thread? = null

    val running: Boolean get() = worker?.isAlive == true

    fun cancel() {
        cancelled.set(true)
        killConnections()
    }

    private fun killConnections() {
        val copy = synchronized(conns) { ArrayList(conns) }
        for (c in copy) {
            try {
                c.disconnect()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun track(c: HttpURLConnection) {
        conns.add(c)
    }

    private fun untrack(c: HttpURLConnection?) {
        if (c != null) conns.remove(c)
    }

    /**
     * @param chosen the server picked by the user, or null for "automatic (lowest ping)"
     * @param all    every known server (used for the automatic choice and for the upload fallback)
     */
    fun start(all: List<SpeedServer>, chosen: SpeedServer?, l: Listener) {
        if (running) return
        cancelled.set(false)
        val t = Thread {
            try {
                run(chosen, all, l)
            } catch (e: Throwable) {
                post { l.onError(if (cancelled.get()) ERR_CANCEL else ERR_SERVER) }
            }
        }
        t.isDaemon = true
        worker = t
        t.start()
    }

    private fun post(r: () -> Unit) {
        main.post { r() }
    }

    private fun run(chosen: SpeedServer?, all: List<SpeedServer>, l: Listener) {
        if (!Net.isOnline(appCtx)) {
            post { l.onError(ERR_OFFLINE) }
            return
        }
        val net = Net.transport(appCtx)

        // 1) server
        var server: SpeedServer? = chosen
        if (server == null) {
            post { l.onPhase(Phase.SELECT, null) }
            ServerManager.probeAll(all, false, { cancelled.get() }) { _, _ -> }
            if (cancelled.get()) {
                post { l.onError(ERR_CANCEL) }
                return
            }
            server = ServerManager.best(all)
        }
        if (server == null) {
            post { l.onError(if (Net.isOnline(appCtx)) ERR_SERVER else ERR_OFFLINE) }
            return
        }
        val srv: SpeedServer = server

        // upload goes to the same server when it supports it, otherwise to the best upload-capable one
        var upSrv: SpeedServer? = if (srv.canUpload) srv else null
        if (upSrv == null) {
            val capable = all.filter { it.canUpload }
            if (capable.isNotEmpty()) {
                ServerManager.probeAll(capable, false, { cancelled.get() }) { _, _ -> }
                upSrv = ServerManager.best(capable) ?: capable.firstOrNull { it.type == SrvType.CLOUDFLARE }
            }
        }

        // 2) ping + jitter
        post { l.onPhase(Phase.PING, srv) }
        val samples = ArrayList<Double>()
        Net.pingOnce(srv)
        for (i in 0 until PING_SAMPLES) {
            if (cancelled.get()) break
            val v = Net.pingOnce(srv)
            if (v != null) samples.add(v)
        }
        if (cancelled.get()) {
            post { l.onError(ERR_CANCEL) }
            return
        }
        if (samples.size < PING_SAMPLES / 2) {
            post { l.onError(if (Net.isOnline(appCtx)) ERR_SERVER else ERR_OFFLINE) }
            return
        }
        val sorted = samples.sorted()
        val ping = sorted[sorted.size / 2]
        var jsum = 0.0
        for (i in 1 until samples.size) jsum += Math.abs(samples[i] - samples[i - 1])
        val jitter = if (samples.size > 1) jsum / (samples.size - 1) else 0.0
        post { l.onPing(ping, jitter) }

        // 3) download
        post { l.onPhase(Phase.DOWN, srv) }
        val down = measure(true, srv, l)
        if (cancelled.get()) {
            post { l.onError(ERR_CANCEL) }
            return
        }
        if (down == null) {
            post { l.onError(if (Net.isOnline(appCtx)) ERR_DOWNLOAD else ERR_OFFLINE) }
            return
        }

        post { l.onPhaseResult(Phase.DOWN, down) }

        // 4) upload (a failed upload does not throw the download result away)
        var up = -1.0
        if (upSrv != null) {
            post { l.onPhase(Phase.UP, upSrv) }
            up = measure(false, upSrv, l) ?: -1.0
            if (cancelled.get()) {
                post { l.onError(ERR_CANCEL) }
                return
            }
            if (up >= 0) {
                val u = up
                post { l.onPhaseResult(Phase.UP, u) }
            }
        }

        val r = SpeedResult(
            System.currentTimeMillis(), down, up, ping, jitter, srv.label, srv.country, net,
            if (upSrv != null && upSrv.id != srv.id) upSrv.label else ""
        )
        SpeedStore.add(appCtx, r)
        try {
            HomeWidgets.updateAll(appCtx)
        } catch (e: Throwable) {
            // widgets are optional
        }
        post { l.onDone(r) }
    }

    private fun downloadUrl(s: SpeedServer): String {
        if (s.type != SrvType.LIBRESPEED) return s.downUrl
        val sep = if (s.downUrl.contains("?")) "&" else "?"
        return s.downUrl + sep + "ckSize=100&r=" + System.nanoTime()
    }

    /**
     * Runs several parallel connections for a fixed time and reports the live speed (moving 1 s window).
     * The result ignores the first second and a half (TCP slow start) and is the average of the rest.
     */
    private fun measure(download: Boolean, s: SpeedServer, l: Listener): Double? {
        val total = AtomicLong(0)
        val stop = AtomicBoolean(false)
        val errors = AtomicInteger(0)
        val alive = AtomicInteger(0)
        val nThreads = if (download) DOWN_THREADS else UP_THREADS
        val maxBytes = if (download) MAX_DOWN_BYTES else MAX_UP_BYTES
        val phase = if (download) Phase.DOWN else Phase.UP
        val threads = ArrayList<Thread>()

        for (i in 0 until nThreads) {
            alive.incrementAndGet()
            val t = Thread {
                try {
                    if (download) downloadLoop(s, total, stop, errors) else uploadLoop(s, total, stop, errors)
                } finally {
                    alive.decrementAndGet()
                }
            }
            t.isDaemon = true
            threads.add(t)
            t.start()
        }

        val t0 = SystemClock.elapsedRealtime()
        val limit = if (download) DOWN_MS else UP_MS
        val window = kotlin.collections.ArrayDeque<LongArray>()
        var ema = 0.0
        var warmBytes = -1L
        var warmT = 0L
        while (true) {
            try {
                Thread.sleep(150)
            } catch (e: InterruptedException) {
                break
            }
            if (cancelled.get()) break
            val now = SystemClock.elapsedRealtime()
            val b = total.get()
            val el = now - t0
            window.addLast(longArrayOf(now, b))
            while (window.size > 2 && now - window.first()[0] > 1000) window.removeFirst()
            val f = window.first()
            val dt = now - f[0]
            if (dt > 0) {
                val inst = (b - f[1]) * 8.0 / (dt / 1000.0) / 1e6
                ema = if (ema == 0.0) inst else ema * 0.6 + inst * 0.4
                val shown = ema
                post { l.onLive(phase, shown) }
            }
            if (warmBytes < 0 && el >= WARM_MS) {
                warmBytes = b
                warmT = now
            }
            if (el >= limit || b >= maxBytes) break
            if (alive.get() == 0) break
        }
        stop.set(true)
        val endB = total.get()
        val endT = SystemClock.elapsedRealtime()
        killConnections()
        for (t in threads) {
            try {
                t.join(800)
            } catch (e: InterruptedException) {
                // ignore
            }
        }
        if (cancelled.get()) return null
        if (warmBytes < 0) {
            warmBytes = 0
            warmT = t0
        }
        val secs = (endT - warmT) / 1000.0
        val bytes = endB - warmBytes
        if (secs < 0.3 || bytes <= 0) return null
        return bytes * 8.0 / secs / 1e6
    }

    private fun downloadLoop(s: SpeedServer, total: AtomicLong, stop: AtomicBoolean, errors: AtomicInteger) {
        val buf = ByteArray(65536)
        while (!stop.get() && !cancelled.get()) {
            var c: HttpURLConnection? = null
            try {
                c = Net.open(downloadUrl(s), "GET", 5000, 8000)
                track(c)
                if (c.responseCode !in 200..299) {
                    errors.incrementAndGet()
                    return
                }
                val ins = c.inputStream
                while (!stop.get()) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total.addAndGet(n.toLong())
                }
            } catch (e: Exception) {
                if (!stop.get() && !cancelled.get()) errors.incrementAndGet()
                return
            } finally {
                untrack(c)
                try {
                    c?.disconnect()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private val payload: ByteArray by lazy {
        val b = ByteArray(262144)
        Random().nextBytes(b) // random bytes: cannot be compressed on the way
        b
    }

    private fun uploadLoop(s: SpeedServer, total: AtomicLong, stop: AtomicBoolean, errors: AtomicInteger) {
        val url = s.upUrl ?: return
        var chunk = 262144
        while (!stop.get() && !cancelled.get()) {
            var c: HttpURLConnection? = null
            try {
                val t0 = SystemClock.elapsedRealtime()
                c = Net.open(url, "POST", 5000, 20000)
                track(c)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setFixedLengthStreamingMode(chunk)
                val out = c.outputStream
                var left = chunk
                while (left > 0 && !stop.get()) {
                    val n = Math.min(left, payload.size)
                    out.write(payload, 0, n)
                    total.addAndGet(n.toLong())
                    left -= n
                }
                if (stop.get()) return
                out.flush()
                out.close()
                val code = c.responseCode
                try {
                    c.inputStream.close()
                } catch (e: IOException) {
                    // body is not needed
                }
                if (code !in 200..299) {
                    errors.incrementAndGet()
                    return
                }
                // faster links get bigger requests so the per-request overhead stays small
                if (SystemClock.elapsedRealtime() - t0 < 1200 && chunk < 4 * 1024 * 1024) chunk *= 2
            } catch (e: Exception) {
                if (!stop.get() && !cancelled.get()) errors.incrementAndGet()
                return
            } finally {
                untrack(c)
                try {
                    c?.disconnect()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
