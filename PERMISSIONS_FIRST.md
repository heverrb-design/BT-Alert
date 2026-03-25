# ✅ PERMISOS SOLICITADOS EN PRIMER INICIO

## Problema Original
Los permisos se solicitaban DESPUÉS de mostrar la pantalla de configuración. El usuario debería ver los diálogos de permisos PRIMERO.

## ✅ Solución Implementada

Se reorganizó el flujo en `onCreate()` de MainActivity para que:

### 1️⃣ PRIMER PASO: Solicitar Permiso "Mostrar sobre otras aplicaciones"
```kotlin
if (!Settings.canDrawOverlays(this)) {
    // Mostrar diálogo para ir a Configuración
    // Esto es CRÍTICO para que las alarmas funcionen
}
```

### 2️⃣ SEGUNDO PASO: Solicitar Permisos de Bluetooth y Notificaciones
```kotlin
requestBluetoothAndNotificationPermissions()
// - Bluetooth Connect (Android 12+)
// - Bluetooth Scan (Android 12+)
// - Post Notifications (Android 13+)
// - Location (Android 11 y anteriores)
```

### 3️⃣ TERCER PASO: Cargar dispositivos y mostrar Configuración
```kotlin
checkPermissionsAndLoadDevices()
checkFirstTimeConfig()  // Mostrar diálogo de configuración
```

---

## 📋 Nuevo Flujo en Primer Inicio

```
APP INICIA
    ↓
¿Es la primera ejecución?
    ├─ SÍ → Solicitar Permisos
    │       ├─ 1. Permiso "Mostrar sobre aplicaciones"
    │       ├─ 2. Permiso Bluetooth
    │       ├─ 3. Permiso Notificaciones
    │       └─ 4. Mostrar Diálogo de Configuración
    │
    └─ NO → Solicitar solo permisos faltantes
            └─ Cargar dispositivos y mostrar UI
```

---

## 🔧 Cambios en el Código

### Archivo: `MainActivity.kt`

#### En `onCreate()`:
```kotlin
// Detectar si es primera ejecución
val isFirstTime = sharedPrefs.getString("selected_device_mac", null) == null

if (isFirstTime) {
    // SOLICITAR TODOS LOS PERMISOS PRIMERO
    requestAllPermissionsFirstTime()
} else {
    // Ejecuciones posteriores: solo permisos faltantes
    checkPermissionsAndLoadDevices()
}
```

#### Nuevos Métodos:
```kotlin
✅ requestAllPermissionsFirstTime()
   - Solicita "Mostrar sobre otras aplicaciones" primero
   - Luego solicita Bluetooth y Notificaciones

✅ requestBluetoothAndNotificationPermissions()
   - Agrupa la solicitud de permisos de Bluetooth
   - Incluye permiso POST_NOTIFICATIONS
```

#### Mejorado `checkFirstTimeConfig()`:
```kotlin
// Ahora simplemente muestra el diálogo si es primera vez
// Sin verificaciones de permisos (ya fueron solicitados)
```

---

## 📱 Flujo Que Verá el Usuario (Primera Ejecución)

### 1. App Abre
- Se carga la UI
- Se inicia la carga de dispositivos

### 2. Diálogo: "Necesito un permiso"
```
Necesito un permiso

Para que pueda avisarte automáticamente al 
conectar un dispositivo Bluetooth, necesito que 
actives el permiso 'Mostrar sobre otras 
aplicaciones'.

[CONFIGURAR] [MÁS TARDE]
```
→ Si toca CONFIGURAR, lo lleva a Configuración de Sistema
→ Cuando regresa, continúa

### 3. Diálogo: "Permisos necesarios"
```
Permisos necesarios

La aplicación necesita los siguientes permisos:
• Bluetooth: Para conectar y monitorear dispositivos
• Notificaciones: Para alertarte cuando se desconecte

[PERMITIR] [CANCELAR]
```
→ Se solicitan permisos en tiempo de ejecución

### 4. Diálogo: "Selecciona un dispositivo"
```
Selecciona un dispositivo Bluetooth para monitorear
[Dropdown con dispositivos]
[Guardar]
```
→ El usuario elige el dispositivo a monitorear

---

## 🧪 Pruebas Recomendadas

### Prueba 1: Primera Ejecución (App Limpia)
```
1. Desinstalar app completamente
2. Instalar versión nueva
3. Abrir app
4. Verificar que aparezcan diálogos en orden:
   ✅ "Necesito un permiso" (overlay)
   ✅ "Permisos necesarios" (Bluetooth + Notificaciones)
   ✅ "Selecciona dispositivo" (configuración)
5. Conceder todos los permisos
6. Configurar dispositivo
```

### Prueba 2: Segunda Ejecución
```
1. Cerrar app
2. Abrirla de nuevo
3. Verificar que NO muestra diálogos de permisos
   (ya fueron concedidos)
4. Mostrar directamente la UI
```

### Prueba 3: Revocar Permiso y Reabriry
```
1. Ir a Configuración > Aplicaciones > BT Alert
2. Revocar permiso "Post Notifications"
3. Abrir app
4. Verificar que solicita el permiso revocado
```

---

## ✨ Beneficios

✅ **Usuario vé permisos PRIMERO** - No hay confusión  
✅ **Flujo lógico** - Permisos → Configuración → UI  
✅ **Mejor UX** - Todos los permisos en los primeros pasos  
✅ **Sin sorpresas** - El usuario sabe qué va a pasar  

---

## 🚀 Compilar y Probar

```bash
# 1. Compilar
./gradlew clean build

# 2. Instalar (desinstalar primero para prueba limpia)
adb uninstall com.example.btalert
./gradlew installDebug

# 3. Abrir app y verificar flujo
adb shell am start -n com.example.btalert/.MainActivity

# 4. Ver logs
adb logcat | grep -i "MainActivity\|Permission"
```

---

## 📊 Orden de Permisos Solicitados

| Paso | Permiso | Tipo | Android |
|------|---------|------|---------|
| 1 | SYSTEM_ALERT_WINDOW | Setting | 6+ |
| 2 | BLUETOOTH_CONNECT | Runtime | 12+ |
| 3 | BLUETOOTH_SCAN | Runtime | 12+ |
| 4 | ACCESS_FINE_LOCATION | Runtime | <12 |
| 5 | POST_NOTIFICATIONS | Runtime | 13+ |

---

**Archivo modificado:** `MainActivity.kt`  
**Métodos nuevos:** 2  
**Métodos mejorados:** 2  
**Líneas de código:** ~100  
**Impacto:** Alto (mejor UX en primer inicio)

