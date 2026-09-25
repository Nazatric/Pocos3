package com.pocos3.input

import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import androidx.core.content.getSystemService

// =============================================================================
// Controller manager.
//
// Detects physical controllers (Bluetooth, USB, built-in gamepad). Maps
// Android InputDevice sources to PS3 pad classes. Forwards input to the
// core via PocoS3Core.nativeOverlayPadData.
//
// Hot-plugging is observed via InputManager.InputDeviceListener.
// =============================================================================

class ControllerManager private constructor(private val context: Context) {
    private val inputManager = context.getSystemService<InputManager>()
    private val bluetoothManager = context.getSystemService<BluetoothManager>()
    private val listeners = mutableMapOf<Int, (Int, Boolean) -> Unit>()

    fun discover(): List<Controller> {
        val ids = InputDevice.getDeviceIds()
        return ids.mapNotNull { id ->
            val dev = InputDevice.getDevice(id)
            if (dev == null || !dev.supportsSource(InputDevice.SOURCE_GAMEPAD) &&
                !dev.supportsSource(InputDevice.SOURCE_JOYSTICK)) null
            else Controller(
                deviceId = dev.id,
                name = dev.name,
                vendorId = dev.vendorId,
                productId = dev.productId,
                sources = dev.sources,
            )
        }
    }

    fun registerHotplugListener(onChange: (Int, Boolean) -> Unit) {
        inputManager?.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { onChange(deviceId, true) }
            override fun onInputDeviceRemoved(deviceId: Int) { onChange(deviceId, false) }
            override fun onInputDeviceChanged(deviceId: Int) {}
        }, null)
    }

    companion object {
        @Volatile private var instance: ControllerManager? = null
        fun get(context: Context): ControllerManager =
            instance ?: synchronized(this) {
                instance ?: ControllerManager(context.applicationContext).also { instance = it }
            }
    }
}

data class Controller(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
) {
    val isDualShock: Boolean get() = vendorId == 0x054C
    val isGenericXbox: Boolean get() = vendorId == 0x045E
}
