# Diagnóstico y Solución del Problema de Alerta de Audio

## Problema Reportado
> "La app reinicia correctamente antes de desbloquear el teléfono y en modo activo pero no suena la alerta a pesar que el dispositivo bluetooth no esta conectado."

## Resumen de la Solución

Se han realizado mejoras significativas en los componentes de reproducción de audio y logging para diagnosticar y solucionar el problema:

### 1. **Mejoras en `BluetoothMonitorService.kt`**

#### `startAlarmSequence()` - Secuencia de alerta mejorada
- ✅ Establece `ringer mode` a `NORMAL` (no silencio)
- ✅ Aumenta el volumen del stream `ALARM` al máximo
- ✅ Inicia `volumeLockRunnable` para mantener volumen máximo
- ✅ Llama a nueva función `playAlarmAudio()`

#### `playAlarmAudio()` - Nueva función para reproducción de audio con reintentos
- ✅ **Intento 1**: Reproduce MP3 personalizado (`alerta_fuerte.mp3`)
- ✅ **Intento 2** (fallback): Si falla, usa sonido de alarma del sistema
- ✅ **Intento 3+**: Reintentos automáticos hasta 3 veces
- ✅ Logs detallados en cada paso

#### Logs agregados
```
BluetoothMonitorService:
  - onCreate()
  - onStartCommand()
  - checkCurrentConnection()
  - handleBluetoothEvent()
  - startAlarmSequence()
  - playAlarmAudio()
  - stopAlarm()
```

### 2. **Mejoras en `BluetoothReceiver.kt`**

- ✅ TAG constante para logs consistentes
- ✅ Logs detallados en `onReceive()`
- ✅ Confirmación de eventos `ACTION_ACL_DISCONNECTED`
- ✅ Confirmación de forward al servicio

## Flujo de Ejecución Actual

```
Dispositivo BT se desconecta
        ↓
BluetoothReceiver.onReceive() recibe ACTION_ACL_DISCONNECTED
        ↓ (Log: "onReceive: action=android.bluetooth.device.action.ACL_DISCONNECTED")
        ↓
Inicia BluetoothMonitorService con el evento
        ↓ (Log: "onReceive: startForegroundService called")
        ↓
BluetoothMonitorService.onStartCommand() recibe el intent
        ↓ (Log: "onStartCommand: called with action=...")
        ↓
BluetoothMonitorService.handleBluetoothEvent() procesa el evento
        ↓ (Log: "handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected")
        ↓
Se programa alarmRunnable con delay de 3000ms
        ↓
startAlarmSequence() es ejecutado
        ↓ (Log: "startAlarmSequence: called")
        ↓
playAlarmAudio() intenta reproducir:
  a) MediaPlayer con MP3 personalizado
  b) RingtoneManager (fallback)
  c) Reintentos automáticos
        ↓ (Log: "playAlarmAudio: playback started successfully" O error)
        ↓
Flash + Vibración + Notificación + Pantalla encendida
```

## Pasos de Debugging

### Paso 1: Verificar que el audio se detecta correctamente

```bash
# En Android Studio, abre Logcat
# Establece filtro: "BluetoothMonitorService|BluetoothReceiver"

# Desconecta el dispositivo BT y observa los logs:
# Deberías ver:
# D/BluetoothReceiver: onReceive: action=android.bluetooth.device.action.ACL_DISCONNECTED
# D/BluetoothMonitorService: handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected
```

### Paso 2: Verificar que se intenta reproducir el audio

```bash
# Busca en los logs:
# D/BluetoothMonitorService: playAlarmAudio: attempt 1/3
# D/BluetoothMonitorService: playAlarmAudio: setting data source from URI=...

# Si ves estos logs, el sistema está intentando reproducir
# Si NO los ves, hay un problema anterior en la cadena de eventos
```

### Paso 3: Verificar si el audio se reproduce exitosamente

```bash
# Busca uno de estos logs:

# EXITO:
# D/BluetoothMonitorService: playAlarmAudio: playback started successfully

# FALLBACK (también funciona):
# D/BluetoothMonitorService: playAlarmAudio: fallback ringtone playing successfully

# ERROR:
# E/BluetoothMonitorService: playAlarmAudio: failed to create MediaPlayer from resource
# E/BluetoothMonitorService: playAlarmAudio: fallback also failed
```

## Problemas y Soluciones

### PROBLEMA 1: No ves logs de BluetoothReceiver
**Causa posible**: El evento ACTION_ACL_DISCONNECTED no se está generando
**Solución**:
1. Verifica que el dispositivo BT esté realmente emparejado
2. Intenta desconectar el dispositivo BT (apágalo, no solo desconecta el Bluetooth del teléfono)
3. Si aún así no ves logs, el problema podría estar en el hardware BT del dispositivo

### PROBLEMA 2: Ves logs de desconexión pero NO de startAlarmSequence
**Causa posible**: El evento no llega a handleBluetoothEvent() correctamente
**Solución**:
1. Verifica que el MAC del dispositivo coincide con el configurado
2. Busca en los logs: "handleBluetoothEvent: targetMac=" para confirmar
3. Si el MAC no coincide, reconfigurar el dispositivo en la app

