package com.hshim.springapiassist.log.storage

import com.hshim.springapiassist.log.model.ApiLogEntry

interface ApiLogStorage {
    fun save(entry: ApiLogEntry)
}
