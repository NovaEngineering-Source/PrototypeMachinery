package github.kasuminova.prototypemachinery.api.machine.structure.logic

import kotlinx.serialization.json.JsonObject

/**
 * Optional extension for [StructureValidator]s that want to receive JSON parameters
 * from `StructureData.validators`.
 *
 * Loader behavior:
 * - The loader always creates validators via [StructureValidatorRegistry].
 * - If the created validator implements this interface, it will receive the parsed params.
 * - If params are present but the validator is not configurable, params will be ignored with a warning.
 */
public interface JsonConfigurableStructureValidator : StructureValidator {

    /**
     * Configure this validator from JSON params.
     *
     * This is called once per structure load (per validator instance).
     */
    public fun configure(params: JsonObject)
}
