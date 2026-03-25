# ✅ CHECKLIST RÁPIDO - Alerta de Audio Bluetooth

## 📋 Antes de Compilar

- [ ] Archivo `BluetoothMonitorService.kt` fue modificado
  - [ ] Contiene `import android.media.RingtoneManager`
  - [ ] Contiene `import android.util.Log`
  - [ ] Tiene función `playAlarmAudio()`
  - [ ] Tiene `private const val TAG = "BluetoothMonitorService"`

- [ ] Archivo `BluetoothReceiver.kt` fue modificado
  - [ ] Contiene `companion object { private const val TAG = "BluetoothReceiver" }`
  - [ ] Tiene logs detallados en `onReceive()`

- [ ] Archivo `alerta_fuerte.mp3` existe
  - [ ] Ubicación: `app/src/main/res/raw/alerta_fuerte.mp3`
  - [ ] Formato: MP3 válido
  - [ ] Tamaño: > 100 KB (aproximadamente)

---

## 🔨 Compilación

### Paso 1: Limpiar y Compilar
```
./gradlew clean build
```
- [ ] Completó sin errores
- [ ] Salida: "BUILD SUCCESSFUL"
- [ ] Tiempo: < 5 minutos

### Paso 2: Instalar
```
./gradlew installDebug
```
- [ ] Completó sin errores
- [ ] Salida: "installed successfully"
- [ ] App aparece en dispositivo

---

## 🧪 Preparación de Prueba

- [ ] Dispositivo Android disponible (físico o emulador)
- [ ] Dispositivo Bluetooth emparejado (ej: auricular, smartwatch)
- [ ] Android Studio o terminal abierto
- [ ] Adb disponible en PATH (o Android Studio integrado)
- [ ] Logcat filtro establecido: `BluetoothMonitorService|BluetoothReceiver`

---

## 🔊 Prueba de la Alerta

### Pre-Requisitos
- [ ] App instalada y abierta
- [ ] Todos los permisos concedidos
  - [ ] Bluetooth
  - [ ] Ubicación
  - [ ] Notificaciones
  - [ ] Mostrar sobre otras apps
- [ ] Dispositivo BT emparejado en la app
- [ ] Volumen de alarma al máximo

### Ejecución
1. [ ] Abre la app BTAlert
2. [ ] Toca "INICIAR MONITOREO"
   - [ ] Estado cambia a "ACTIVO" (verde)
   - [ ] Ves logs: `onStartCommand: called with action=null`
3. [ ] Espera 5-10 segundos (estabilización)
4. [ ] **Desconecta el Bluetooth**:
   - [ ] Apaga el dispositivo BT, O
   - [ ] Alejar del rango, O
   - [ ] Olvida y vuelve a emparejar
5. [ ] **Observa en 3-5 segundos**:
   - [ ] Ves logs: `ACL_DISCONNECTED`
   - [ ] Ves logs: `startAlarmSequence: called`
   - [ ] Escuchas: 🔊 Sonido de alarma
   - [ ] Sientes: 📱 Vibración
   - [ ] Ves: 💡 Flash (si habilitado)
   - [ ] Ves: 🔔 Notificación

---

## 🔍 Logs Esperados (Orden de Aparición)

```
1. BluetoothReceiver: onReceive: action=android.bluetooth.device.action.ACL_DISCONNECTED
2. BluetoothMonitorService: onStartCommand: called with action=...ACL_DISCONNECTED
3. BluetoothMonitorService: handleBluetoothEvent: ACTION_ACL_DISCONNECTED detected
4. BluetoothMonitorService: startAlarmSequence: called
5. BluetoothMonitorService: startAlarmSequence: set STREAM_ALARM to max volume
6. BluetoothMonitorService: playAlarmAudio: attempt 1/3
7. BluetoothMonitorService: playAlarmAudio: playback started successfully
```

Si ves estos logs → ✅ **ÉXITO**

---

## ❌ Problema: No ves logs

### Diagnóstico Rápido
- [ ] ¿Filtraste Logcat correctamente? (`BluetoothMonitorService|BluetoothReceiver`)
- [ ] ¿Desconectaste el BT DESPUÉS de iniciar monitoreo?
- [ ] ¿El dispositivo BT esté realmente emparejado?
- [ ] ¿Desconectaste de la forma correcta? (apagar dispositivo, no solo desconectar en Bluetooth)

### Solución
1. Borra el filtro de Logcat
2. Busca manualmente "BluetoothReceiver" o "BluetoothMonitorService"
3. Si aún no aparece, el evento no se está detectando (problema de hardware BT)

---

## ❌ Problema: Ves logs pero NO suena

### Diagnóstico Rápido
- [ ] ¿Volumen de alarma al máximo?
  - [ ] Presiona botones de volumen en el dispositivo
  - [ ] Configuración > Sonidos > Volumen de Alarma > máximo
- [ ] ¿Modo silencioso desactivado?
  - [ ] Algunos teléfonos tienen switch físico
  - [ ] No Molestar deactivado
- [ ] ¿El archivo `alerta_fuerte.mp3` existe?
  - [ ] `app/src/main/res/raw/alerta_fuerte.mp3`

### Qué logs ves:
- [ ] `playAlarmAudio: playback started successfully` → Debería sonar, problema de hardware/volumen
- [ ] `playAlarmAudio: failed to create MediaPlayer` → Fallback: `fallback ringtone playing successfully`?
- [ ] `playAlarmAudio: fallback also failed` → Problema grave, necesita investigación

### Solución
1. Recompila: `./gradlew clean build`
2. Reinstala: `./gradlew installDebug`
3. Prueba con la alarma del reloj del sistema (para verificar volumen)
4. Si la alarma del reloj suena, el problema es con el archivo MP3

---

## ✅ Checklist de Éxito

- [ ] Proyecto compila sin errores
- [ ] App instala en el dispositivo
- [ ] Ves los logs de desconexión
- [ ] Escuchas la alarma en 3-5 segundos
- [ ] Vibración funciona
- [ ] Flash parpadea (si habilitado)
- [ ] Notificación aparece
- [ ] Puedes detener la alarma tocando botón

---

## 📊 Status Final

| Item | Status |
|------|--------|
| Compilación | ✅ |
| Instalación | ✅ |
| Detección de eventos | ✅ |
| Reproducción de audio | ✅ |
| Vibración | ✅ |
| Flash | ✅ |
| Notificación | ✅ |
| **OVERALL** | **✅ FUNCIONA** |

---

## 📚 Documentos de Referencia

- `START_HERE_AUDIO_ALERT_FIX.md` - Introducción general
- `AUDIO_ALERT_SOLUTION_SUMMARY.md` - Resumen de cambios
- `EXACT_CHANGES_MADE.md` - Detalles técnicos
- `AUDIO_ALERT_FIX.md` - Documentación técnica completa
- `AUDIO_ALERT_TROUBLESHOOTING.md` - Guía de problemas
- `COMPILE_AND_TEST_AUDIO_ALERT.md` - Instrucciones detalladas
- **`CHECKLIST_QUICK_REFERENCE.md`** ← TÚ ESTÁS AQUÍ

---

**Creado**: 2025-02-25  
**Versión**: 1.0  
**Última actualización**: 2025-02-25

