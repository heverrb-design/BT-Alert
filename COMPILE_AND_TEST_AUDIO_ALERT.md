# INSTRUCCIONES DE COMPILACIÓN Y PRUEBA - Alerta de Audio Bluetooth

## 📋 Resumen de Cambios

Se han realizado las siguientes mejoras para solucionar el problema de que **la alerta no suena cuando el dispositivo Bluetooth se desconecta**:

### Archivos Modificados:
1. **`BluetoothMonitorService.kt`**
   - Agregada función `playAlarmAudio()` con reintentos y fallback
   - Mejorada `startAlarmSequence()` con mejor control de volumen
   - Agregados logs detallados en todos los puntos críticos
   - Agregada importación de `RingtoneManager` y `Log`

2. **`BluetoothReceiver.kt`**
   - Agregado TAG constante para logs
   - Agregados logs detallados en `onReceive()`
   - Mejor diagnosticabilidad de eventos Bluetooth

### Sin cambios:
- `MainActivity.kt` - ✅ Sin modificaciones necesarias
- `AndroidManifest.xml` - ✅ Sin modificaciones necesarias
- `BootReceiver.kt` - ✅ Sin modificaciones necesarias
- Todos los recursos (sonidos, layouts, strings) - ✅ Sin cambios

---

## 🛠️ PASO 1: Compilación

### Requisitos Previos:
- ✅ Android Studio instalado (cualquier versión reciente)
- ✅ Java SDK 11+ instalado
- ✅ Gradle sincronizado correctamente

### Compilar:

**Opción A: Con Android Studio (RECOMENDADO)**
```
1. Abre el proyecto en Android Studio
2. Espera a que Gradle se sincronice
3. Build > Build Project (Ctrl+F9)
4. Espera a que termine (sin errores)
```

**Opción B: Con terminal/PowerShell**
```powershell
cd D:\AndroidStudioProjects\BTAlert
./gradlew.bat clean build
# O en WSL/Git Bash:
./gradlew clean build
```

### Verificar que compile correctamente:
```
BUILD SUCCESSFUL (o similar)
Total time: XX.XXs
```

---

## 📱 PASO 2: Instalar en Dispositivo/Emulador

### Opción A: Android Studio
```
1. Run > Run 'app' (Shift+F10)
2. Selecciona el dispositivo o emulador
3. La app se instalará automáticamente
```

### Opción B: Terminal
```powershell
./gradlew.bat installDebug
# O:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Verificar instalación:
```powershell
adb shell pm list packages | findstr btalert
# Deberías ver: com.example.btalert
```

---

## 🧪 PASO 3: Preparación para Prueba

### En el dispositivo:

1. **Abre la app BTAlert**
2. **Otorga TODOS los permisos que solicita**:
   - ✅ Bluetooth
   - ✅ Ubicación (si solicita)
   - ✅ Notificaciones
   - ✅ Mostrar sobre otras apps

3. **Ve a Configuración > Aplicaciones > BTAlert**
   - ✅ Verifica que todos los permisos estén "Permitidos"

4. **Empareja un dispositivo Bluetooth** (ej: auricular, smartwatch)
   - Configuración > Bluetooth > Dispositivos disponibles
   - Selecciona tu dispositivo

5. **En la app BTAlert**:
   - Toca el botón de configuración (engranaje)
   - Selecciona el dispositivo Bluetooth que emparejaste
   - Presiona "Guardar configuración"

6. **Aumenta el volumen del dispositivo**:
   - Toca los botones de volumen
   - Asegúrate de que el "volumen de alarma" esté al máximo

---

## ▶️ PASO 4: Prueba de la Alerta

### En Android Studio - Abre Logcat:

```
1. View > Tool Windows > Logcat
2. O: Shift + Alt + 6
3. En el campo de filtro (arriba a la derecha):
   - Escribe: BluetoothMonitorService|BluetoothReceiver
```

### Ejecutar la prueba:

```
1. En la app BTAlert, presiona el botón verde "INICIAR MONITOREO"
   - Deberías ver: "Estado: ACTIVO" (verde)
   - Deberías ver en Logcat:
     D/BluetoothMonitorService: onStartCommand: called with action=null
     D/BluetoothMonitorService: onCreate: service created (si es primera vez)

