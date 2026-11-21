package com.wanderwildwood.hitome

import org.junit.Assert.assertEquals
import org.junit.Test

class OneLineTest {
    private fun line(lead: String?, text: String, more: Int = 0) = Glance.Line(lead, text, false, more)

    @Test fun headingLeadsThenFirstLineThenWhatIsLeft() {
        val today = Glance.Section(
            "com.wanderwildwood.koyomi", "Today",
            listOf(line("10:00", "Ada Whitlock"), line("13:30", "Tomas Reyes"), line(null, "and 3 more", more = 3)),
        )
        val one = Glance.oneLine(today).lines.single()
        assertEquals("Today", one.lead)
        assertEquals("10:00 Ada Whitlock +4", one.text)
        assertEquals(null, Glance.oneLine(today).heading)
    }

    @Test fun withoutHeadingTheFirstLineStaysAsItIs() {
        val sky = Glance.Section("com.wanderwildwood.soramoyo", null, listOf(line("52°", "Rain from 3 pm")))
        val one = Glance.oneLine(sky).lines.single()
        assertEquals("52°", one.lead)
        assertEquals("Rain from 3 pm", one.text)
    }

    @Test fun aMarkIsNotRepeatedAfterTheHeading() {
        val memo = Glance.Section("com.wanderwildwood.oboegaki", "Memo", listOf(line("•", "Buy oats"), line("•", "Call Tomas")))
        assertEquals("Buy oats +1", Glance.oneLine(memo).lines.single().text)
    }
}
