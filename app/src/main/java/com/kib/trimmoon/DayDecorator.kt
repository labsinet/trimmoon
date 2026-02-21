// DayDecorator.kt (новий файл у тій самій папці)
package com.kib.trimmoon

import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class DayDecorator(
    private val dates: Set<CalendarDay>,
    private val color: Int
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    override fun decorate(view: DayViewFacade) {
        val drawable = ShapeDrawable(OvalShape()).apply {
            paint.color = color
        }
        view.background = drawable  // або view.addSpan(...), але background простіше
    }
}