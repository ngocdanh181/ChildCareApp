package com.example.childlocate.ui.child.sos.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class SOSTileService : TileService() {
    private val TAG = "SOSWidget12345TileService"

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Starting to listen for tile updates")

        // Cập nhật trạng thái của tile khi bắt đầu lắng nghe
        qsTile?.apply {
            // Kiểm tra xem SOS có đang hoạt động không
            val isSosActive = getSharedPreferences("SOS_DATA", MODE_PRIVATE)
                .getBoolean("is_sos_active", false)

            state = if (isSosActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Lấy trạng thái hiện tại
        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE
        Log.d(TAG, "SOS Quick Settings Tile clicked")
        if (currentState == Tile.STATE_INACTIVE) {
            // Nếu đang không hoạt động, kích hoạt SOS
            Log.d(TAG, "SOS Quick Settings Tile activated")

            //luu trang thai vao sharedPreferences
            getSharedPreferences("SOS_DATA", MODE_PRIVATE).edit()
                .putBoolean("is_sos_active", true)
                .apply()
            // Gửi tín hiệu SOS khi nhấn vào tile
            SOSService.startSendSOS(this)
            // Cập nhật trạng thái tile thành active
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                updateTile()
            }
        }else{
            //nếu đang hoạt động . hủy SOS
            Log.d(TAG, "SOS Quick Settings Tile deactivated")
            //luu trang thai vao sharedPreferences
            getSharedPreferences("SOS_DATA", MODE_PRIVATE).edit()
                .putBoolean("is_sos_active", false)
                .apply()
            // Hủy tín hiệu SOS khi nhấn vào tile
            SOSService.cancelSOS(this)
            // Cập nhật trạng thái tile thành inactive
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
        
    }
}