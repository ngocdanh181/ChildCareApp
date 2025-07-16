package com.example.childlocate.service

import android.net.VpnService

// WebFilterVpnService.kt
/*
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap*/

class WebFilterVpnService : VpnService() {
/*
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "WebFilterVpnService"

    // Sử dụng HashSet cho hiệu suất tìm kiếm nhanh hơn
    private val blockedDomains = HashSet<String>().apply {
        add("facebook.com")
        add("pornhub.com")
        add("xvideos.com")
        add("xnxx.com")
        add("twitter.com")
        add("instagram.com")
        add("youtube.com")
        // Thêm các domain khác nếu cần
    }

    // Các domain quan trọng của hệ thống KHÔNG nên chặn
    private val systemDomains = HashSet<String>().apply {
        add("googleapis.com")
        add("google.com")
        add("gstatic.com")
        add("dns.google")
        add("cloudflare-dns.com")
        add("android.com")
        add("app-measurement.com")
        add("play.google.com")
        add("connectivitycheck.gstatic.com")
    }

    // Cache cho các kết quả DNS đã xử lý
    private val dnsCache = ConcurrentHashMap<String, ByteArray>()

    companion object {
        private const val MTU = 1500
        private const val BUFFER_SIZE = 8192 // Tăng kích thước buffer
        private const val NOTIFICATION_ID = 6789
        private const val CHANNEL_ID = "dns_filter_channel"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX = 32
        private const val DNS_PORT = 53

        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpnService()
                START_NOT_STICKY
            }
            else -> {
                if (!isRunning) startVpnService()
                START_STICKY
            }
        }
    }

    private fun startVpnService() {
        try {
            startForegroundServiceWithType()
            setupVpnInterface()
            startPacketProcessing()
            isRunning = true
            Log.d(TAG, "VPN Service started")
        } catch (e: Exception) {
            Log.e(TAG, "Service start failed", e)
            stopSelf()
        }
    }

    private fun startForegroundServiceWithType() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupVpnInterface() {
        vpnInterface = Builder().apply {
            setMtu(MTU)
            addAddress(VPN_ADDRESS, VPN_PREFIX)

            // CHỈ chặn lưu lượng DNS, không chặn toàn bộ lưu lượng mạng
            // Điều này sẽ cải thiện hiệu suất đáng kể
            addRoute("0.0.0.0", 0)

            // Thêm DNS server
            addDnsServer("8.8.8.8") // Google DNS
            addDnsServer("1.1.1.1") // Cloudflare DNS

            // Các thiết lập quan trọng khác
            allowFamily(OsConstants.AF_INET) // Chỉ xử lý IPv4
            setBlocking(false) // Chế độ không chặn để tăng hiệu suất
            allowBypass() // Cho phép một số ứng dụng bỏ qua VPN
            setSession("Web Filter VPN")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false) // Không tính là dữ liệu đo lường
            }

            // Loại trừ ứng dụng hiện tại để tránh lặp vô hạn
            try {
                addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot exclude self from VPN", e)
            }
        }.establish()
    }

    private fun startPacketProcessing() {
        serviceScope.launch {
            val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
            val packet = ByteArray(BUFFER_SIZE)

            var errorCount = 0
            val maxErrors = 10

            while (isRunning && errorCount < maxErrors) {
                try {
                    val length = vpnInput.read(packet)
                    if (length <= 0) {
                        delay(10) // Đợi một chút nếu không có dữ liệu
                        continue
                    }

                    // Kiểm tra có phải là DNS packet
                    if (isDnsPacket(packet, length)) {
                        val response = processDnsPacket(packet, length)
                        if (response != null) {
                            vpnOutput.write(response)
                            continue // Đã xử lý xong, tiếp tục vòng lặp
                        }
                    }

                    // Chuyển tiếp gói tin nguyên bản nếu không phải DNS hoặc không cần chặn
                    vpnOutput.write(packet, 0, length)

                    // Reset bộ đếm lỗi nếu xử lý thành công
                    errorCount = 0

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet: ${e.message}")
                    errorCount++
                    delay(100) // Đợi một chút khi có lỗi
                }
            }

            if (errorCount >= maxErrors) {
                Log.e(TAG, "Too many errors, stopping VPN service")
                stopVpnService()
            }
        }
    }

    private fun isDnsPacket(packet: ByteArray, length: Int): Boolean {
        if (length < 28) return false // Gói tin quá nhỏ để là DNS

        try {
            // Kiểm tra version IP (chỉ xử lý IPv4)
            val version = packet[0].toInt() shr 4
            if (version != 4) return false

            // Tính toán độ dài header IP
            val ihl = packet[0].toInt() and 0x0F
            if (ihl < 5) return false
            val ipHeaderLength = ihl * 4

            // Kiểm tra protocol (17 = UDP)
            if (packet[9].toInt() != 17) return false

            // Kiểm tra port đích
            val dstPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                    (packet[ipHeaderLength + 3].toInt() and 0xFF)

            return dstPort == DNS_PORT
        } catch (e: Exception) {
            return false
        }
    }

    private fun processDnsPacket(packet: ByteArray, length: Int): ByteArray? {
        try {
            // Tìm vị trí bắt đầu của header IP
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            // Vị trí bắt đầu của data DNS (sau IP header và UDP header)
            val dnsStart = ipHeaderLength + 8

            if (length <= dnsStart + 12) return null // Không đủ dữ liệu

            // Phân tích gói tin DNS
            val dnsData = packet.copyOfRange(dnsStart, length)
            val dnsMessage = Message(dnsData)

            // Kiểm tra xem có phải là truy vấn DNS không
            if (dnsMessage.header.getFlag(Flags.QR.toInt())) {
                return null // Đây là response, không phải query
            }

            val question = dnsMessage.getQuestion()
            if (question == null) return null

            // Lấy tên miền đang được truy vấn
            val domain = question.name.toString().removeSuffix(".").toLowerCase()
            Log.d(TAG, "DNS query for domain: $domain")

            // Kiểm tra xem domain có nằm trong danh sách cần chặn không
            if (shouldBlockDomain(domain)) {
                Log.d(TAG, "Blocking domain: $domain")

                // Kiểm tra cache trước
                val cachedResponse = dnsCache[domain]
                if (cachedResponse != null) {
                    return cachedResponse
                }

                // Tạo response DNS trả về 0.0.0.0
                val response = "hello"//dnsMessage.Copy()
                response.header.setFlag(Flags.QR.toInt()) // Đánh dấu là response

                // Thêm section trả lời
                val record = Record.fromString(
                    question.name,
                    Type.A,
                    DClass.IN,
                    300, // TTL 300 giây
                    "0.0.0.0",
                    null
                )
                response.addRecord(record, Section.ANSWER)

                // Chuyển đổi response thành byte array
                val responseData = response.toWire()

                // Tạo gói tin IP và UDP hoàn chỉnh
                val fullResponse = createDnsResponsePacket(packet, responseData, length)

                // Lưu vào cache
                dnsCache[domain] = fullResponse

                return fullResponse
            }

            // Không chặn, trả về null để chuyển tiếp gói tin gốc
            return null

        } catch (e: Exception) {
            Log.e(TAG, "Error processing DNS packet: ${e.message}")
            return null // Trong trường hợp lỗi, cho phép gói tin đi qua
        }
    }

    /**
     * Tạo gói tin DNS response hoàn chỉnh bao gồm IP và UDP header
     */
    private fun createDnsResponsePacket(original: ByteArray, dnsResponse: ByteArray, originalLength: Int): ByteArray {
        // Tạo bản sao của gói tin gốc
        val response = original.copyOf(originalLength)

        // Tính toán vị trí các header
        val ipHeaderLength = (original[0].toInt() and 0x0F) * 4
        val udpHeaderLength = 8
        val dnsStart = ipHeaderLength + udpHeaderLength

        // Đảo nguồn/đích trong IP header
        // Đổi IP nguồn và đích
        for (i in 12..15) {
            val temp = response[i]
            response[i] = response[i + 4]
            response[i + 4] = temp
        }

        // Đảo port nguồn/đích trong UDP header
        for (i in 0..1) {
            val temp = response[ipHeaderLength + i]
            response[ipHeaderLength + i] = response[ipHeaderLength + 2 + i]
            response[ipHeaderLength + 2 + i] = temp
        }

        // Tính toán độ dài mới của UDP
        val newUdpLength = udpHeaderLength + dnsResponse.size
        response[ipHeaderLength + 4] = (newUdpLength shr 8).toByte()
        response[ipHeaderLength + 5] = (newUdpLength and 0xFF).toByte()

        // Xóa checksum UDP để hệ thống tính lại
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        // Tính toán độ dài mới của gói tin IP
        val newTotalLength = ipHeaderLength + newUdpLength
        response[2] = (newTotalLength shr 8).toByte()
        response[3] = (newTotalLength and 0xFF).toByte()

        // Xóa IP checksum để hệ thống tính lại
        response[10] = 0
        response[11] = 0

        // Đặt cờ QR (là response thay vì query)
        response[dnsStart] = response[dnsStart] or 0x80.toByte()

        // Sao chép dữ liệu DNS mới vào gói tin
        System.arraycopy(dnsResponse, 0, response, dnsStart, dnsResponse.size)

        return response.copyOf(ipHeaderLength + newUdpLength)
    }

    private fun shouldBlockDomain(domain: String): Boolean {
        // Không chặn các domain hệ thống quan trọng
        for (systemDomain in systemDomains) {
            if (domain.endsWith(systemDomain)) {
                return false
            }
        }

        // Kiểm tra trong danh sách domain bị chặn
        for (blockedDomain in blockedDomains) {
            if (domain == blockedDomain || domain.endsWith(".$blockedDomain")) {
                return true
            }
        }

        return false
    }

    private fun createNotification(): Notification {
        // Tạo intent để mở ứng dụng khi nhấn vào thông báo
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Tạo intent để dừng dịch vụ
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, WebFilterVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bộ lọc web đang hoạt động")
            .setContentText("Đang bảo vệ khỏi nội dung không phù hợp")
            .setSmallIcon(R.drawable.ic_vpn)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Dừng", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dịch vụ bộ lọc web",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dịch vụ lọc nội dung web không phù hợp"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun stopVpnService() {
        if (isRunning) {
            isRunning = false
            serviceScope.coroutineContext.cancel()
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(true)
            stopSelf()

            // Xóa cache để giải phóng bộ nhớ
            dnsCache.clear()

            Log.d(TAG, "VPN Service stopped")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

 */
}