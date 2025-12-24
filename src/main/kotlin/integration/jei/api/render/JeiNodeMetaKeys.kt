package github.kasuminova.prototypemachinery.integration.jei.api.render

/**
 * Well-known keys for [PMJeiRequirementNode.metadata].
 */
public object JeiNodeMetaKeys {

    /**
     * String: precomputed label to render on the top-left of a slot (e.g. "50%" or "12.5%*").
     *
     * If absent, chance overlay should be considered disabled for this node.
     */
    public const val CHANCE_OVERLAY_LABEL: String = "__pm_jei_chance_overlay_label"
}
