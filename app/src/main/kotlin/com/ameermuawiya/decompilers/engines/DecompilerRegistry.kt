/*
 * Apk Decompiler Pro
 * Copyright (C) 2026 Ameer Muawiya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ameermuawiya.decompilers.engines

import java.util.Collections
import java.util.LinkedHashMap

/*
 * Central registry holding and managing all registered decompiler engine plugins.
 */
object DecompilerRegistry {
    private val engines = Collections.synchronizedMap(LinkedHashMap<String, DecompilerEngine>())

    init {
        registerEngine(JadxEngine())
        registerEngine(CfrEngine())
        registerEngine(ProcyonEngine())
        registerEngine(FernFlowerEngine())
    }

    /*
     * Registers a new decompiler engine implementation into the global registry.
     */
    fun registerEngine(engine: DecompilerEngine) {
        engines[engine.id.lowercase()] = engine
    }

    /*
     * Retrieves all registered decompiler engines.
     */
    fun getAllEngines(): List<DecompilerEngine> {
        return engines.values.toList()
    }

    /*
     * Retrieves a decompiler engine by unique string key identifier.
     */
    fun getEngine(id: String): DecompilerEngine? {
        return engines[id.lowercase()]
    }

    /*
     * Retrieves display names for all registered decompiler engines.
     */
    fun getEngineNames(): Array<String> {
        return engines.values.map { "${it.name} ${it.version}" }.toTypedArray()
    }

    /*
     * Retrieves identifier keys for all registered decompiler engines.
     */
    fun getEngineValues(): Array<String> {
        return engines.values.map { it.id }.toTypedArray()
    }

    /*
     * Retrieves descriptions for all registered decompiler engines.
     */
    fun getEngineDescriptions(): Array<String> {
        return engines.values.map { it.description }.toTypedArray()
    }

    /*
     * Checks if a decompiler engine is registered and available on the system.
     */
    fun isAvailable(id: String): Boolean {
        return getEngine(id)?.isAvailable ?: false
    }
}
