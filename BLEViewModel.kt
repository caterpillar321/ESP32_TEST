package com.klstudio.esp32connection

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klstudio.esp32connection.data.BLEDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.sql.DataSource

@HiltViewModel
class BLEViewModel @Inject constructor(
    private val bleDataSource: BLEDataSource
) : ViewModel() {
    private val _receivedData = MutableStateFlow<String>("")
    val receivedData = _receivedData.asStateFlow()

    private val _isScanning = MutableStateFlow<Boolean>(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isConnected = MutableStateFlow<Boolean>(false)
    val isConnected = _isConnected.asStateFlow()

    init {
        bleDataSource.setOnReceivedListener { data ->
            _receivedData.value = data.toString()
        }
    }

    fun scanAndConnect(targetName: String) {
        viewModelScope.launch {
            _isScanning.value = true
            val found = bleDataSource.startScan(targetName)
            _isScanning.value = false

            if (found) {
                val success = bleDataSource.connect()
                _isConnected.value = success
            } else {
                _isConnected.value = false
            }
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch {
            val success = bleDataSource.sendData(text)
            if (!success) {
                Log.w("BLEViewModel", "Failed to Connect")
            }
        }
    }

    fun disconnect() {
        bleDataSource.disconnect()
        _isConnected.value = false
    }

}
