package com.klstudio.esp32connection.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

class BLEDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BLEDataSource {

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null
    private var onReceived: ((String) -> Unit)? = null

    private var targetName: String = ""
    private var scanResultDeferred: CompletableDeferred<Boolean>? = null

    val serviceUuid: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    val characteristicUuid: UUID = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890")

    @SuppressLint("ObsoleteSdkInt")
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }


    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }


    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasBluetoothPermissions()) return
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) { return }
            if (result.device.name == targetName) {
                targetDevice = result.device
                bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
                scanResultDeferred?.complete(true)
            }
        }
    }


    override suspend fun startScan(targetDeviceName: String): Boolean {
        if (!hasBluetoothPermissions()) return false

        targetName = targetDeviceName
        scanResultDeferred = CompletableDeferred()

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return false
        val scanFilters = listOf(
            ScanFilter.Builder().setDeviceName(targetDeviceName).build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) { return false }

        scanner.startScan(scanFilters, scanSettings, scanCallback)
        withTimeoutOrNull(10000L) {
            scanResultDeferred?.await()
        } ?: run {
            scanner.stopScan(scanCallback)
            return false
        }

        return targetDevice != null
    }


    private fun enableNotification(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID) {
        if (!hasBluetoothPermissions()) return
        val service = gatt.getService(serviceUuid) ?: return
        val characteristic = service.getCharacteristic(charUuid) ?: return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) { return }

        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        val enableNotificationValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, enableNotificationValue)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enableNotificationValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }


    override suspend fun connect(): Boolean {
        if (!hasBluetoothPermissions()) return false
        return suspendCancellableCoroutine { continuation ->
            val device = targetDevice ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) { return@suspendCancellableCoroutine }

            bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED
                        ) { return }
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        continuation.resume(false)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        //val serviceUuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
                        //val characteristicUuid = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890")
                        enableNotification(bluetoothGatt!!, serviceUuid, characteristicUuid)
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) { return }
                        gatt.requestMtu(517)
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    @Suppress("DEPRECATION")
                    val data = characteristic.getStringValue(0)
                    onReceived?.invoke(data)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    val data = value.toString(Charsets.UTF_8)
                    onReceived?.invoke(data)
                }
            })
        }
    }

    override suspend fun sendData(data: String): Boolean {
        if (!hasBluetoothPermissions()) return false
        val gatt = bluetoothGatt ?: return false

        val service = gatt.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        val value = data.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val status = gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                status == BluetoothStatusCodes.SUCCESS
            } catch (e: SecurityException) {
                false
            }

        } else {
            @Suppress("DEPRECATION")
            characteristic.setValue(value)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) { return false }
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    override fun setOnReceivedListener(listener: (String) -> Unit) {
        onReceived = listener
    }

    override fun disconnect() {
        if (hasBluetoothPermissions()) {
            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
            } catch (e: SecurityException) {
                Log.e("BLEDataSourceImpl", "BLE Permission Not Granted")
            }
        }
    }
}
