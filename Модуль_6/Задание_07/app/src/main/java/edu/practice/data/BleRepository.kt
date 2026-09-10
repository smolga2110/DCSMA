package edu.practice.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import edu.practice.domain.HeartRateParser
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Device(val name: String, val address: String)

data class BleState(
    val devices: List<Device> = emptyList(),
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val heartRate: Int? = null,
    val status: String = "Disconnected",
)

@SuppressLint("MissingPermission")
class BleRepository(ctx: Context) {
    private val context = ctx.applicationContext
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timeout: Job? = null
    val state = MutableStateFlow(BleState())
    private val serviceId = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val heartId = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val cccId = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun permissionDenied() {
        state.update { it.copy(status = "Доступ к Bluetooth запрещён") }
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val device = result.device
                val row =
                    Device(
                        device.name ?: result.scanRecord?.deviceName ?: "Без имени",
                        device.address,
                    )
                state.update {
                    it.copy(
                        devices =
                            (it.devices.filterNot { d -> d.address == row.address } + row)
                                .sortedBy { d -> d.name }
                    )
                }
            }

            override fun onScanFailed(code: Int) {
                state.update { it.copy(scanning = false, status = "Ошибка сканирования: $code") }
            }
        }
    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (g !== gatt) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    state.update {
                        it.copy(connected = false, status = "Ошибка подключения: $status")
                    }
                    g.close()
                    gatt = null
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state.update { it.copy(connected = true, status = "Обнаружение сервисов…") }
                    if (!g.discoverServices())
                        state.update { it.copy(status = "Не удалось запросить сервисы") }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close()
                    gatt = null
                    state.update {
                        it.copy(connected = false, heartRate = null, status = "Disconnected")
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (g !== gatt) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    state.update { it.copy(status = "Ошибка обнаружения GATT") }
                    return
                }
                val ch = g.getService(serviceId)?.getCharacteristic(heartId)
                if (ch == null) {
                    state.update { it.copy(status = "Heart Rate Service не найден") }
                    return
                }
                val descriptor = ch.getDescriptor(cccId)
                if (descriptor == null || !g.setCharacteristicNotification(ch, true)) {
                    state.update { it.copy(status = "Уведомления недоступны") }
                    return
                }
                val accepted =
                    if (Build.VERSION.SDK_INT >= 33)
                        g.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                        ) == BluetoothStatusCodes.SUCCESS
                    else {
                        @Suppress("DEPRECATION") descriptor.value =
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION") g.writeDescriptor(descriptor)
                    }
                if (!accepted) state.update { it.copy(status = "Не удалось включить уведомления") }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                d: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (g === gatt)
                    state.update {
                        it.copy(
                            status =
                                if (status == BluetoothGatt.GATT_SUCCESS)
                                    "Connected — ожидание измерения"
                                else "Ошибка CCC: $status"
                        )
                    }
            }

            @Deprecated("Legacy callback")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < 33) {
                    @Suppress("DEPRECATION") val value = c.value
                    receive(g, c, value)
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                receive(g, c, value)
            }
        }

    private fun receive(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
        if (g !== gatt || c.uuid != heartId) return
        val bpm = HeartRateParser.parse(value)
        state.update {
            it.copy(
                heartRate = bpm,
                status = if (bpm != null) "Connected" else "Некорректный пакет Heart Rate",
            )
        }
    }

    fun scan() {
        try {
            if (adapter == null) {
                state.update { it.copy(status = "BLE не поддерживается") }
                return
            }
            if (!adapter.isEnabled) {
                state.update { it.copy(status = "Включите Bluetooth") }
                return
            }
            stopScan()
            state.update {
                it.copy(devices = emptyList(), scanning = true, status = "Сканирование…")
            }
            adapter.bluetoothLeScanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            timeout =
                scope.launch {
                    delay(15000)
                    stopScan()
                    state.update {
                        if (!it.connected) it.copy(status = "Сканирование завершено") else it
                    }
                }
        } catch (e: SecurityException) {
            permissionDenied()
        } catch (e: Exception) {
            state.update { it.copy(scanning = false, status = "Ошибка Bluetooth") }
        }
    }

    fun stopScan() {
        timeout?.cancel()
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        state.update { it.copy(scanning = false) }
    }

    fun connect(address: String) {
        try {
            stopScan()
            disconnect()
            state.update { it.copy(status = "Connecting") }
            gatt =
                adapter
                    ?.getRemoteDevice(address)
                    ?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            state.update { it.copy(status = "Не удалось подключиться") }
        }
    }

    fun disconnect() {
        gatt?.let {
            gatt = null
            try {
                it.disconnect()
            } finally {
                it.close()
            }
        }
        state.update { it.copy(connected = false, heartRate = null, status = "Disconnected") }
    }

    fun close() {
        stopScan()
        try {
            disconnect()
        } catch (_: Exception) {}
        scope.cancel()
    }
}
