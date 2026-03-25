# FIX: Alerta de Audio - Dispositivo Desconectado

## Cambios Realizados

Se ha mejorado significativamente el sistema de alerta de audio del servicio `BluetoothMonitorService` para diagnosticar y solucionar el problema de que la alerta no suena cuando el dispositivo Bluetooth no está conectado.

### 1. **Mejoras en `startAlarmSequence()`**

#### Antes:
- Sin logs detallados
- Sin reintentos si fallaba el MediaPlayer
- Sin fallback a alternativas de reproducción

#### Ahora:
- ✅ **Logs detallados** para cada paso de la reproducción
- ✅ **Aumento garantizado del volumen** ANTES de reproducir
- ✅ **Fallback a RingtoneManager** si MediaPlayer falla con el recurso personalizado
- ✅ **Reintentos automáticos** (hasta 3 intentos) si la reproducción falla
- ✅ **Mejor control de volumen** con `volumeLockRunnable` que mantiene el volumen al máximo

```kotlin
fun playAlarmAudio() {
    // 1. Intenta reproducir con MediaPlayer usando el recurso personalizado
    // 2. Si falla, intenta con RingtoneManager (sonido de alarma del sistema)
    // 3. Si ambos fallan, lo reinenta después de 1 segundo
}
```

### 2. **Logs Mejorados en Todo el Servicio**

Se han agregado logs `Log.d()` y `Log.e()` en todos los puntos críticos:

- `onCreate()` - Inicialización del servicio
- `onStartCommand()` - Comandos recibidos
- `checkCurrentConnection()` - Verificación de conexión inicial
- `handleBluetoothEvent()` - Eventos Bluetooth detectados
- `startAlarmSequence()` - Secuencia de alerta
- `stopAlarm()` - Detención de alerta
- `playAlarmAudio()` - Reproducción de audio

### 3. **Control de Volumen Mejorado**

```kotlin
// Establecer ringer mode a NORMAL
audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

// Aumentar volumen al máximo ANTES de reproducir
val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, AudioManager.FLAG_SHOW_UI)

// Mantener el volumen bloqueado durante la alerta
// (volumeLockRunnable lo verifica cada 2 segundos)
```

## Flujo de Ejecución

```
1. Dispositivo Bluetooth se desconecta
                    ↓
2. BluetoothReceiver recibe ACTION_ACL_DISCONNECTED
                    ↓
3. BluetoothMonitorService.handleBluetoothEvent() es llamado
                    ↓
4. Se programa alarmRunnable con delay de 3000ms
                    ↓
5. startAlarmSequence() es ejecutado:
   - Establece ringer mode a NORMAL
   - Aumenta volumen al máximo
   - Inicia playAlarmAudio()
                    ↓
6. playAlarmAudio() intenta reproducir:
   a) MediaPlayer con recurso personalizado (alerta_fuerte.mp3)
   b) RingtoneManager con alarma del sistema (fallback)
   c) Reintentos automáticos si ambos fallan
                    ↓
7. Flash (si está habilitado) y vibración se inician simultáneamente
                    ↓
8. volumeLockRunnable verifica y mantiene el volumen máximo cada 2 segundos
```

## Cómo Depurar

### 1. **Ver los Logs en Android Studio**

```bash
# Terminal o Logcat:
adb logcat | grep BluetoothMonitorService
```

### 2. **Logs Importantes a Buscar**

Cuando se desconecte el dispositivo Bluetooth, busca:

```
D/BluetoothMonitorService: handleBluetoothEvent: action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothMonitorService: handleBluetoothEvent: targetMac=XX:XX:XX:XX:XX:XX
D/BluetoothMonitorService: handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected for target device
D/BluetoothMonitorService: startAlarmSequence: called
D/BluetoothMonitorService: startAlarmSequence: setting ringer mode to NORMAL
D/BluetoothMonitorService: startAlarmSequence: set STREAM_ALARM to max volume=15
D/BluetoothMonitorService: playAlarmAudio: attempt 1/3
D/BluetoothMonitorService: playAlarmAudio: setting data source from URI=...
D/BluetoothMonitorService: playAlarmAudio: playback started successfully
```

### 3. **Qué Significan los Logs**

| Log | Significado |
|-----|-------------|
| `handleBluetoothEvent: action=...ACL_DISCONNECTED` | ✅ La desconexión fue detectada |
| `startAlarmSequence: called` | ✅ Se inició la secuencia de alerta |
| `set STREAM_ALARM to max volume` | ✅ El volumen fue aumentado |
| `playAlarmAudio: playback started successfully` | ✅ El audio se está reproduciendo |
| `playAlarmAudio: failed to create MediaPlayer` | ⚠️ El recurso personalizado falló (pero hay fallback) |
| `playAlarmAudio: fallback ringtone playing successfully` | ✅ Se está usando el sonido del sistema |
| `playAlarmAudio: max attempts reached, giving up` | ❌ **PROBLEMA**: El audio no se pudo reproducir |

