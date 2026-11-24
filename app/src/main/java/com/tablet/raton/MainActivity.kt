package com.tablet.raton

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Fondo = Color(0xFF15171C)
private val Tarjeta = Color(0xFF22262F)
private val Texto = Color(0xFFE6E8EE)
private val Gris = Color(0xFF9BA1AE)
private val Azul = Color(0xFF4C9AFF)
private val Verde = Color(0xFF46C46A)
private val Naranja = Color(0xFFF5A524)
private val Rojo = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Azul, background = Fondo, surface = Tarjeta)) {
                Box(Modifier.fillMaxSize().background(Fondo)) { Pantalla() }
            }
        }
    }
}

private fun permisosNecesarios(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@SuppressLint("MissingPermission")
@Composable
fun Pantalla() {
    val ctx = LocalContext.current
    val diag = remember { BluetoothDiag(ctx.applicationContext) }
    var tienePermisos by remember {
        mutableStateOf(permisosNecesarios().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED })
    }
    var btActivo by remember { mutableStateOf(diag.adapter?.isEnabled == true) }
    var encontrados by remember { mutableStateOf<List<DispositivoBT>>(emptyList()) }
    var emparejados by remember { mutableStateOf<List<DispositivoBT>>(emptyList()) }
    var ratones by remember { mutableStateOf<List<String>>(emptyList()) }
    var teclados by remember { mutableStateOf<List<String>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var busquedaHecha by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tienePermisos = r.values.all { it }
    }
    val pedirActivar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btActivo = diag.adapter?.isEnabled == true
    }

    DisposableEffect(Unit) {
        diag.onCambio = { encontrados = it }
        onDispose { diag.liberar() }
    }
    // Refresco continuo: estado del Bluetooth, emparejados y ratones conectados
    LaunchedEffect(tienePermisos) {
        while (true) {
            btActivo = diag.adapter?.isEnabled == true
            if (tienePermisos) emparejados = diag.emparejados()
            ratones = BluetoothDiag.ratonesActivos()
            teclados = BluetoothDiag.tecladosActivos()
            delay(2000)
        }
    }
    LaunchedEffect(buscando) {
        if (buscando) { delay(20_000); diag.detener(); buscando = false; busquedaHecha = true }
    }

    fun buscar() {
        if (!tienePermisos) { pedirPermisos.launch(permisosNecesarios()); return }
        if (!btActivo) { pedirActivar.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return }
        aviso = null
        diag.iniciar(); buscando = true
    }

    // ---------------- diagnóstico
    val ratonVisto = encontrados.filter { it.esRaton || (it.esHid && !it.esTeclado) }
    val ratonEmparejado = emparejados.filter { it.esRaton || (it.esHid && !it.esTeclado) }
    val (colorDiag, textoDiag) = when {
        diag.adapter == null -> Rojo to "Esta tablet no tiene Bluetooth disponible."
        !tienePermisos -> Naranja to "Concede el permiso de \"Dispositivos cercanos\" para poder buscar el ratón."
        !btActivo -> Naranja to "El Bluetooth está apagado. Actívalo para buscar el ratón."
        ratones.isNotEmpty() -> Verde to "¡Funciona! Android reconoce el ratón: ${ratones.joinToString()}. Si no ves el puntero, mueve el ratón o reinicia DeX."
        ratonEmparejado.isNotEmpty() -> Naranja to "El ratón ${ratonEmparejado.joinToString { it.nombre }} está emparejado pero NO conectado. Apágalo y enciéndelo. " +
            "Si sigue igual, ábrelo en Ajustes de Bluetooth > Desvincular y vuelve a emparejarlo desde aquí."
        buscando -> Azul to "Buscando… pon el ratón en modo emparejamiento ahora (luz parpadeando rápido)."
        ratonVisto.isNotEmpty() -> Verde to "Se detecta el ratón ${ratonVisto.first().nombre}. Pulsa \"Emparejar\" en la lista."
        busquedaHecha && encontrados.isEmpty() -> Rojo to "No se encontró ningún dispositivo Bluetooth. Revisa que el Bluetooth de la tablet funcione (prueba con unos auriculares)."
        busquedaHecha -> Rojo to "La tablet ve otros dispositivos Bluetooth pero NO el ratón. El problema está en el ratón: no está en modo emparejamiento, " +
            "está conectado a otro aparato, tiene poca batería o el interruptor está en modo 2.4G en lugar de BT. Sigue la guía de abajo."
        else -> Gris to "Pulsa \"Buscar ratón\" con el ratón en modo emparejamiento."
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Ratón Bluetooth", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Texto)
            Text("Diagnóstico y emparejamiento de ratones y teclados inalámbricos", fontSize = 14.sp, color = Gris)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Tarjeta), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(colorDiag))
                        Spacer(Modifier.width(10.dp))
                        Text("Diagnóstico", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Texto)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(textoDiag, fontSize = 16.sp, color = Texto)
                    aviso?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 14.sp, color = Naranja) }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { buscar() }, enabled = !buscando) {
                            Text(if (buscando) "Buscando…" else "Buscar ratón", fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Ajustes de Bluetooth") }
                    }
                    if (buscando) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Tarjeta), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Conectados ahora como entrada", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Texto)
                    Text("Ratones: " + (ratones.joinToString().ifBlank { "ninguno" }), fontSize = 15.sp,
                        color = if (ratones.isEmpty()) Gris else Verde)
                    Text("Teclados: " + (teclados.joinToString().ifBlank { "ninguno" }), fontSize = 15.sp,
                        color = if (teclados.isEmpty()) Gris else Verde)
                }
            }
        }
        if (encontrados.isNotEmpty()) {
            item { Text("Dispositivos encontrados (${encontrados.size})", fontSize = 15.sp, color = Gris) }
            items(encontrados, key = { "f" + it.direccion }) { d ->
                FilaDispositivo(d, onEmparejar = {
                    buscando = false
                    if (!diag.emparejar(d.direccion)) aviso = "No se pudo iniciar el emparejamiento con ${d.nombre}. Pon el ratón otra vez en modo emparejamiento y reinténtalo."
                    else aviso = "Emparejando con ${d.nombre}… acepta la solicitud si aparece."
                })
            }
        }
        if (emparejados.isNotEmpty()) {
            item { Text("Emparejados en esta tablet (${emparejados.size})", fontSize = 15.sp, color = Gris) }
            items(emparejados, key = { "e" + it.direccion }) { d -> FilaDispositivo(d, onEmparejar = null) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Tarjeta), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Cómo poner el ratón en modo emparejamiento\n\n" +
                    "1. Mira la base del ratón: si tiene un interruptor con \"BT\" y \"2.4G\", ponlo en BT (o BT1/BT2).\n" +
                    "2. Enciéndelo y mantén pulsado el botón de conexión (a veces bajo la rueda o en la base) 3–5 s, hasta que la luz parpadee RÁPIDO.\n" +
                    "3. Si no tiene botón, mantén pulsados a la vez el clic derecho + la rueda unos 5 s.\n" +
                    "4. Si alguna vez lo usaste con un móvil o PC, apaga el Bluetooth de ese aparato: el ratón se reconecta a él.\n" +
                    "5. Ponle pilas nuevas o cárgalo: con poca batería muchos ratones no se emparejan.\n" +
                    "6. Ten el ratón a menos de 1 metro y pulsa \"Buscar ratón\".\n\n" +
                    "Si tras esto no aparece en la lista pero sí otros dispositivos, el ratón no está emitiendo: puede estar averiado o no ser compatible con Android.",
                    color = Texto, fontSize = 15.sp, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun FilaDispositivo(d: DispositivoBT, onEmparejar: (() -> Unit)?) {
    val destacar = d.esRaton || d.esHid
    Card(
        colors = CardDefaults.cardColors(containerColor = if (d.esRaton) Color(0xFF1C2E22) else Tarjeta),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.nombre, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (destacar) Verde else Texto)
                val senal = d.rssi?.let { r -> " · señal " + when { r > -60 -> "fuerte"; r > -80 -> "media"; else -> "débil" } + " ($r dBm)" } ?: ""
                Text("${d.clase} · ${d.tipo}$senal", fontSize = 13.sp, color = Gris)
                Text(d.direccion + if (d.emparejado) " · emparejado" else "", fontSize = 12.sp, color = Gris)
            }
            if (onEmparejar != null && !d.emparejado) {
                Button(onClick = onEmparejar,
                    colors = ButtonDefaults.buttonColors(containerColor = if (destacar) Color(0xFF24865A) else Color(0xFF2F3542))) {
                    Text("Emparejar")
                }
            }
        }
    }
}
