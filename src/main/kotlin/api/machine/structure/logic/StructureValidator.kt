package github.kasuminova.prototypemachinery.api.machine.structure.logic

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext

public fun interface StructureValidator {

    public fun validate(context: StructureMatchContext): Boolean

}
