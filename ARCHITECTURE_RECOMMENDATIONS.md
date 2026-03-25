# 🏗️ Recomendaciones de Arquitectura - BT Alert

## Introducción

Este documento contiene recomendaciones para mejorar la arquitectura y robustez de la aplicación BT Alert más allá de las correcciones inmediatas ya implementadas.

---

## 1. Patrones de Diseño Recomendados

### 1.1 Repository Pattern

**Beneficio:** Abstrae acceso a SharedPreferences y Bluetooth

```kotlin
// Antes: Acceso directo en varios lugares
val mac = sharedPrefs.getString("selected_device_mac", null)

// Después: Centralizado en repositorio
class BluetoothRepository(private val sharedPrefs: SharedPreferences) {
    fun getSelectedDeviceMac(): String? = 
        sharedPrefs.getString("selected_device_mac", null)
    
    fun setSelectedDeviceMac(mac: String) =
        sharedPrefs.edit { putString("selected_device_mac", mac) }
}

// Uso:
val mac = repository.getSelectedDeviceMac()
```

### 1.2 Result Wrapper Pattern

**Beneficio:** Manejo explícito de éxito/error

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val exception: Exception) : Result<T>()
    object Loading : Result<Nothing>()
}

// Uso:
fun loadDevices(): Result<List<BluetoothDevice>> = try {
    val devices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
    Result.Success(devices)
} catch (e: Exception) {
    Result.Error(e)
}
```

### 1.3 Service Locator / Dependency Injection

**Beneficio:** Inyección de dependencias para testing y flexibilidad

```kotlin
// Usar Hilt o Dagger
@HiltViewModel
class MainViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    // ...
}
```

---

## 2. Mejoras de Código

### 2.1 Consolidación de Funciones

**Antes:** Múltiples funciones similares
```kotlin
private fun loadPairedDevices() { ... }
private fun queryConnectedProfiles(...) { ... }
private fun getBluetoothBatteryLevel(...) { ... }
```

**Después:** Clase especializada
```kotlin
class BluetoothDeviceManager {
    fun getAvailableDevices(): List<BluetoothDevice>
    fun getBatteryLevel(device: BluetoothDevice): Int
    fun isConnected(device: BluetoothDevice): Boolean
}
```

### 2.2 Uso de Corrutinas

**Antes:** Handler para operaciones asincrónicas
```kotlin
handler.postDelayed({ 
    loadPairedDevices() 
}, 500)
```

**Después:** Corrutinas con Kotlin
```kotlin
viewModelScope.launch {
    delay(500)
    loadPairedDevices()
}
```

### 2.3 LiveData/StateFlow para Estado

**Antes:** Broadcasts y callbacks
```kotlin
sendBroadcast(Intent("ACTION_MONITORING_STATE_CHANGED")
    .putExtra("is_active", isActive))
```

**Después:** StateFlow centralizado
```kotlin
private val _monitoringState = MutableStateFlow(false)
val monitoringState: StateFlow<Boolean> = _monitoringState.asStateFlow()

// Usar en UI
lifecycleScope.launch {
    viewModel.monitoringState.collect { isActive ->
        updateButtonState(isActive)
    }
}
```

---

## 3. Testing

### 3.1 Unit Tests

```kotlin
class BluetoothRepositoryTest {
    @Test
    fun testGetSelectedDeviceMac_ReturnsSavedMac() {
        val expected = "AA:BB:CC:DD:EE:FF"
        sharedPrefs.edit { putString("selected_device_mac", expected) }
        
        val result = repository.getSelectedDeviceMac()
        
        assertEquals(expected, result)
    }
}
```

### 3.2 Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class BluetoothReceiverIntegrationTest {
    @Test
    fun testOnReceive_WithBluetoothDevice_StartsService() {
        val intent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED)
        val receiver = BluetoothReceiver()
        
        receiver.onReceive(context, intent)
        
        // Verificar que el servicio se inició
        verify(context).startService(any())
    }
}
```

---

## 4. Estructura de Carpetas Mejorada

```
app/src/main/java/com/example/btalert/
├── data/
│   ├── repository/
│   │   ├── BluetoothRepository.kt
│   │   ├── SettingsRepository.kt
│   │   └── PreferenceManager.kt
│   ├── model/
│   │   └── BluetoothDeviceModel.kt
│   └── datasource/
│       └── SharedPreferencesDatasource.kt
├── domain/
│   ├── usecase/
│   │   ├── GetPairedDevicesUseCase.kt
│   │   ├── MonitorBluetoothUseCase.kt
│   │   └── PlayAlarmUseCase.kt
│   └── model/
│       └── Result.kt
├── presentation/
│   ├── viewmodel/
│   │   ├── MainViewModel.kt
│   │   └── SettingsViewModel.kt
│   ├── view/
│   │   ├── MainActivity.kt
│   │   ├── SettingsFragment.kt
│   │   └── components/
│   └── adapter/
│       └── DevicesAdapter.kt
├── service/
│   ├── BluetoothMonitorService.kt
│   └── AlarmService.kt
├── receiver/
│   ├── BluetoothReceiver.kt
│   ├── BootReceiver.kt
│   └── PowerButtonReceiver.kt
├── util/
│   ├── Constants.kt
│   ├── Extensions.kt
│   └── Logger.kt
└── MyApplication.kt
```

