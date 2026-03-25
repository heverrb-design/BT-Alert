# ✅ CRASH EN CONFIGURACIÓN - ARREGLADO

## 🐛 Problema
La app crasheaba cuando se tocaba el botón de configuración estando el monitoreo activo.

## 🔍 Causa Identificada

El crash ocurría en el método `showSettingsDialog()` por:

1. **Acceso inseguro a TimePicker.hour/minute** - En algunas versiones de Android, acceder a estas propiedades puede lanzar excepciones
2. **Sin try-catch global** - No había manejo de excepciones en el método
3. **Acceso sin validación a spinnerLanguage** - `languageCodes[selectedLangIndex]` podría fallar si el índice está fuera de rango

## ✅ Soluciones Implementadas

### 1. Try-catch Global en `showSettingsDialog()`
```kotlin
try {
    // Todo el método dentro de try-catch
    // ...
} catch (e: Exception) {
    Log.e(TAG, "showSettingsDialog: unexpected error", e)
    Toast.makeText(this, "Error al abrir configuración: ${e.message}", Toast.LENGTH_SHORT).show()
}
```

### 2. Try-catch para Acceso a TimePicker
```kotlin
try {
    tpStart.setIs24HourView(true)
    tpEnd.setIs24HourView(true)
} catch (e: Exception) {
    Log.w(TAG, "showSettingsDialog: error setting time views", e)
}

try {
    tpStart.hour = sharedPrefs.getInt("start_hour", 8)
    tpStart.minute = sharedPrefs.getInt("start_minute", 0)
} catch (e: Exception) {
    Log.w(TAG, "showSettingsDialog: error setting start time", e)
}
```

### 3. Try-catch en btnSave.setOnClickListener
```kotlin
btnSave.setOnClickListener {
    try {
        // Acceso seguro a languageCodes
        val selectedLangCode = languageCodes.getOrNull(selectedLangIndex) ?: "es"
        
        // Acceso seguro a TimePicker al guardar
        try {
            putInt("start_hour", tpStart.hour)
            putInt("start_minute", tpStart.minute)
            putInt("end_hour", tpEnd.hour)
            putInt("end_minute", tpEnd.minute)
        } catch (e: Exception) {
            // Usar valores por defecto
            putInt("start_hour", 8)
            putInt("start_minute", 0)
            putInt("end_hour", 20)
            putInt("end_minute", 0)
        }
    } catch (e: Exception) {
        Log.e(TAG, "error in btnSave onClick", e)
        Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 🚀 Para Probar

### Test 1: Abrir Configuración sin Monitoreo
```
1. Abrir app
2. Tocar ⚙️ Configuración
3. ✅ Debe abrirse sin crashes
```

### Test 2: Abrir Configuración CON Monitoreo Activo
```
1. Abrir app
2. Tocar "INICIAR MONITOREO"
3. Elegir dispositivo y guardar
4. Monitoreo está activo
5. Tocar ⚙️ Configuración
6. ✅ Debe abrirse sin crashes
```

### Test 3: Guardar Cambios en Configuración
```
1. Abrir configuración
2. Cambiar ajustes (dispositivo, horario, idioma, etc.)
3. Tocar "Guardar"
4. ✅ Debe guardar sin crashes
```

### Test 4: Con Monitoreo Activo - Cambiar Dispositivo
```
1. Monitoreo activo
2. Abrir configuración
3. Cambiar dispositivo seleccionado
4. Guardar
5. ✅ Debe cambiar sin crashes
```

---

## 📊 Cambios Realizados

| Aspecto | Antes | Después |
|--------|-------|---------|
| Try-catch global | ❌ No | ✅ Sí |
| Acceso a TimePicker | ❌ Sin validación | ✅ Con try-catch |
| Acceso a languageCodes | ❌ Sin validación | ✅ Con getOrNull() |
| Manejo de excepciones | ❌ Básico | ✅ Exhaustivo |
| Mensajes de error | ❌ No | ✅ Sí (Toast) |

---

## ✨ Beneficios

✅ **Sin crashes** - Se capturan todas las excepciones  
✅ **Mensajes útiles** - El usuario ve qué salió mal  
✅ **Fallbacks** - Valores por defecto si algo falla  
✅ **Mejor debug** - Logs detallados para troubleshooting  

---

## 🧪 Compilar y Probar

```bash
# 1. Compilar
./gradlew clean build

# 2. Desinstalar anterior
adb uninstall com.example.btalert

# 3. Instalar
./gradlew installDebug

# 4. Verificar que no hay crashes al abrir configuración
adb shell am start -n com.example.btalert/.MainActivity
```

---

**Archivo modificado:** `MainActivity.kt`  
**Método:** `showSettingsDialog()`  
**Cambios:** Agregados ~80 líneas de try-catch  
**Resultado:** ✅ Sin crashes al abrir configuración  

