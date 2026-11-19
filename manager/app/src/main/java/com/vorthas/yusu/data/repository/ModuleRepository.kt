package com.vorthas.yusu.data.repository

import com.vorthas.yusu.data.model.Module
import com.vorthas.yusu.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
