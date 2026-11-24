# Ratón Bluetooth (Galaxy Tab S6 Lite)

App Android para diagnosticar y emparejar ratones y teclados Bluetooth que la tablet no detecta.

- **Buscar ratón**: búsqueda Bluetooth clásica + BLE durante 20 s; identifica ratones y teclados por su clase Bluetooth y por el anuncio BLE (Appearance / servicio HID).
- **Emparejar** directamente desde la lista.
- **Conectados ahora**: muestra si Android ya reconoce un ratón/teclado como dispositivo de entrada.
- **Diagnóstico automático**: indica si el problema está en el ratón (no emite / modo 2.4G / conectado a otro aparato) o en la tablet.
- Guía paso a paso para poner el ratón en modo emparejamiento.

## Instalar
Descarga `RatonBluetooth.apk` desde **Releases** en la tablet e instálalo. Concede el permiso "Dispositivos cercanos".

## Compilar
GitHub Actions compila el APK en cada push (`.github/workflows/apk.yml`).
