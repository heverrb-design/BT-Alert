# 🎯 GUÍA DE INSTALACIÓN Y PRUEBA - Permisos en Primer Inicio

## ⚠️ IMPORTANTE: Desinstalar Versión Anterior

Para que el flujo de permisos funcionte correctamente, **DEBES desinstalar completamente la versión anterior**:

```bash
adb uninstall com.example.btalert
```

Si no desinstales, Android cachea las preferencias y no mostrará los diálogos de permisos nuevamente.

---

## 🚀 Pasos de Instalación

### 1. Limpiar Compilaciones Anteriores
```bash
cd D:\AndroidStudioProjects\BTAlert
./gradlew clean
```

### 2. Compilar
```bash
./gradlew build
```

### 3. Desinstalar Versión Anterior (IMPORTANTE)
```bash
adb uninstall com.example.btalert
```

### 4. Instalar Nueva Versión
```bash
./gradlew installDebug
```

### 5. Verificar Instalación
```bash
adb shell pm list packages | grep btalert
# Debe mostrar: com.example.btalert
```

---

## 📱 Flujo de Prueba (Primera Ejecución)

### Paso 1: Abrir la App
```bash
adb shell am start -n com.example.btalert/.MainActivity
```

### Paso 2: Primer Diálogo - "Necesito un permiso"
```
┌─────────────────────────────────────┐
│ Necesito un permiso                 │
│                                     │
│ Para que pueda avisarte              │
│ automáticamente al conectar un       │
│ dispositivo Bluetooth, necesito que  │
│ actives el permiso 'Mostrar sobre    │
│ otras aplicaciones'.                 │
│                                     │
│  [CONFIGURAR] [MÁS TARDE]          │
└─────────────────────────────────────┘
```

**Opciones:**
- ✅ **CONFIGURAR:** Te lleva a Configuración de Sistema
  - Busca "BT Alert" > "Mostrar sobre otras apps"
  - Activa el toggle
  - Regresa a la app automáticamente
  
- ❌ **MÁS TARDE:** Continúa sin este permiso (NO recomendado)

### Paso 3: Segundo Diálogo - "Permisos necesarios"
```
┌─────────────────────────────────────┐
│ Permisos necesarios                 │
│                                     │
│ La aplicación necesita los siguientes│
│ permisos:                            │
│                                     │
│ • Bluetooth: Para conectar y         │
│   monitorear dispositivos            │
│ • Notificaciones: Para alertarte     │
│   cuando se desconecte               │
│                                     │
│  [PERMITIR] [CANCELAR]              │
└─────────────────────────────────────┘
```

**Opciones:**
- ✅ **PERMITIR:** Concede todos los permisos
  - Android mostrará dialogo de permisos
  - Concede: Bluetooth Connect, Bluetooth Scan, Notificaciones
  
- ❌ **CANCELAR:** Continúa sin permisos

### Paso 4: Tercer Diálogo - "Selecciona dispositivo"
```
┌─────────────────────────────────────┐
│ Selecciona un dispositivo Bluetooth  │
│ para monitorear                      │
│                                     │
│ [Dispositivos encontrados ▼]        │
│                                     │
│ Deshabilitar flash                  │
│ ☐                                   │
│                                     │
│ [Guardar]                           │
└─────────────────────────────────────┘
```

**Acciones:**
1. Selecciona el dispositivo a monitorear del dropdown
2. (Opcional) Deshabilita flash si no lo deseas
3. Toca **GUARDAR**

---

## ✅ Lo Que Deberías Ver Después

Después de guardar la configuración:

1. **La app mostrará el estado:** "MONITOREO INACTIVO"
2. **Habrá un botón azul:** "INICIAR MONITOREO"
3. **Se verá una notificación** en la barra superior
4. **El dispositivo está listo para usar**

---

## 🔍 Verificación de Logs

Para ver los logs de lo que sucede:

```bash
# Ver todos los logs
adb logcat | grep MainActivity

# Ver solo logs de permisos
adb logcat | grep -i "permission\|permission"

# Ver logs de la app
adb logcat | grep "BT Alert"
```

---

## 🧪 Pruebas Manuales

