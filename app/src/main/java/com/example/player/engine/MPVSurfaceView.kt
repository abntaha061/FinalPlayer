package com.example.player.engine

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class MPVSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var engine: PlayerEngine? = null

    init {
        holder.addCallback(this)
    }

    fun bindEngine(playerEngine: PlayerEngine) {
        this.engine = playerEngine
        if (holder.surface != null && holder.surface.isValid) {
            playerEngine.attachSurface(holder.surface)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        engine?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine?.attachSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        engine?.detachSurface()
    }
}
