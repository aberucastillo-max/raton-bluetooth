package com.tablet.raton

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.view.InputDevice
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

data class DispositivoBT(
    val direccion: String,
    val nombre: String,
    val tipo: String,          // Clásico / BLE / Dual
    val esRaton: Boolean,
    val esTeclado: Boolean,
    val esHid: Boolean,        // dispositivo de entrada (ratón, teclado, mando...)
    val clase: String,
    val rssi: Int?,
    val emparejado: Boolean,
)

/** Búsqueda Bluetooth (clásico + BLE) para diagnosticar ratones y teclados. */
@SuppressLint("MissingPermission")
class BluetoothDiag(private val ctx: Context) {
    val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val encontrados = LinkedHashMap<String, DispositivoBT>()
    var onCambio: ((List<DispositivoBT>) -> Unit)? = null
    var buscando = false
        private set
    private var registrado = false

    private val HID = ParcelUuid.fromString("00001812-0000-1000-8000-00805f9b34fb")

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt().takeIf { it != Short.MIN_VALUE.toInt() }
                    val cls = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                    anadir(dev, rssi, cls, apariencia = null, hidBle = false, viaBle = false)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val dev = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    encontrados[dev.address]?.let { encontrados[dev.address] = it.copy(emparejado = dev.bondState == BluetoothDevice.BOND_BONDED) }
                    publicar()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { /* el escaneo BLE sigue hasta detener() */ }
            }
        }
    }

    private val bleCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val rec = r.scanRecord
            val hid = rec?.serviceUuids?.contains(HID) == true
            anadir(r.device, r.rssi, r.device.bluetoothClass, apariencia(rec?.bytes), hid, viaBle = true)
        }
    }

    /** Lee el campo "Appearance" (tipo 0x19) del anuncio BLE. 0x03C2 = ratón, 0x03C1 = teclado. */
    private fun apariencia(b: ByteArray?): Int? {
        if (b == null) return null
        var i = 0
        while (i < b.size) {
            val len = b[i].toInt() and 0xFF
            if (len == 0 || i + len >= b.size) break
            val tipo = b[i + 1].toInt() and 0xFF
            if (tipo == 0x19 && len >= 3) return (b[i + 2].toInt() and 0xFF) or ((b[i + 3].toInt() and 0xFF) shl 8)
            i += len + 1
        }
        return null
    }

    private fun describir(cls: BluetoothClass?, apar: Int?, hidBle: Boolean): Triple<Boolean, Boolean, String> {
        var raton = false; var teclado = false; var txt = "Desconocido"
        if (cls != null) {
            val major = cls.majorDeviceClass
            val dc = cls.deviceClass
            txt = when (major) {
                BluetoothClass.Device.Major.PERIPHERAL -> {
                    raton = (dc and 0x80) != 0; teclado = (dc and 0x40) != 0
                    when { raton && teclado -> "Teclado + ratón"; raton -> "Ratón"; teclado -> "Teclado"; else -> "Periférico de entrada" }
                }
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio (auriculares/altavoz)"
                BluetoothClass.Device.Major.PHONE -> "Teléfono"
                BluetoothClass.Device.Major.COMPUTER -> "Ordenador"
                BluetoothClass.Device.Major.WEARABLE -> "Reloj / wearable"
                BluetoothClass.Device.Major.HEALTH -> "Salud"
                BluetoothClass.Device.Major.IMAGING -> "Impresora / cámara"
                BluetoothClass.Device.Major.TOY -> "Juguete"
                else -> txt
            }
        }
        when (apar) {
            0x03C2 -> { raton = true; txt = "Ratón (BLE)" }
            0x03C1 -> { teclado = true; txt = "Teclado (BLE)" }
            0x03C0, 0x03C3, 0x03C4 -> txt = "Dispositivo de entrada (BLE)"
        }
        if (hidBle && txt == "Desconocido") txt = "Dispositivo de entrada HID (BLE)"
        return Triple(raton, teclado, txt)
    }

    private fun anadir(dev: BluetoothDevice, rssi: Int?, cls: BluetoothClass?, apariencia: Int?, hidBle: Boolean, viaBle: Boolean) {
        val previo = encontrados[dev.address]
        val (raton, teclado, txt) = describir(cls, apariencia, hidBle)
        val tipoNuevo = if (viaBle) "BLE" else "Clásico"
        val tipo = when {
            previo == null -> tipoNuevo
            previo.tipo != tipoNuevo -> "Dual"
            else -> previo.tipo
        }
        val nombre = try { dev.name } catch (_: Exception) { null }
        encontrados[dev.address] = DispositivoBT(
            direccion = dev.address,
            nombre = nombre?.takeIf { it.isNotBlank() } ?: previo?.nombre ?: "(sin nombre)",
            tipo = tipo,
            esRaton = raton || previo?.esRaton == true,
            esTeclado = teclado || previo?.esTeclado == true,
            esHid = raton || teclado || hidBle || previo?.esHid == true || txt.contains("entrada"),
            clase = if (txt == "Desconocido" && previo != null) previo.clase else txt,
            rssi = rssi ?: previo?.rssi,
            emparejado = dev.bondState == BluetoothDevice.BOND_BONDED,
        )
        publicar()
    }

    private fun publicar() {
        onCambio?.invoke(encontrados.values.sortedWith(compareByDescending<DispositivoBT> { it.esRaton }.thenByDescending { it.esHid }.thenByDescending { it.rssi ?: -200 }))
    }

    fun iniciar() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        encontrados.clear(); publicar()
        if (!registrado) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(ctx, receptor, f, ContextCompat.RECEIVER_EXPORTED)
            registrado = true
        }
        try { if (a.isDiscovering) a.cancelDiscovery(); a.startDiscovery() } catch (_: Exception) {}
        try {
            a.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleCb)
        } catch (_: Exception) {}
        buscando = true
    }

    fun detener() {
        val a = adapter
        try { a?.cancelDiscovery() } catch (_: Exception) {}
        try { a?.bluetoothLeScanner?.stopScan(bleCb) } catch (_: Exception) {}
        buscando = false
    }

    fun liberar() {
        detener()
        if (registrado) { try { ctx.unregisterReceiver(receptor) } catch (_: Exception) {}; registrado = false }
    }

    fun emparejar(direccion: String): Boolean = try {
        detener()   // emparejar con la búsqueda activa suele fallar
        adapter?.getRemoteDevice(direccion)?.createBond() == true
    } catch (_: Exception) { false }

    fun emparejados(): List<DispositivoBT> = try {
        adapter?.bondedDevices?.map { d ->
            val (r, t, txt) = describir(d.bluetoothClass, null, false)
            DispositivoBT(d.address, d.name ?: "(sin nombre)", if (d.type == BluetoothDevice.DEVICE_TYPE_LE) "BLE" else "Clásico",
                r, t, r || t || txt.contains("entrada"), txt, null, true)
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    companion object {
        /** Ratones que Android tiene conectados ahora mismo como dispositivo de entrada. */
        fun ratonesActivos(): List<String> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.supportsSource(InputDevice.SOURCE_MOUSE) && !it.isVirtual && (Build.VERSION.SDK_INT < 29 || it.isExternal) }
            .map { it.name }

        fun tecladosActivos(): List<String> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && !it.isVirtual && (Build.VERSION.SDK_INT < 29 || it.isExternal) }
            .map { it.name }
    }
}
