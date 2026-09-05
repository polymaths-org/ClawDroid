package com.clawdroid.app.core

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.terminal.ProcessManager
import com.clawdroid.app.core.terminal.ProcessManagerProvider
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.db.ClawDroidDatabase

object AppContainer {
    private var _context: Context? = null
    private var _db: ClawDroidDatabase? = null
    private var _processManager: ProcessManager? = null
    private var _voiceManager: VoiceManager? = null
    private var _speechRecognizer: SpeechRecognizerClient? = null

    fun init(context: Context) {
        _context = context.applicationContext
        AppConfigManager.init(context)
        _db = ClawDroidDatabase.get(context)
        _processManager = ProcessManagerProvider.get(context)
    }

    val context: Context get() = _context!!
    val db: ClawDroidDatabase get() = _db!!
    val processManager: ProcessManager get() = _processManager!!

    fun createAgentEngine(projectId: String? = null): AgentEngine {
        return AgentEngine(_context!!, projectId = projectId)
    }

    fun getVoiceManager(): VoiceManager {
        if (_voiceManager == null) {
            _voiceManager = VoiceManager(_context!!)
        }
        return _voiceManager!!
    }

    fun getSpeechRecognizer(): SpeechRecognizerClient {
        if (_speechRecognizer == null) {
            _speechRecognizer = SpeechRecognizerClient(_context!!)
        }
        return _speechRecognizer!!
    }

    fun releaseVoiceResources() {
        _voiceManager?.destroy()
        _voiceManager = null
        _speechRecognizer?.destroy()
        _speechRecognizer = null
    }
}
