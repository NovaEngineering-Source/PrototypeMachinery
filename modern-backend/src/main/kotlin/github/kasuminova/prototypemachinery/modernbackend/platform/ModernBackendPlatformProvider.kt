package github.kasuminova.prototypemachinery.modernbackend.platform

import github.kasuminova.prototypemachinery.api.platform.PMPlatform
import github.kasuminova.prototypemachinery.api.platform.PMPlatformProvider

class ModernBackendPlatformProvider : PMPlatformProvider {

    override fun id(): String = "pm-modern-backend"

    override fun priority(): Int = 200

    override fun create(): PMPlatform = ModernBackendPlatform()
}
