package handboard.app.currency

import org.junit.Assert.*
import org.junit.Test

class CurrencyConvertTest {

    private fun repoWith(vararg rates: Pair<String, Double>): CurrencyRepository {
        val r = CurrencyRepository()
        r.setRates(mapOf(*rates))
        return r
    }

    @Test fun convertsByRatio() {
        val r = repoWith("USD" to 1.0, "EUR" to 0.9)
        assertEquals(90.0, r.convert(100.0, "USD", "EUR")!!, 1e-9)
    }

    @Test fun sameCurrencyIdentity() {
        val r = repoWith("USD" to 1.0)
        assertEquals(5.0, r.convert(5.0, "USD", "USD")!!, 1e-9)
    }

    @Test fun nullOnMissingEmptyOrZero() {
        val r = repoWith("USD" to 1.0, "ZERO" to 0.0)
        assertNull(r.convert(1.0, "USD", "XXX"))
        assertNull(r.convert(1.0, "XXX", "USD"))
        assertNull(r.convert(1.0, "USD", "ZERO"))
        assertNull(CurrencyRepository().convert(1.0, "USD", "EUR"))
    }

    @Test fun parsesSuccessBody() {
        val body = """{"result":"success","rates":{"USD":1.0,"EUR":0.9,"BAD":"x"}}"""
        val map = CurrencyRepository.parseRates(body)!!
        assertEquals(1.0, map["USD"]!!, 1e-9)
        assertEquals(0.9, map["EUR"]!!, 1e-9)
        assertFalse(map.containsKey("BAD"))
    }

    @Test fun rejectsErrorBody() {
        assertNull(CurrencyRepository.parseRates("""{"result":"error"}"""))
    }

    @Test fun currencyListPrioritizesMajors() {
        val list = CurrencyRepository.buildCurrencyList(mapOf("USD" to 1.0, "EUR" to 1.0, "ZZZ" to 1.0, "TRY" to 1.0, "GBP" to 1.0))
        assertEquals(listOf("TRY", "USD", "EUR", "GBP", "ZZZ"), list.map { it.code })
    }
}
