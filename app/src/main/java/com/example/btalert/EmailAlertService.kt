package com.example.btalert

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.btalert.AppConfig.PrefsKeys
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource

/**
 * Captura foto frontal → foto trasera → audio → envía email SMTP.
 * Las cámaras se abren SECUENCIALMENTE para evitar ERROR_MAX_CAMERAS_IN_USE.
 */
class EmailAlertService : Service() {

    companion object {
        private const val TAG           = "EmailAlertService"
        private const val AUDIO_SECONDS = 12
        const val ACTION_SEND           = "com.example.btalert.ACTION_SEND_EMAIL"

        fun buildIntent(context: Context, trigger: String): Intent =
            Intent(context, EmailAlertService::class.java).apply {
                action = ACTION_SEND
                putExtra("trigger", trigger)
            }
    }

    private lateinit var prefs: SharedPreferences
    private val cameraThread = HandlerThread("EmailCameraThread").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var frontPhotoBytes: ByteArray? = null
    private var rearPhotoBytes:  ByteArray? = null
    private var audioFile:       File?      = null
    private var location:        android.location.Location? = null

    private var attachFront = true
    private var attachRear  = true
    private var attachAudio = true

    override fun onCreate() {
        super.onCreate()
        prefs = applicationContext.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_SEND) { stopSelf(); return START_NOT_STICKY }

        val trigger = intent.getStringExtra("trigger") ?: "unknown"
        Log.d(TAG, "EmailAlertService iniciado — trigger=$trigger")

        val lat = intent.getDoubleExtra("lat", Double.MIN_VALUE)
        val lng = intent.getDoubleExtra("lng", Double.MIN_VALUE)
        if (lat != Double.MIN_VALUE) {
            location = android.location.Location("").apply {
                latitude = lat; longitude = lng
            }
        }

        attachFront = prefs.getBoolean(PrefsKeys.PRO_ATTACH_FRONT_CAM, true)
        attachRear  = prefs.getBoolean(PrefsKeys.PRO_ATTACH_REAR_CAM,  true)
        attachAudio = prefs.getBoolean(PrefsKeys.PRO_ATTACH_AUDIO,     true)

