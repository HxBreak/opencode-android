package me.xiaok.opencode.data.repository

import android.util.Log
import me.xiaok.opencode.domain.model.*

class PtyReducer internal constructor(
    private val host: EventReducer,
) {
    fun onPtyCreated(serverId: String, info: PtyInfo) {
        Log.d(TAG, "onPtyCreated: server=$serverId, ptyId=${info.id}, title=${info.title}")
        val serverPtys = host.ptySessionsFlow.value[serverId] ?: emptyMap()
        host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
            put(serverId, serverPtys + (info.id to info))
        }
    }

    fun onPtyUpdated(serverId: String, info: PtyInfo) {
        Log.d(TAG, "onPtyUpdated: server=$serverId, ptyId=${info.id}, status=${info.status}")
        val serverPtys = host.ptySessionsFlow.value[serverId] ?: emptyMap()
        if (info.id in serverPtys) {
            host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
                put(serverId, serverPtys + (info.id to info))
            }
        }
    }

    fun onPtyExited(serverId: String, ptyId: String, exitCode: Int) {
        Log.d(TAG, "onPtyExited: server=$serverId, ptyId=$ptyId, exitCode=$exitCode")
        val serverPtys = host.ptySessionsFlow.value[serverId] ?: return
        val existing = serverPtys[ptyId] ?: return
        host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
            put(serverId, serverPtys + (ptyId to existing.copy(status = "exited")))
        }
    }

    fun onPtyDeleted(serverId: String, ptyId: String) {
        Log.d(TAG, "onPtyDeleted: server=$serverId, ptyId=$ptyId")
        val serverPtys = host.ptySessionsFlow.value[serverId] ?: return
        host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
            put(serverId, serverPtys - ptyId)
        }
    }

    fun setPtys(serverId: String, ptys: List<PtyInfo>) {
        val ptyMap = ptys.associateBy { it.id }
        host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
            put(serverId, ptyMap)
        }
    }

    fun clearForServer(serverId: String) {
        host.ptySessionsFlow.value = host.ptySessionsFlow.value.toMutableMap().apply {
            remove(serverId)
        }
    }

    fun clearAll() {
        host.ptySessionsFlow.value = emptyMap()
    }

    companion object {
        private const val TAG = "PtyReducer"
    }
}