## Checklist de Pruebas

- [ ] **Compilar**: Asegurar que no hay errores de compilación
  - Los logs `Log.d()` se compilan correctamente
  - Las importaciones de `Log` y `RingtoneManager` están presentes

- [ ] **Instalar la app** en un dispositivo o emulador

- [ ] **Emparejar un dispositivo Bluetooth**

- [ ] **Iniciar monitoreo** desde la app

- [ ] **Desconectar el dispositivo Bluetooth** (apagar, alejar, etc.)

- [ ] **Observar los logs**:
  - Abre Android Studio o usa `adb logcat`
  - Busca `BluetoothMonitorService` en el filtro
  - Verifica que veas los logs de `ACTION_ACL_DISCONNECTED`

- [ ] **Escuchar la alerta**:
  - La alarma debe sonar dentro de ~3 segundos de la desconexión
  - Si no suena, revisar los logs para ver dónde falla

- [ ] **Comprobar efectos secundarios**:
  - ✅ El flash debe parpadecer (si está habilitado)
  - ✅ El teléfono debe vibrar
  - ✅ La pantalla debe encenderse (si estaba bloqueada)
  - ✅ La notificación debe aparecer

## Si Todavía No Suena la Alerta

### Posibles Causas y Soluciones

1. **El archivo de audio no existe**
   - Verifica que `app/src/main/res/raw/alerta_fuerte.mp3` existe
   - Si no, copia un archivo MP3 válido a esa ubicación

2. **Los permisos Bluetooth no están concedidos**
   - Abre Configuración > Aplicaciones > BTAlert
   - Concede permisos de Bluetooth, Notificaciones y Cámara

3. **El modo silencioso está activado**
   - El archivo MP3 se reproduce en el stream `STREAM_ALARM`
   - El modo silencioso debería estar deshabilitado en ese stream
   - Pero verifica que no haya excepciones en los logs

4. **El MediaPlayer falla pero RingtoneManager también**
   - Esto es muy raro, pero posible si el sistema de audio está dañado
   - Los logs lo mostrarán claramente

5. **La desconexión no se detecta**
   - BluetoothReceiver debe recibir el evento ACTION_ACL_DISCONNECTED
   - Busca en los logs si ves `handleBluetoothEvent: action=`
   - Si no ves ese log, el evento no llegó al servicio

## Cambios en el Código

### Archivo: `BluetoothMonitorService.kt`

```diff
+ import android.media.RingtoneManager
+ import android.util.Log

class BluetoothMonitorService : Service() {
  companion object {
+     private const val TAG = "BluetoothMonitorService"
  }

+   private var alarmPlayAttempts = 0
+   private val maxAlarmPlayAttempts = 3

+   private fun playAlarmAudio() {
+       // Nueva función con reintentos y fallback
+   }

-   private fun startAlarmSequence() {
+   private fun startAlarmSequence() {
        // Mejorado con logs y mejor control de volumen
    }

-   private fun stopAlarm() {
+   private fun stopAlarm() {
        // Mejorado con logs
    }

-   private fun handleBluetoothEvent() {
+   private fun handleBluetoothEvent() {
        // Mejorado con logs detallados
    }
}
```

## Notas Importantes

⚠️ **Los logs solo aparecen si la app está compilada en modo DEBUG**
- Si compilas en RELEASE, algunos logs pueden estar optimizados
- Pero los logs críticos siempre deberían estar presentes

⚠️ **El volumen del alarm stream es independiente del volumen del ringer**
- Esto es correcto: queremos que la alarma suene aunque el teléfono esté en silencio
- A menos que el usuario haya configurado el stream ALARM en silencio específicamente

⚠️ **Los permisos BLUETOOTH_CONNECT son necesarios**
- Sin ellos, no se puede detectar la desconexión del dispositivo
- Si faltan permisos, la app solicitará concederlos en el primer inicio

## Próximos Pasos

1. **Compilar**: `./gradlew build` (con Java configurado)
2. **Instalar**: `./gradlew installDebug` o arrastra el APK al emulador
3. **Probar**: Sigue el checklist de pruebas arriba
4. **Debugging**: Usa los logs para diagnosticar cualquier problema
5. **Reportar**: Si la alerta sigue sin sonar, comparte los logs

---

**Fecha de cambios**: 2025-02-25  
**Versión de la solución**: 1.0

