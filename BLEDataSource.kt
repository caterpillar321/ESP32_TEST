package com.klstudio.esp32connection.data

interface BLEDataSource {
    suspend fun startScan(targetDeviceName: String): Boolean
    suspend fun connect(): Boolean
    suspend fun sendData(data: String): Boolean
    fun setOnReceivedListener(listener: (String) -> Unit)
    fun disconnect()
}
