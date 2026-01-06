package com.example.xcpro.weather.wind

import com.example.xcpro.weather.wind.domain.WindCandidate
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Test

class WindSelectionUseCaseTest {

    private val useCase = WindSelectionUseCase()

    @Test
    fun `auto newer than manual wins over external`() {
        val auto = candidate(WindSource.EKF, timestamp = 200)
        val manual = candidate(WindSource.MANUAL, timestamp = 100)
        val external = candidate(WindSource.EXTERNAL, timestamp = 150)

        val selected = useCase.select(auto = auto, manual = manual, external = external)

        assertEquals(WindSource.EKF, selected?.source)
    }

    @Test
    fun `external wins when auto not newer than manual`() {
        val auto = candidate(WindSource.CIRCLING, timestamp = 90)
        val manual = candidate(WindSource.MANUAL, timestamp = 100)
        val external = candidate(WindSource.EXTERNAL, timestamp = 80)

        val selected = useCase.select(auto = auto, manual = manual, external = external)

        assertEquals(WindSource.EXTERNAL, selected?.source)
    }

    @Test
    fun `manual wins when auto not newer and no external`() {
        val auto = candidate(WindSource.EKF, timestamp = 90)
        val manual = candidate(WindSource.MANUAL, timestamp = 100)

        val selected = useCase.select(auto = auto, manual = manual, external = null)

        assertEquals(WindSource.MANUAL, selected?.source)
    }

    private fun candidate(source: WindSource, timestamp: Long): WindCandidate =
        WindCandidate(
            vector = WindVector(east = 1.0, north = 0.0),
            source = source,
            quality = 4,
            timestampMillis = timestamp
        )
}
