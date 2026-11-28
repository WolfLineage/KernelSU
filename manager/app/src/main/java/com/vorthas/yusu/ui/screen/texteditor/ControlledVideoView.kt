package com.vorthas.yusu.ui.screen.texteditor

import android.content.Context
import android.widget.MediaController
import android.widget.VideoView

/** VideoView with a typed reference to its controller; avoids View.setTag resource-id rules. */
class ControlledVideoView(context: Context) : VideoView(context) {
    var playerController: MediaController? = null
}