### PROBLEMA 3: Ves "startAlarmSequence: called" pero NO se reproduce audio
**Causa posible**: 
- El archivo `alerta_fuerte.mp3` no existe o es inválido
- Problema con MediaPlayer
- El stream ALARM está en silencio

**Solución**:
```bash
# Verificar que el archivo existe:
ls -la app/src/main/res/raw/alerta_fuerte.mp3

# Si no existe, copiar un MP3 válido:
cp /ruta/a/tu/sonido.mp3 app/src/main/res/raw/alerta_fuerte.mp3

# Luego recompilar y reinstalar
./gradlew clean build
./gradlew installDebug
```

### PROBLEMA 4: Ves "playback started successfully" pero NO suena
**Causa posible**: 
- El volumen del stream ALARM está en cero
- El teléfono está en modo silencio Y el app no está forzando el volumen correctamente
- Problema con los altavoces del dispositivo

**Solución**:
1. Abre Configuración > Sonidos
2. Aumenta el volumen de Alarma (no Ringer)
3. Desactiva cualquier "modo silencioso" específico para alarmas
4. Prueba con otro sonido (ej: alarma del reloj del sistema)

## Verificaciones Finales

Antes de compilar, asegurate de:

- [ ] El archivo `BluetoothMonitorService.kt` contiene:
  - [ ] Import de `android.media.RingtoneManager`
  - [ ] Import de `android.util.Log`
  - [ ] `private const val TAG = "BluetoothMonitorService"`
  - [ ] Función `playAlarmAudio()` completa
  - [ ] Función `startAlarmSequence()` mejorada

- [ ] El archivo `BluetoothReceiver.kt` contiene:
  - [ ] `companion object { private const val TAG = "BluetoothReceiver" }`
  - [ ] Logs detallados en `onReceive()`

- [ ] El archivo `alerta_fuerte.mp3` existe en `app/src/main/res/raw/`

- [ ] El AndroidManifest.xml tiene:
  - [ ] `<receiver android:name=".BluetoothReceiver" android:exported="true">`
  - [ ] Intent filters para `ACTION_ACL_CONNECTED` y `ACTION_ACL_DISCONNECTED`

## Compilación y Prueba

```bash
# 1. Compilar
./gradlew clean build

# 2. Instalar en emulador o dispositivo
./gradlew installDebug

# 3. Abrir Logcat en Android Studio
# o: adb logcat | grep -E "BluetoothMonitorService|BluetoothReceiver"

# 4. Iniciar la app en el dispositivo
# 5. Emparejar un dispositivo BT
# 6. Iniciar monitoreo
# 7. Desconectar el dispositivo BT
# 8. Observar los logs y escuchar la alerta
```

## Esperado vs Actual

### ESPERADO ✅
```
D/BluetoothReceiver: onReceive: action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothMonitorService: handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected
D/BluetoothMonitorService: startAlarmSequence: called
D/BluetoothMonitorService: playAlarmAudio: playback started successfully
<sonido de alarma fuerte y continuo>
```

### ACTUAL ❌ (antes de la solución)
```
<sin logs detallados>
<sin sonido>
```

## Notas Importantes

⚠️ **Los logs aparecen en Logcat con TAG `BluetoothMonitorService` y `BluetoothReceiver`**
- Si no ves logs, asegúrate de:
  1. Filtrar por `BluetoothMonitorService|BluetoothReceiver`
  2. La app esté compilada en modo DEBUG (es el default)
  3. El dispositivo esté conectado por USB o en emulador

⚠️ **El stream ALARM es INDEPENDIENTE del Ringer**
- Esto es correcto por diseño
- La alarma sonará aunque el teléfono esté en modo silencio
- A menos que específicamente silencies el stream ALARM

⚠️ **El delay de 3000ms (3 segundos) es intencional**
- Permite que se estabilice la conexión BT antes de alertar
- Si desconectas y reconectas rápidamente, la alarma se cancela

## Archivos Modificados

```
app/src/main/java/com/example/btalert/
├── BluetoothMonitorService.kt (MODIFICADO)
│   └── Mejoras: logs, playAlarmAudio(), startAlarmSequence(), stopAlarm(), etc.
├── BluetoothReceiver.kt (MODIFICADO)
│   └── Mejoras: TAG, logs detallados
└── MainActivity.kt (sin cambios)
```

## Contacto y Soporte

Si después de aplicar estos cambios y seguir los pasos de debugging la alerta aún no suena:

1. **Recopila los logs completos** de Logcat cuando desconectes el dispositivo
2. **Verifica que compiles sin errores**: `./gradlew clean build`
3. **Reinstala la app**: `./gradlew uninstallDebug installDebug`
4. **Prueba en un dispositivo diferente** si es posible

---

**Última actualización**: 2025-02-25  
**Versión de solución**: 1.0  
**Status**: ✅ Listo para implementar