        // Iniciar cadena de capturas secuenciales
        startCaptureChain()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        audioFile?.delete()
    }

    // =========================================================
    // CADENA SECUENCIAL: frontal → trasera → audio → email
    // =========================================================

    private fun startCaptureChain() {
        if (attachFront) {
            Log.d(TAG, "Paso 1: capturando foto frontal...")
            capturePhoto(front = true) { bytes ->
                frontPhotoBytes = bytes
                Log.d(TAG, "📸 Foto frontal: ${bytes?.size ?: 0} bytes")
                stepRear()
            }
        } else {
            stepRear()
        }
    }

    private fun stepRear() {
        if (attachRear) {
            // Pequeño delay para que la cámara frontal libere completamente
            cameraHandler.postDelayed({
                Log.d(TAG, "Paso 2: capturando foto trasera...")
                capturePhoto(front = false) { bytes ->
                    rearPhotoBytes = bytes
                    Log.d(TAG, "📸 Foto trasera: ${bytes?.size ?: 0} bytes")
                    stepAudio()
                }
            }, 800L)
        } else {
            stepAudio()
        }
    }

    private fun stepAudio() {
        if (attachAudio) {
            Log.d(TAG, "Paso 3: grabando audio ${AUDIO_SECONDS}s...")
            recordAudio {
                Log.d(TAG, "🎙️ Audio listo: ${audioFile?.length() ?: 0} bytes")
                stepSend()
            }
        } else {
            stepSend()
        }
    }

    private fun stepSend() {
        Log.d(TAG, "Paso final: enviando email...")
        Thread { sendEmail() }.start()
    }

    // =========================================================
    // CAPTURA DE FOTO con callback al terminar
    // =========================================================

    private fun capturePhoto(front: Boolean, onDone: (ByteArray?) -> Unit) {
        val label = if (front) "frontal" else "trasera"
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Sin permiso de cámara — omitiendo $label")
                onDone(null); return
            }

            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val facing = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
                if (front) facing == CameraCharacteristics.LENS_FACING_FRONT
                else       facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: run {
                Log.w(TAG, "Cámara $label no encontrada")
                onDone(null); return
            }

            val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
            var captured = false

            imageReader.setOnImageAvailableListener({ reader ->
                if (captured) return@setOnImageAvailableListener
                captured = true
                val image = reader.acquireLatestImage()
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error leyendo imagen $label", e); null
                } finally {
                    image?.close()
                }
                reader.close()
                onDone(bytes)
            }, cameraHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    val req = camera.createCaptureRequest(
                                        CameraDevice.TEMPLATE_STILL_CAPTURE
                                    ).apply {
                                        addTarget(imageReader.surface)
                                        set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_AUTO)
                                        set(CaptureRequest.FLASH_MODE,
                                            CaptureRequest.FLASH_MODE_OFF)
                                    }.build()
                                    session.capture(req, null, cameraHandler)
                                    cameraHandler.postDelayed({ camera.close() }, 3000)
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    Log.e(TAG, "Config falló $label")
                                    camera.close(); onDone(null)
                                }
                            }, cameraHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sesión $label", e)
                        camera.close(); onDone(null)
                    }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Error cámara $label: $error")
                    camera.close(); onDone(null)
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error capturando $label", e)
            onDone(null)
        }
    }

    // =========================================================
    // GRABACIÓN DE AUDIO con callback al terminar
    // =========================================================

    private fun recordAudio(onDone: () -> Unit) {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Sin permiso de micrófono")
                onDone(); return
            }

            val outFile = File(cacheDir, "sos_audio_${System.currentTimeMillis()}.m4a")
            audioFile = outFile

            @Suppress("DEPRECATION")
            val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setMaxDuration(AUDIO_SECONDS * 1000)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.setOnInfoListener { mr, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    try { mr.stop() } catch (_: Exception) {}
                    mr.release()
                    onDone()
                }
            }
            recorder.setOnErrorListener { mr, _, _ ->
                mr.release()
                onDone()
            }
            recorder.prepare()
            recorder.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error grabando audio", e)
            onDone()
        }
    }

    // =========================================================
    // ENVÍO DE EMAIL VIA SMTP
    // =========================================================

    private fun sendEmail() {
        try {
            val host    = prefs.getString(PrefsKeys.PRO_SMTP_HOST, "") ?: ""
            val port    = prefs.getString(PrefsKeys.PRO_SMTP_PORT, "587") ?: "587"
            val user    = prefs.getString(PrefsKeys.PRO_SMTP_USER, "") ?: ""
            val pass    = prefs.getString(PrefsKeys.PRO_SMTP_PASS, "") ?: ""
            val to      = prefs.getString(PrefsKeys.PRO_EMAIL_TO, "") ?: ""
            val subject = prefs.getString(PrefsKeys.PRO_EMAIL_SUBJECT, "🆘 BT Alert") ?: "🆘 BT Alert"
            val body    = prefs.getString(PrefsKeys.PRO_EMAIL_BODY, "") ?: ""

            Log.d(TAG, "SMTP: host=$host port=$port user=$user to=$to")

            if (host.isEmpty() || user.isEmpty() || pass.isEmpty() || to.isEmpty()) {
                Log.e(TAG, "❌ Configuración SMTP incompleta — abortando")
                stopSelf(); return
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val locationText = location?.let {
                "https://maps.google.com/?q=${it.latitude},${it.longitude}"
            } ?: getString(R.string.sos_location_unavailable)

            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.trust", host)
                put("mail.smtp.connectiontimeout", "20000")
                put("mail.smtp.timeout", "20000")
                put("mail.smtp.writetimeout", "20000")
            }

            Log.d(TAG, "Creando sesión SMTP...")
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
            })

            val mime = MimeMessage(session).apply {
                setFrom(InternetAddress(user))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject("$subject — $timestamp", "UTF-8")
            }

            val multipart: Multipart = MimeMultipart()

            // Texto
            multipart.addBodyPart(MimeBodyPart().apply {
                setText("$body\n\n⏰ $timestamp\n📍 $locationText", "UTF-8")
            })

            // Foto frontal
            frontPhotoBytes?.let { bytes ->
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(bytes, "image/jpeg"))
                    fileName = "frontal_$timestamp.jpg"
                })
                Log.d(TAG, "📎 Foto frontal adjuntada")
            }

            // Foto trasera
            rearPhotoBytes?.let { bytes ->
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(bytes, "image/jpeg"))
                    fileName = "trasera_$timestamp.jpg"
                })
                Log.d(TAG, "📎 Foto trasera adjuntada")
            }

            // Audio
            audioFile?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(file))
                    fileName = "audio_$timestamp.m4a"
                })
                Log.d(TAG, "📎 Audio adjuntado: ${file.length()} bytes")
            }

            mime.setContent(multipart)

            Log.d(TAG, "Conectando a SMTP y enviando...")
            Transport.send(mime)
            Log.w(TAG, "✅ Email enviado correctamente a $to")

        } catch (e: jakarta.mail.AuthenticationFailedException) {
            Log.e(TAG, "❌ Error autenticación SMTP — verifica usuario/contraseña de app", e)
        } catch (e: jakarta.mail.MessagingException) {
            Log.e(TAG, "❌ Error SMTP: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado enviando email", e)
        } finally {
            audioFile?.delete()
            stopSelf()
        }
    }
}
