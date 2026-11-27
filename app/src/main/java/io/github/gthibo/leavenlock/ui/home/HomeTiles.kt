package io.github.gthibo.leavenlock.ui.home

import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.settings.NotificationField

/** A small stat tile on Home's vehicle card. */
enum class HomeTile { RANGE, TWELVE_VOLT, CAR_ON, CLIMATE }

/**
 * The tiles Home shows, in order: the "Vehicle details" the user turned on that the car
 * reported. The lock state and drive battery above the tiles always show, so LOCK_STATE and
 * EV_BATTERY have no tile.
 */
fun homeTiles(status: VehicleStatus, fields: Set<NotificationField>): List<HomeTile> = buildList {
    if (NotificationField.RANGE in fields && status.rangeMiles != null) add(HomeTile.RANGE)
    if (NotificationField.TWELVE_VOLT in fields && status.twelveVoltPercent != null) add(HomeTile.TWELVE_VOLT)
    if (NotificationField.ENGINE in fields) add(HomeTile.CAR_ON)
    if (NotificationField.CLIMATE in fields && status.climateOn != null) add(HomeTile.CLIMATE)
}
