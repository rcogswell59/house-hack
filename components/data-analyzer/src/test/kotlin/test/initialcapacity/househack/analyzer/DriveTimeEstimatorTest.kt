package test.initialcapacity.househack.analyzer

import io.initialcapacity.househack.analyzer.DriveTimeEstimator
import org.junit.Test
import kotlin.test.assertEquals

class DriveTimeEstimatorTest {
    @Test
    fun cocoa_to_cape_canaveral_is_about_23_minutes() {
        val minutes = DriveTimeEstimator.minutes(
            fromLat = 28.3861, fromLng = -80.7420, toLat = 28.3922, toLng = -80.6077, roadFactor = 1.4, avgSpeedMph = 30.0,
        )
        assertEquals(22.9, minutes, 0.5)
    }

    @Test
    fun haversine_of_same_point_is_zero() {
        assertEquals(0.0, DriveTimeEstimator.haversineMiles(28.39, -80.60, 28.39, -80.60), 1e-9)
    }
}
