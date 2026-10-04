package com.kusal.solargridxmobile.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kusal.solargridxmobile.data.model.EnergyReservation

class ReservationDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "solargridx_local.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_RESERVATIONS = "reservations"
        const val COL_ID = "id"
        const val COL_PROSUMER_NIC = "prosumer_nic"
        const val COL_STATION_ID = "station_id"
        const val COL_STATION_NAME = "station_name"
        const val COL_SLOT_ID = "slot_id"
        const val COL_TRANSFER_ID = "transfer_id"
        const val COL_RESERVATION_DATE = "reservation_date"
        const val COL_START_TIME = "start_time"
        const val COL_END_TIME = "end_time"
        const val COL_REQUESTED_KWH = "requested_kwh"
        const val COL_STATUS = "status"
        const val COL_CANCELLATION_REASON = "cancellation_reason"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_RESERVATIONS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_PROSUMER_NIC TEXT NOT NULL,
                $COL_STATION_ID TEXT NOT NULL,
                $COL_STATION_NAME TEXT,
                $COL_SLOT_ID TEXT NOT NULL,
                $COL_TRANSFER_ID TEXT,
                $COL_RESERVATION_DATE TEXT NOT NULL,
                $COL_START_TIME TEXT NOT NULL,
                $COL_END_TIME TEXT NOT NULL,
                $COL_REQUESTED_KWH REAL NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_CANCELLATION_REASON TEXT,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_UPDATED_AT TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE $TABLE_RESERVATIONS ADD COLUMN $COL_TRANSFER_ID TEXT")
    }

    fun saveReservations(reservations: List<EnergyReservation>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (res in reservations) {
                val values = ContentValues().apply {
                    put(COL_ID, res.id)
                    put(COL_PROSUMER_NIC, res.prosumerNIC)
                    put(COL_STATION_ID, res.stationId)
                    put(COL_STATION_NAME, res.stationName)
                    put(COL_SLOT_ID, res.slotId)
                    put(COL_TRANSFER_ID, res.transferId)
                    put(COL_RESERVATION_DATE, res.reservationDate)
                    put(COL_START_TIME, res.startTime)
                    put(COL_END_TIME, res.endTime)
                    put(COL_REQUESTED_KWH, res.requestedEnergyKwh)
                    put(COL_STATUS, res.status)
                    put(COL_CANCELLATION_REASON, res.cancellationReason)
                    put(COL_CREATED_AT, res.createdAt)
                    put(COL_UPDATED_AT, res.updatedAt)
                }
                db.insertWithOnConflict(
                    TABLE_RESERVATIONS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getReservationsForProsumer(nic: String): List<EnergyReservation> {
        val list = mutableListOf<EnergyReservation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RESERVATIONS,
            null,
            "$COL_PROSUMER_NIC = ?",
            arrayOf(nic),
            null,
            null,
            "$COL_CREATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val res = EnergyReservation(
                    id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                    prosumerNIC = it.getString(it.getColumnIndexOrThrow(COL_PROSUMER_NIC)),
                    stationId = it.getString(it.getColumnIndexOrThrow(COL_STATION_ID)),
                    stationName = it.getString(it.getColumnIndexOrThrow(COL_STATION_NAME)),
                    slotId = it.getString(it.getColumnIndexOrThrow(COL_SLOT_ID)),
                    transferId = it.getString(it.getColumnIndexOrThrow(COL_TRANSFER_ID)),
                    reservationDate = it.getString(it.getColumnIndexOrThrow(COL_RESERVATION_DATE)),
                    startTime = it.getString(it.getColumnIndexOrThrow(COL_START_TIME)),
                    endTime = it.getString(it.getColumnIndexOrThrow(COL_END_TIME)),
                    requestedEnergyKwh = it.getDouble(it.getColumnIndexOrThrow(COL_REQUESTED_KWH)),
                    status = it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                    cancellationReason = it.getString(it.getColumnIndexOrThrow(COL_CANCELLATION_REASON)),
                    createdAt = it.getString(it.getColumnIndexOrThrow(COL_CREATED_AT)),
                    updatedAt = it.getString(it.getColumnIndexOrThrow(COL_UPDATED_AT))
                )
                list.add(res)
            }
        }
        return list
    }

    fun updateReservationStatus(id: String, newStatus: String, reason: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_STATUS, newStatus)
            if (reason != null) {
                put(COL_CANCELLATION_REASON, reason)
            }
        }
        db.update(TABLE_RESERVATIONS, values, "$COL_ID = ?", arrayOf(id))
    }
}
