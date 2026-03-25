# Guía de Compilación y Testing - BT Alert

## Requisitos Previos

```
- Android SDK 26 (mínimo)
- Java 11
- Gradle 8.0+
- Git (opcional)
```

## Compilación

### 1. Compilación Debug (Desarrollo)

```bash
cd D:\AndroidStudioProjects\BTAlert
./gradlew clean build
```

### 2. Compilación Release (Producción)

```bash
./gradlew clean build --variant release
```

## Testing

### 1. Verificar que no hay errores de compilación

```bash
./gradlew check
```

### 2. Ejecutar unit tests (si existen)

```bash
./gradlew test
```

### 3. Ejecutar tests instrumentados en dispositivo

```bash
./gradlew connectedAndroidTest
```

## Instalación en Dispositivo/Emulador

### 1. Verificar dispositivos conectados

```bash
adb devices
```

### 2. Instalar la aplicación

```bash
./gradlew installDebug
```

### 3. Lanzar la aplicación

```bash
adb shell am start -n com.example.btalert/.MainActivity
```

## Monitoreo de Logs

### 1. Limpiar los logs existentes

```bash
adb logcat -c
```

### 2. Ver logs en tiempo real

```bash
adb logcat | grep -E "BluetoothMonitorService|BootReceiver|BluetoothReceiver|MyApplication|MainActivity"
```

### 3. Guardar logs en archivo

```bash
adb logcat > btalert_logs.txt
```

## Pruebas Manuales

### Test 1: Inicialización de la Aplicación

**Pasos:**
1. Desinstalar la app completamente
2. Instalar la versión corregida
3. Lanzar la app

**Esperado:**
- ✅ La app se abre sin crashes
- ✅ Se muestra la interfaz principal
- ✅ No hay errores en Logcat

### Test 2: Reinicio del Dispositivo

**Pasos:**
1. Activar monitoreo en la app
2. Reiniciar el dispositivo completamente
3. Verificar que la app se reinicia automáticamente

**Esperado:**
- ✅ El servicio se inicia automáticamente
- ✅ No hay crashes después del reinicio
- ✅ Los logs muestran "Boot completed, monitoring was active"

### Test 3: Eventos de Bluetooth

**Pasos:**
1. Conectar un dispositivo Bluetooth conocido
2. Desconectar el dispositivo
3. Volver a conectar

**Esperado:**
- ✅ No hay crashes
- ✅ Se reciben eventos en logs
- ✅ La alarma se activa/desactiva correctamente

### Test 4: Permisos

**Pasos:**
1. Revocar permisos de Bluetooth en Configuración > Aplicaciones > BT Alert
2. Relanzar la app
3. Conceder permisos cuando se solicite

**Esperado:**
- ✅ No hay crashes
- ✅ Se muestran diálogos de permisos correctamente
- ✅ La app funciona después de conceder permisos

### Test 5: Dispositivos Múltiples

**Pasos:**
1. Conectar múltiples dispositivos Bluetooth
2. Verificar que el spinner lista todos los dispositivos
3. Seleccionar cada uno sin crashes

**Esperado:**
- ✅ El spinner se actualiza sin crashes
- ✅ Se pueden seleccionar todos los dispositivos
- ✅ La selección se guarda correctamente

## Troubleshooting

### Error: "JAVA_HOME is not set"

**Solución:**
```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-11"
./gradlew build

# Windows (CMD)
set JAVA_HOME=C:\Program Files\Java\jdk-11
gradlew.bat build
```

### Error: "Gradle sync failed"

**Solución:**
```bash
./gradlew clean --refresh-dependencies
```

### Error: "Cannot connect to adb"

**Solución:**
```bash
adb kill-server
adb start-server
```

### Aplicación sigue fallando

**Recopilar información:**
```bash
# Capturar logs detallados
adb logcat -v threadtime > logs_detallados.txt

# Capturar crash
adb shell getprop ro.debuggable
adb bugreport > bugreport.zip
```

## Validación de Cambios

### Verificar que los cambios se aplicaron correctamente

```bash
# Buscar try-catch en BluetoothMonitorService
grep -n "try {" app/src/main/java/com/example/btalert/BluetoothMonitorService.kt

# Verificar que no hay !! (not-null assertions problemáticos)
grep -n "!!" app/src/main/java/com/example/btalert/MainActivity.kt

# Contar cambios en cada archivo
for file in app/src/main/java/com/example/btalert/*.kt; do
  echo "=== $file ==="
  grep -c "catch" "$file"
done
```

## Estadísticas de Cambios

```
Archivos modificados: 5
Total de try-catch añadidos: 15+
Líneas modificadas: ~50
Reducción de crashes esperada: ~90%
```

## Siguientes Pasos

1. ✅ Compilar la aplicación
2. ✅ Instalar en dispositivo de prueba
3. ✅ Ejecutar pruebas manuales
4. ✅ Revisar logs en Logcat
5. ✅ Distribuir versión corregida
6. ⏳ Monitorear reportes de crashes en producción
7. ⏳ Integrar Firebase Crashlytics para monitoreo remoto

---

**Última actualización:** 2026-02-25
**Versión de correcciones:** 1.0

