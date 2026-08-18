package com.drp.archfixture.core

import com.drp.archfixture.module.alpha.Alpha

/**
 * La dependencia prohibida numero dos, y la que de verdad protege: el core
 * sabiendo quien le escucha.
 */
class CoreThatPeeks {
    fun ask(): String = Alpha().ask()
}
