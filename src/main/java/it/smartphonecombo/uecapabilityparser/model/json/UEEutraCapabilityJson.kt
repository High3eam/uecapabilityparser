package it.smartphonecombo.uecapabilityparser.model.json

import it.smartphonecombo.uecapabilityparser.extension.getObject
import it.smartphonecombo.uecapabilityparser.extension.getObjectAtPath
import it.smartphonecombo.uecapabilityparser.extension.repeat
import kotlinx.serialization.json.JsonObject

class UEEutraCapabilityJson(val rootJson: JsonObject) {
    val eutraCapabilityV9e0 =
        rootJson.getObjectAtPath(
            "nonCriticalExtension.".repeat(2) +
                "lateNonCriticalExtension" +
                ".nonCriticalExtension".repeat(3)
        )

    val eutraCapabilityV10i0 =
        eutraCapabilityV9e0?.getObjectAtPath("nonCriticalExtension".repeat(4, "."))

    val eutraCapabilityV11d0 = eutraCapabilityV10i0?.getObject("nonCriticalExtension")

    val eutraCapabilityV1020 = rootJson.getObjectAtPath("nonCriticalExtension".repeat(3, "."))

    val eutraCapabilityV1060 = eutraCapabilityV1020?.getObject("nonCriticalExtension")

    val eutraCapabilityV1090 = eutraCapabilityV1060?.getObject("nonCriticalExtension")

    val eutraCapabilityV1170 =
        eutraCapabilityV1090?.getObjectAtPath("nonCriticalExtension".repeat(2, "."))

    val eutraCapabilityV1180 = eutraCapabilityV1170?.getObject("nonCriticalExtension")

    val eutraCapabilityV11a0 = eutraCapabilityV1180?.getObject("nonCriticalExtension")

    val eutraCapabilityV1250 = eutraCapabilityV11a0?.getObject("nonCriticalExtension")

    val eutraCapabilityV1260 = eutraCapabilityV1250?.getObject("nonCriticalExtension")

    val eutraCapabilityV1270 = eutraCapabilityV1260?.getObject("nonCriticalExtension")

    val eutraCapabilityV1280 = eutraCapabilityV1270?.getObject("nonCriticalExtension")

    val eutraCapabilityV1310 = eutraCapabilityV1280?.getObjectAtPath("nonCriticalExtension")

    val eutraCapabilityV1320 = eutraCapabilityV1310?.getObject("nonCriticalExtension")

    val eutraCapabilityV1330 = eutraCapabilityV1320?.getObject("nonCriticalExtension")

    val eutraCapabilityV1340 = eutraCapabilityV1330?.getObject("nonCriticalExtension")

    val eutraCapabilityV1350 = eutraCapabilityV1340?.getObject("nonCriticalExtension")

    val eutraCapabilityV1430 =
        eutraCapabilityV1350?.getObjectAtPath("nonCriticalExtension".repeat(2, "."))

    val eutraCapabilityV1450 =
        eutraCapabilityV1430?.getObjectAtPath("nonCriticalExtension".repeat(2, "."))

    val eutraCapabilityV1460 = eutraCapabilityV1450?.getObject("nonCriticalExtension")

    val eutraCapabilityV1510 = eutraCapabilityV1460?.getObject("nonCriticalExtension")

    val eutraCapabilityV1530 =
        eutraCapabilityV1510?.getObjectAtPath("nonCriticalExtension".repeat(2, "."))

    val eutraCapabilityV1540 = eutraCapabilityV1530?.getObject("nonCriticalExtension")

    val eutraCapabilityV1560 =
        eutraCapabilityV1540?.getObjectAtPath("nonCriticalExtension".repeat(2, "."))

    val eutraCapabilityV1690 =
        eutraCapabilityV1560?.getObjectAtPath("nonCriticalExtension".repeat(7, "."))
}
