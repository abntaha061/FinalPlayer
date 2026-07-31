package `is`.xyz.mpv

import android.content.Context
import android.view.Surface

object MPVLib {
    interface EventObserver {
        fun eventProperty(property: String, value: String) {}
        fun eventProperty(property: String, value: Long) {}
        fun eventProperty(property: String, value: Boolean) {}
        fun event(eventId: Int) {}
    }

    const val MPV_EVENT_NONE = 0
    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_LOG_MESSAGE = 2
    const val MPV_EVENT_GET_PROPERTY_REPLY = 3
    const val MPV_EVENT_SET_PROPERTY_REPLY = 4
    const val MPV_EVENT_COMMAND_REPLY = 5
    const val MPV_EVENT_START_FILE = 6
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_CLIENT_MESSAGE = 16
    const val MPV_EVENT_VIDEO_RECONFIG = 17
    const val MPV_EVENT_AUDIO_RECONFIG = 18
    const val MPV_EVENT_SEEK = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE = 22
    const val MPV_EVENT_QUEUE_OVERFLOW = 24

    private val observers = mutableListOf<EventObserver>()

    @JvmStatic
    fun create(context: Context) {}

    @JvmStatic
    fun init() {}

    @JvmStatic
    fun destroy() {}

    @JvmStatic
    fun attachSurface(surface: Surface) {}

    @JvmStatic
    fun detachSurface() {}

    @JvmStatic
    fun command(cmd: Array<String>) {}

    @JvmStatic
    fun setOptionString(option: String, value: String): Int = 0

    @JvmStatic
    fun getPropertyInt(property: String): Int = 0

    @JvmStatic
    fun getPropertyString(property: String): String = ""

    @JvmStatic
    fun setPropertyInt(property: String, value: Int) {}

    @JvmStatic
    fun setPropertyDouble(property: String, value: Double) {}

    @JvmStatic
    fun setPropertyString(property: String, value: String) {}

    @JvmStatic
    fun setPropertyBoolean(property: String, value: Boolean) {}

    @JvmStatic
    fun addObserver(observer: EventObserver) {
        if (!observers.contains(observer)) observers.add(observer)
    }

    @JvmStatic
    fun removeObserver(observer: EventObserver) {
        observers.remove(observer)
    }
}
