package io.horizontalsystems.bitcoincore.network.peer.task

import io.horizontalsystems.bitcoincore.network.messages.IMessage

open class PeerTask {

    interface Listener {
        fun onTaskCompleted(task: PeerTask)
        fun onTaskFailed(task: PeerTask, e: Exception)
    }

    interface Requester {
        val protocolVersion: Int
        fun send(message: IMessage)
    }

    var requester: Requester? = null
    var listener: Listener? = null

    protected var lastActiveTime: Long? = null
    protected var allowedIdleTime: Long? = null

    open fun start() = Unit
    open fun handleTimeout() = Unit

    fun checkTimeout() {
        allowedIdleTime?.let { allowedIdleTime ->
            lastActiveTime?.let { lastActiveTime ->
                if (System.currentTimeMillis() - lastActiveTime > allowedIdleTime) {
                    handleTimeout()
                }
            }
        }
    }

    fun resetTimer() {
        lastActiveTime = System.currentTimeMillis()
    }

    open fun handleMessage(message: IMessage): Boolean {
        return false
    }

}
