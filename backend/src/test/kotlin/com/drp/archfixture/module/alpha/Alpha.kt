package com.drp.archfixture.module.alpha

import com.drp.archfixture.module.beta.Beta

/** La dependencia prohibida numero uno: un modulo que referencia a otro. */
class Alpha {
    fun ask(): String = Beta().value()
}
