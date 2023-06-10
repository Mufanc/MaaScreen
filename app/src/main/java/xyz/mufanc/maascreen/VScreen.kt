package xyz.mufanc.maascreen

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.IActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManagerGlobal
import android.hardware.display.IDisplayManager
import android.hardware.display.IVirtualDisplayCallback
import android.hardware.display.VirtualDisplayConfig
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.ServiceManager
import android.util.Base64
import android.util.Base64OutputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

object VScreen {
    private const val NAME = "MaaScreen@Virtual"
    private const val PACKAGE = "com.android.shell"
    private const val FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

    private val dm: IDisplayManager by lazy {
        IDisplayManager.Stub.asInterface(ServiceManager.getService("display"))
    }

    private val am: IActivityManager by lazy {
        IActivityManager.Stub.asInterface(ServiceManager.getService("activity"))
    }

    private var displayId: Int? = null
    private lateinit var callback: IVirtualDisplayCallback
    private lateinit var imageReader: ImageReader
    private val encoder = ScreenEncoder()

    @SuppressLint("WrongConstant")
    fun init(width: Int, height: Int, dpi: Int): Int {
        if (displayId != null) return displayId!!

        callback = DisplayManagerGlobal::class.java.declaredClasses
            .find { it.simpleName == "VirtualDisplayCallback" }!!
            .declaredConstructors[0]
            .apply { isAccessible = true }
            .newInstance(null, null) as IVirtualDisplayCallback

        val sdkVersion = Build.VERSION.SDK_INT

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        displayId = when {
            sdkVersion < 30 -> dm.createVirtualDisplay(
                callback, null,
                PACKAGE,
                NAME, width, height, dpi, imageReader.surface,
                FLAGS, null
            )

            else -> {
                val builder = VirtualDisplayConfig.Builder(NAME, width, height, dpi)
                builder.setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                builder.setSurface(imageReader.surface)
                dm.createVirtualDisplay(builder.build(), callback, null, PACKAGE)
            }
        }

        val worker = HandlerThread("")
        worker.start()

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) encoder.update(image)
        }, Handler(worker.looper))

        return displayId!!
    }

    fun destroy() {
        dm.releaseVirtualDisplay(callback)
    }

    fun startActivity(intent: Intent) {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId!!

        am.startActivityAsUser(
            null, PACKAGE, intent,
            null, null, null, 0,
            0,
            null,
            options.toBundle(),
            Process.myUserHandle().hashCode()
        )
    }

    fun capture(): String? {
        return encoder.encode()
    }

    private class ScreenEncoder {

        private var screen: Image? = null
        private val screenLock = Any()

        private val stream = ByteArrayOutputStream()
        private lateinit var lastEncoded: String

        private val lock = ReentrantLock()
        private val condition = lock.newCondition()

        fun runEncodeLoop() {
            thread(isDaemon = true) {
                while (true) {
                    lock.lock()
                    condition.await()
                    lock.unlock()

                    stream.reset()

                    val b64stream = Base64OutputStream(stream, Base64.DEFAULT)

                    var bitmap: Bitmap?
                    synchronized(screenLock) {
                        bitmap = Bitmap.createBitmap(screen!!.width, screen!!.height, Bitmap.Config.ARGB_8888)
                        bitmap!!.copyPixelsFromBuffer(screen!!.planes[0].buffer.apply { rewind() })
                    }
                    bitmap!!.compress(Bitmap.CompressFormat.PNG, 80, b64stream)

                    lastEncoded = stream.toString()
                }
            }
        }

        fun update(image: Image) {
            synchronized(screenLock) {
                if (screen != null) screen!!.close() else runEncodeLoop()
                screen = image
            }

            lock.lock()
            condition.signal()
            lock.unlock()
        }

        fun encode(): String? {
            if (!::lastEncoded.isInitialized) return null
            return lastEncoded
        }
    }
}
