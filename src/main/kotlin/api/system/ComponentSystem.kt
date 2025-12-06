package github.kasuminova.prototypemachinery.api.system

public interface ComponentSystem<E, C> {

    public fun onPreTick(entity: E, component: C)

    public fun onTick(entity: E, component: C)

    public fun onPostTick(entity: E, component: C)

}