### Prueba 1: Verificar que Aparecen Todos los Diálogos
```
1. Desinstalar: adb uninstall com.example.btalert
2. Instalar versión nueva
3. Abrir app
4. Verificar que ves 3 diálogos en orden:
   ✅ "Necesito un permiso" (overlay)
   ✅ "Permisos necesarios" (Bluetooth + Notificaciones)
   ✅ "Selecciona dispositivo" (configuración)
5. Conceder todos los permisos
6. Guardar configuración
```

### Prueba 2: Verificar que No Aparecen en Segunda Ejecución
```
1. Cerrar la app
2. Abrir nuevamente
3. Verificar que NO muestra diálogos de permisos
4. Muestra directamente la UI
```

### Prueba 3: Revocar Permiso Manualmente
```
1. Ir a: Configuración > Aplicaciones > BT Alert > Permisos
2. Desactivar "Notificaciones"
3. Abrir la app
4. La app debe solicitar el permiso nuevamente
```

---

## 🛠️ Troubleshooting

### Problema: Los Diálogos No Aparecen
```
Causa: La app fue instalada previamente y Android cachea los permisos

Solución:
1. Desinstalar completamente:
   adb uninstall com.example.btalert
2. Ir a Configuración > Aplicaciones > Todos
3. Buscar y eliminar datos caché de BT Alert
4. Instalar nuevamente
```

### Problema: "Permiso denegado" en los logs
```
Causa: El usuario rechazó los permisos

Solución:
1. Ir a Configuración > Aplicaciones > BT Alert > Permisos
2. Activar manualmente todos los permisos
3. Reabre la app
```

### Problema: Diálogos aparecen pero no se responden
```
Causa: Posible conflicto con permisos de overlay

Solución:
1. Ir a Configuración > Aplicaciones > BT Alert
2. Activar "Mostrar sobre otras aplicaciones"
3. Reabre la app
```

---

## 📊 Orden de Diálogos (Referencia)

| Paso | Diálogo | Permiso | Acción |
|------|---------|---------|--------|
| 1 | Necesito un permiso | SYSTEM_ALERT_WINDOW | Ir a Settings |
| 2 | Permisos necesarios | BLUETOOTH_CONNECT, BLUETOOTH_SCAN, POST_NOTIFICATIONS | Permitir/Cancelar |
| 3 | Selecciona dispositivo | N/A (es configuración) | Elegir y guardar |

---

## 🎉 Éxito

Cuando todo funciona correctamente:

✅ Primera ejecución muestra 3 diálogos en orden  
✅ Segunda ejecución no muestra diálogos  
✅ Se pueden revocar permisos manualmente  
✅ App solicita permiso revocado nuevamente  

---

## 📞 Preguntas Frecuentes

**P: ¿Tengo que revocar el permiso manualmente después?**  
R: No. Los permisos se guardan. El diálogo de permisos solo aparece si:
   - Es la primera ejecución
   - El usuario previamente rechazó

**P: ¿Qué pasa si digo "Más tarde" en el primer diálogo?**  
R: Continúa sin el permiso "Mostrar sobre otras aplicaciones". Las alarmas no funcionarán correctamente.

**P: ¿Por qué necesita 3 diálogos?**  
R: 
   - Diálogo 1: Permiso de sistema (ir a Settings)
   - Diálogo 2: Permisos de runtime (Bluetooth + Notificaciones)
   - Diálogo 3: Configuración de la app (elegir dispositivo)

**P: ¿Los logs muestran algo importante?**  
R: Sí, búsca "requestAllPermissionsFirstTime" o "requestBluetoothAndNotificationPermissions"

---

## 🚀 Próximos Pasos

1. **Compilar:** `./gradlew build`
2. **Desinstalar anterior:** `adb uninstall com.example.btalert`
3. **Instalar nueva:** `./gradlew installDebug`
4. **Probar:** Abre la app y verifica los diálogos
5. **Validar:** Todos los diálogos aparecen en orden
6. **¡Distribuir!** Si todo funciona

---

**Tiempo de instalación:** ~5 minutos  
**Tiempo de testing:** ~5 minutos  
**Total:** ~10 minutos  

¡Listo para distribuir después! 🎉

