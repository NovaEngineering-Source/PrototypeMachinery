package github.kasuminova.prototypemachinery.api.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import net.minecraft.nbt.NBTTagCompound

/**
 * Runtime component attached to a RecipeProcess (e.g., progress timer, buffer).
 * 运行时进程组件（如进度计时器、缓存等）。
 */
public interface RecipeProcessComponent {

    public val type: RecipeProcessComponentType<*>

    public val owner: RecipeProcess

    public fun serializeNBT(): NBTTagCompound

    public fun deserializeNBT(nbt: NBTTagCompound)

    /**
     * Interface for process components that support client synchronization.
     * 支持客户端同步的进程组件接口。
     *
     * Implementations can provide both full and incremental syncs.
     * 实现可以提供全量和增量同步。
     */
    public interface Synchronizable : RecipeProcessComponent {

        public enum class SyncType {
            FULL,       // Initial sync / 初始同步
            INCREMENTAL // Partial update / 增量更新
        }

        /**
         * Write data to be sent to client.
         * 写入要发送到客户端的数据。
         *
         * @param type The type of sync requested / 请求的同步类型
         * @return NBT containing data to sync, or null if nothing to sync
         */
        public fun writeClientNBT(type: SyncType): NBTTagCompound?

        /**
         * Read data received from server.
         * 读取从服务端接收的数据。
         *
         * @param nbt The received data / 接收到的数据
         * @param type The type of sync received / 接收到的同步类型
         */
        public fun readClientNBT(nbt: NBTTagCompound, type: SyncType)
    }
}