2. Espera 5-10 segundos para que el servicio se estabilice

3. **DESCONECTA el dispositivo Bluetooth**:
   - Apaga el dispositivo BT, O
   - Alejar más de 10 metros (fuera de rango), O
   - En Configuración > Bluetooth > toca el dispositivo > "Olvidar" (y vuelve a emparejar después)

4. **Observa los logs en Logcat**:
   - Deberías ver dentro de ~3 segundos:

```

### Logs ESPERADOS ✅:

```
D/BluetoothReceiver: onReceive: action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothReceiver: onReceive: device=XX:XX:XX:XX:XX:XX, action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothMonitorService: onStartCommand: called with action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothMonitorService: handleBluetoothEvent: action=android.bluetooth.device.action.ACL_DISCONNECTED
D/BluetoothMonitorService: handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected for target device
D/BluetoothMonitorService: startAlarmSequence: called, isAlarmPlaying=false
D/BluetoothMonitorService: startAlarmSequence: setting ringer mode to NORMAL
D/BluetoothMonitorService: startAlarmSequence: set STREAM_ALARM to max volume=15
D/BluetoothMonitorService: playAlarmAudio: attempt 1/3
D/BluetoothMonitorService: playAlarmAudio: setting data source from URI=android.resource://com.example.btalert/2130903040
D/BluetoothMonitorService: playAlarmAudio: media prepared
D/BluetoothMonitorService: playAlarmAudio: playback started successfully
```

### ¿Qué debería pasar?

**Simultáneamente con los logs anteriores**:
- 🔊 **Suena una alarma fuerte y continua**
- 📱 **Vibración continua**
- 💡 **Flash (luz) parpadeando** (si está habilitado en configuración)
- 🔔 **Notificación en la barra de estado**
- 📺 **Pantalla se enciende** (si estaba bloqueada)

---

## 🔍 PASO 5: Diagnosticar Problemas

### Problema: No ves NINGÚN log de Bluetooth

```
Significado: El evento de desconexión no se está generando

Solución:
1. Verifica que el dispositivo BT esté REALMENTE emparejado
2. Intenta desconectar de una forma diferente:
   - Apaga el dispositivo BT completamente
   - Intenta alejarte del dispositivo (fuera de rango)
3. Si aún así no ves logs, el hardware BT podría estar dañado
```

### Problema: Ves logs de BluetoothReceiver pero NO de BluetoothMonitorService

```
Significado: El evento llegó a BluetoothReceiver pero no al servicio

Solución:
1. Verifica que el servicio esté ejecutándose:
   adb shell dumpsys activity services | findstr BluetoothMonitorService
   
2. Verifica los permisos:
   Configuración > Aplicaciones > BTAlert > Permisos > ver cada uno
   
3. Reinicia la app:
   adb shell am force-stop com.example.btalert
   Luego abre la app manualmente
```

### Problema: Ves logs pero NO se reproduce audio

```
Logs que ves:
D/BluetoothMonitorService: playAlarmAudio: attempt 1/3
E/BluetoothMonitorService: playAlarmAudio: failed to create MediaPlayer from resource

Significado: El archivo alerta_fuerte.mp3 no existe o es inválido

Solución:
1. Verifica que el archivo existe:
   app/src/main/res/raw/alerta_fuerte.mp3
   
2. Si NO existe:
   - Copia un MP3 válido a ese directorio
   - Renumébralo como alerta_fuerte.mp3
   - Recompila: ./gradlew clean build
   
3. Si existe pero aún así falla:
   - Prueba con un MP3 diferente (más pequeño o de buena calidad)
   - Algunos MP3 pueden tener formatos incompatibles
```

### Problema: Ves "playback started successfully" pero NO suena

```
Logs que ves:
D/BluetoothMonitorService: playAlarmAudio: playback started successfully
<pero no suena>

Posibles causas:
1. Volumen de alarma en cero
2. Modo silencioso activado
3. Problema con los altavoces del dispositivo

