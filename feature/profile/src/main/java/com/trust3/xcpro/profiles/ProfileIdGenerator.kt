package com.trust3.xcpro.profiles

import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject

class ProfileIdGenerator private constructor(
    private val nextId: () -> String
) {
    @Inject
    constructor() : this({ UUID.randomUUID().toString() })

    fun newId(): String = nextId()

    companion object {
        fun fixed(vararg ids: String): ProfileIdGenerator {
            val remainingIds = ArrayDeque(ids.asList())
            return ProfileIdGenerator {
                check(remainingIds.isNotEmpty()) { "No fixed profile IDs remaining." }
                remainingIds.removeFirst()
            }
        }
    }
}
