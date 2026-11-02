package io.github.rickpro.stride.core.profile

import io.github.rickpro.stride.core.profile.ProfileForm.Fields
import io.github.rickpro.stride.core.units.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {
    private val metric = MeasureUnit.METRIC
    private val imperial = MeasureUnit.IMPERIAL
    private val me = Profile(Sex.MALE, heightCm = 175.26, weightKg = 70.0, ageYears = 41)

    @Test
    fun `fields are shown in the chosen unit system`() {
        assertEquals(Fields(Sex.MALE, "175", "", "70", "41"), ProfileForm.fieldsFor(me, metric))
        assertEquals(Fields(Sex.MALE, "5", "9", "154.3", "41"), ProfileForm.fieldsFor(me, imperial))
        assertEquals(Fields(), ProfileForm.fieldsFor(null, metric))
        assertEquals("", ProfileForm.fieldsFor(me.copy(ageYears = null), metric).age)
        assertEquals("70.5", ProfileForm.fieldsFor(me.copy(weightKg = 70.5), metric).weight)
    }

    @Test
    fun `metric fields parse to cm and kg`() {
        val p = ProfileForm.parse(Fields(Sex.FEMALE, "168", "", "61.5", ""), metric)!!
        assertEquals(Sex.FEMALE, p.sex)
        assertEquals(168.0, p.heightCm, 1e-9)
        assertEquals(61.5, p.weightKg, 1e-9)
        assertNull(p.ageYears)
    }

    @Test
    fun `imperial fields parse feet and inches and pounds to cm and kg`() {
        val p = ProfileForm.parse(Fields(Sex.MALE, "5", "9", "154", "30"), imperial)!!
        assertEquals(175.26, p.heightCm, 1e-9)
        assertEquals(69.853, p.weightKg, 1e-3)
        assertEquals(30, p.ageYears)
        // inches may be left empty, and may be fractional
        assertEquals(152.4, ProfileForm.parse(Fields(Sex.OTHER, "5", "", "120", ""), imperial)!!.heightCm, 1e-9)
        assertEquals(176.53, ProfileForm.parse(Fields(Sex.OTHER, "5", "9.5", "120", ""), imperial)!!.heightCm, 1e-9)
    }

    @Test
    fun `a comma works as a decimal separator and spaces are ignored`() {
        val p = ProfileForm.parse(Fields(Sex.OTHER, " 172,5 ", "", " 68,2", " 29 "), metric)!!
        assertEquals(172.5, p.heightCm, 1e-9)
        assertEquals(68.2, p.weightKg, 1e-9)
        assertEquals(29, p.ageYears)
    }

    @Test
    fun `anything missing or not a number gives no profile`() {
        assertNull(ProfileForm.parse(Fields(null, "170", "", "70", ""), metric)) // no sex chosen
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "", "", "70", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "170", "", "", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "tall", "", "70", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "170", "", "70", "old"), metric)) // age is optional but must be a number
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "NaN", "", "70", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "5.5", "9", "154", ""), imperial)) // feet are whole
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "5", "-1", "154", ""), imperial))
    }

    @Test
    fun `values outside the ranges give no profile`() {
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "99", "", "70", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "170", "", "19", ""), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "170", "", "70", "3"), metric))
        assertNull(ProfileForm.parse(Fields(Sex.MALE, "3", "0", "154", ""), imperial)) // 91 cm
        assertNotNull(ProfileForm.parse(Fields(Sex.MALE, "170", "", "70", "5"), metric)) // the edge is in
    }

    @Test
    fun `what is shown parses back to the same profile`() {
        for (unit in listOf(metric, imperial)) {
            val shown = ProfileForm.fieldsFor(me, unit)
            val back = ProfileForm.parse(shown, unit)!!
            assertEquals(me.sex, back.sex)
            assertEquals(me.heightCm, back.heightCm, 1.3) // whole cm or whole inch
            assertEquals(me.weightKg, back.weightKg, 0.1)
            assertEquals(me.ageYears, back.ageYears)
            assertTrue(back.isValid)
        }
    }
}