Soluciones:
1. Aumenta el volumen manualmente:
   - Presiona botones de volumen en el dispositivo
   - Abre Configuración > Sonidos > Volumen de Alarma > Máximo
   
2. Desactiva modo silencioso:
   - Algunos teléfonos tienen un switch físico
   - Verifica que "No molestar" esté desactivado
   
3. Prueba con la alarma del reloj:
   - Abre Reloj > Alarmas > crea una alarma para ahora+1min
   - Si suena la alarma del reloj pero no la app, el problema es la configuración
```

### Problema: El audio suena una sola vez y luego se detiene

```
Significado: El isLooping=true no está funcionando, O la alarma se detiene

Solución:
Busca en logs:
D/BluetoothMonitorService: stopAlarm: called

Si ves este log poco después de startAlarmSequence, significa que algo está
deteniendo la alarma prematuramente. Esto podría ser:
1. El dispositivo se reconecta inmediatamente (reconecta automáticamente)
2. Un error en el código que no vimos

En ese caso, reporta los logs completos para investigar más.
```

---

## 📊 Tabla de Referencia Rápida

| Situación | Log a buscar | Acción |
|-----------|--------------|--------|
| ¿Se detectó la desconexión? | `ACL_DISCONNECTED` | Si no aparece, problema en BT |
| ¿Llegó al servicio? | `onStartCommand: called with action=` | Si no, problema en registro |
| ¿Se inició la alarma? | `startAlarmSequence: called` | Si no, problema en handleBluetoothEvent |
| ¿Se intentó reproducir? | `playAlarmAudio: attempt` | Si no, problema en scheduling |
| ¿Se reproduce el audio? | `playback started successfully` | Si no, problema con archivo o MediaPlayer |
| ¿Se detiene la alarma? | `stopAlarm: called` | Si aparece inmediatamente, problema en reconexión |

---

## ✅ Verificación Final

Después de compilar e instalar, verifica:

- [ ] La app compila sin errores
- [ ] La app se instala correctamente en el dispositivo
- [ ] Puedes iniciar monitoreo sin crashes
- [ ] Los logs de `onStartCommand` aparecen cuando inicias monitoreo
- [ ] Cuando desconectas el BT, ves los logs de `ACL_DISCONNECTED`
- [ ] Ves logs de `playAlarmAudio`
- [ ] Escuchas la alarma sonando
- [ ] La vibración funciona
- [ ] El flash parpadea (si está habilitado)
- [ ] La notificación aparece

---

## 🚀 Próximos Pasos

Si todo funciona:
1. ✅ **EXITO**: La solución funcionó
2. Prueba con diferentes dispositivos BT para verificar consistencia
3. Prueba con la pantalla bloqueada/encendida
4. Prueba con audífonos conectados

Si algo no funciona:
1. Recopila los logs completos de Logcat (copy-paste todo)
2. Sigue los pasos de diagnosticar problemas arriba
3. Si aún no funciona, reporta con los logs

---

## 💡 Notas Técnicas

- El delay de 3000ms (3 segundos) es intencional para evitar alarmas falsas por reconexiones rápidas
- El audio se reproduce en el stream `STREAM_ALARM`, no en `STREAM_RING` o `STREAM_MUSIC`
- El stream ALARM es independiente del modo silencioso (ese es el punto - la alarma suena aunque esté silenciado)
- El `volumeLockRunnable` verifica el volumen cada 2 segundos para evitar que el usuario lo baje durante la alarma

---

## 📞 Contacto y Soporte

Si después de seguir todos estos pasos la alerta aún no funciona:

1. Recopila TODOS los logs:
   ```powershell
   adb logcat > logs.txt
   # Desconecta el BT
   # Espera 10 segundos
   # Presiona Ctrl+C para detener
   ```

2. Comparte el archivo `logs.txt` junto con:
   - Modelo del dispositivo
   - Versión de Android
   - Modelo del dispositivo Bluetooth que probaste
   - Pasos exactos que realizaste

---

**Última actualización**: 2025-02-25  
**Versión**: 1.0  
**Estado**: ✅ Listo para compilar y probar