---

## 5. Configuración de Dependencias Recomendadas

```gradle
dependencies {
    // Hilt para DI
    implementation 'com.google.dagger:hilt-android:2.48'
    kapt 'com.google.dagger:hilt-compiler:2.48'
    
    // Corrutinas
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
    
    // Lifecycle
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
    
    // Firebase Crashlytics
    implementation 'com.google.firebase:firebase-crashlytics-ktx:18.5.0'
    implementation 'com.google.firebase:firebase-analytics-ktx:21.4.0'
    
    // Logging
    implementation 'com.squareup.timber:timber:5.0.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito.kotlin:mockito-kotlin:5.1.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

---

## 6. Logging Mejorado

### Usar Timber en lugar de Log

```kotlin
// Antes:
Log.d("TAG", "mensaje")

// Después:
Timber.d("mensaje")
Timber.e(exception, "Error en operación")

// En MainActivity.onCreate():
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
} else {
    Timber.plant(CrashlyticsTree()) // Personalizado
}
```

---

## 7. Monitoreo en Producción

### Firebase Crashlytics

```kotlin
// build.gradle.kts
plugins {
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
}

// En MyApplication.kt
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
```

### Custom Exception Handler

```kotlin
class GlobalExceptionHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        Timber.e(exception, "Uncaught exception in thread: ${thread.name}")
        FirebaseCrashlytics.getInstance().recordException(exception)
        // Mostrar diálogo al usuario si es posible
    }
}

// En MyApplication.onCreate():
Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler())
```

---

## 8. Performance

### Evitar Memory Leaks

```kotlin
// ❌ ANTES: Memory leak potencial
private var receiver: BroadcastReceiver? = null

override fun onDestroy() {
    super.onDestroy()
    // Quizás se olvida de desregistrar
}

// ✅ DESPUÉS: Usando scope
private val scope = CoroutineScope(Dispatchers.Main + Job())

override fun onDestroy() {
    super.onDestroy()
    scope.cancel() // Cancela automáticamente
    receiver?.let { unregisterReceiver(it) }
}
```

### Lazy Initialization

```kotlin
private val bluetoothAdapter: BluetoothAdapter? by lazy {
    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
}
```

---

## 9. Compatibilidad y Deprecación

### Handle API Level Differences

```kotlin
// Centralizar en una clase helper
object AndroidVersionHelper {
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    
    fun getBluetoothDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}

// Uso:
val device = AndroidVersionHelper.getBluetoothDevice(intent)
```

---

## 10. Roadmap de Mejoras

### Corto Plazo (1-2 semanas)
- [x] Implementar try-catch (YA HECHO)
- [ ] Agregar Timber para logging
- [ ] Unit tests básicos

### Mediano Plazo (1-2 meses)
- [ ] Refactorizar a arquitectura MVVM
- [ ] Implementar Repository Pattern
- [ ] Agregar Firebase Crashlytics

### Largo Plazo (3-6 meses)
- [ ] Migrar a Jetpack Compose (UI moderna)
- [ ] Implementar Hilt para DI
- [ ] Agregar testing completo (80%+ coverage)

---

## 📊 Comparativa: Antes vs Después

```
┌─────────────────────┬──────────────┬──────────────┐
│ Aspecto             │ Actual       │ Recomendado  │
├─────────────────────┼──────────────┼──────────────┤
│ Manejo de errores   │ Básico       │ Robusto      │
│ Logging             │ Log nativo   │ Timber       │
│ Testing             │ Ninguno      │ 80%+ coverage│
│ Arquitectura        │ Monolítica   │ MVVM         │
│ DI                  │ Manual       │ Hilt         │
│ Async               │ Handler      │ Corrutinas   │
│ Monitoreo           │ Ninguno      │ Crashlytics  │
└─────────────────────┴──────────────┴──────────────┘
```

---

## 📚 Recursos Útiles

- [Android Architecture Components](https://developer.android.com/topic/architecture)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [Firebase Crashlytics](https://firebase.google.com/docs/crashlytics)
- [MVVM Pattern](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel)
- [Dependency Injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)

---

## ✅ Conclusión

Las correcciones inmediatas resuelven los crashes actuales. Las recomendaciones anteriores permiten que la aplicación sea:

- 🔒 **Más robusta** - Manejo adecuado de errores
- 🧪 **Más testeable** - Arquitectura separada en capas
- 📊 **Más monitoreable** - Logging y crash reporting
- 🚀 **Más mantenible** - Código limpio y organizado

La implementación gradual de estas mejoras llevará a una aplicación de mayor calidad y confiabilidad.

---

**Elaborado por:** GitHub Copilot  
**Fecha:** 2026-02-25  
**Versión:** 1.0
