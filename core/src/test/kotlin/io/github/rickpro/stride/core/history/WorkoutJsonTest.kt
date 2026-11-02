package io.github.rickpro.stride.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutJsonTest {
    private val workout = Workout(
        id = "w-1700000000000",
        startedAtEpochMs = 1_700_000_000_000L,
        endedAtEpochMs = 1_700_003_727_000L,
        durationSeconds = 3727,
        distanceKm = 0.933,
        kcal = 93,
        steps = 1520,
        stepsEstimated = true,
        avgSpeedKmh = 0.9012,
        maxSpeedKmh = 1.0,
        samples = listOf(SpeedSample(0, 0.0), SpeedSample(5, 1.0), SpeedSample(10, 0.95)),
    )

    @Test
    fun `round trip keeps every field`() {
        val line = WorkoutJson.encode(workout)
        assertFalse(line.contains('\n'))
        assertEquals(workout, WorkoutJson.decode(line))
    }

    @Test
    fun `encoded line is plain flat JSON with dots as decimal separators`() {
        val line = WorkoutJson.encode(workout.copy(samples = emptyList()))
        assertEquals(
            "{\"id\":\"w-1700000000000\",\"startedAt\":1700000000000,\"endedAt\":1700003727000,\"duration\":3727," +
                "\"distanceKm\":0.933,\"kcal\":93,\"steps\":1520,\"stepsEstimated\":true,\"avgKmh\":0.9012,\"maxKmh\":1," +
                "\"samples\":[]}",
            line,
        )
    }

    @Test
    fun `special characters in the id survive`() {
        val odd = workout.copy(id = "w-\"quoted\" \\ back\nline\ttab \u0001 ünicode")
        assertEquals(odd, WorkoutJson.decode(WorkoutJson.encode(odd)))
    }

    @Test
    fun `unknown keys are ignored and missing optional ones default`() {
        val line = "{\"id\":\"w-1\",\"startedAt\":1,\"endedAt\":2,\"duration\":40,\"distanceKm\":0.1,\"kcal\":1,\"steps\":2," +
            "\"avgKmh\":1,\"maxKmh\":1,\"future\":{\"nested\":[1,2,{\"a\":null}]}}"
        val decoded = WorkoutJson.decode(line)
        assertEquals("w-1", decoded?.id)
        assertEquals(false, decoded?.stepsEstimated)
        assertTrue(decoded?.samples?.isEmpty() == true)
    }

    @Test
    fun `bad input decodes to null instead of crashing`() {
        for (bad in listOf("", "   ", "{", "[]", "null", "{\"id\":\"x\"}", "{\"id\":1,\"startedAt\":1}", "not json",
            "{\"id\":\"w\",\"startedAt\":\"1\",\"endedAt\":2,\"duration\":3,\"distanceKm\":0.1,\"kcal\":1,\"steps\":2,\"avgKmh\":1,\"maxKmh\":1}",
            WorkoutJson.encode(workout) + " trailing")) {
            assertNull(bad, WorkoutJson.decode(bad))
        }
    }

    @Test
    fun `odd numbers never break the line`() {
        val odd = workout.copy(distanceKm = Double.NaN, avgSpeedKmh = Double.POSITIVE_INFINITY)
        val decoded = WorkoutJson.decode(WorkoutJson.encode(odd))
        assertEquals(0.0, decoded!!.distanceKm, 0.0)
        assertEquals(0.0, decoded.avgSpeedKmh, 0.0)
    }
}
