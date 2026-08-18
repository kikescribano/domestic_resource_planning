package com.drp.archfixture.platform

import com.drp.archfixture.core.CoreThatPeeks

/** La dependencia prohibida numero tres: plataforma apoyandose en el core. */
class PlatformThatLeans {
    fun ask(): String = CoreThatPeeks().ask()
}
