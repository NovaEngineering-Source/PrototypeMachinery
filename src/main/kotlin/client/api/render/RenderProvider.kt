package github.kasuminova.prototypemachinery.client.api.render

/**
 * Adapts a domain object (e.g. a TileEntity, a preview dummy, etc.) into a [Renderable].
 */
public fun interface RenderProvider<T> {
    public fun toRenderable(value: T): Renderable?
}
