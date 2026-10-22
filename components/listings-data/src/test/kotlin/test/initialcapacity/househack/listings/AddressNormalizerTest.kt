package test.initialcapacity.househack.listings

import io.initialcapacity.househack.listings.AddressNormalizer
import org.junit.Test
import kotlin.test.assertEquals

class AddressNormalizerTest {
    @Test
    fun normalizes_case_punctuation_directionals_and_suffixes() {
        assertEquals("123 N ATLANTIC AVE", AddressNormalizer.normalize("123 North Atlantic Avenue, Cape Canaveral"))
    }

    @Test
    fun collapses_whitespace_and_abbreviates_common_suffixes() {
        assertEquals("45 OCEAN BLVD", AddressNormalizer.normalize("  45   Ocean  Boulevard. "))
        assertEquals("7 PALM CT", AddressNormalizer.normalize("7 Palm Court"))
        assertEquals("8 W RIVER DR", AddressNormalizer.normalize("8 West River Drive"))
    }
}
