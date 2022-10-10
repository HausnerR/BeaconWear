package org.altbeacon.beaconreference

import android.app.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.*
import org.altbeacon.bluetooth.BluetoothMedic
import android.bluetooth.le.AdvertiseSettings

import android.bluetooth.le.AdvertiseCallback

import org.altbeacon.beacon.BeaconTransmitter

import org.altbeacon.beacon.BeaconParser

import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.logging.LogManager


class BeaconReferenceApplication: Application(), MonitorNotifier {
    lateinit var region: Region

    override fun onCreate() {
        super.onCreate()

        val beaconManager = BeaconManager.getInstanceForApplication(this)

        // enabling debugging will send lots of verbose debug information from the library to Logcat
        // this is useful for troubleshooting problems
        // BeaconManager.setDebug(true)


        // The BluetoothMedic code here, if included, will watch for problems with the bluetooth
        // stack and optionally:
        // - power cycle bluetooth to recover on bluetooth problems
        // - periodically do a proactive scan or transmission to verify the bluetooth stack is OK
        BluetoothMedic.getInstance().enablePowerCycleOnFailures(this)
        // BluetoothMedic.getInstance().enablePeriodicTests(this, BluetoothMedic.SCAN_TEST + BluetoothMedic.TRANSMIT_TEST)

        // By default, the library will scan in the background every 5 minutes on Android 4-7,
        // which will be limited to scan jobs scheduled every ~15 minutes on Android 8+
        // If you want more frequent scanning (requires a foreground service on Android 8+),
        // configure that here.
        // If you want to continuously range beacons in the background more often than every 15 minutes,
        // you can use the library's built-in foreground service to unlock this behavior on Android
        // 8+.   the method below shows how you set that up.
        setupForegroundService()

        beaconManager.setEnableScheduledScanJobs(false)

        // The following code block effectively disables beacon scanning in the foreground service
        // to save battery.  Do not include this code block if you want to detect beacons
        beaconManager.beaconParsers.clear() // clearing all beacon parsers ensures nothing matches
        beaconManager.backgroundBetweenScanPeriod = Long.MAX_VALUE
        beaconManager.backgroundScanPeriod = 0
        beaconManager.foregroundBetweenScanPeriod = Long.MAX_VALUE
        beaconManager.foregroundScanPeriod = 0

        // The following code block activates the foreground service by starting background scanning
        region = Region("dummy-region", Identifier.parse("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"), null, null)
        beaconManager.startMonitoring(region)
        beaconManager.addMonitorNotifier(this)

        // This code block starts beacon transmission
        val beacon = Beacon.Builder()
            .setId1("144978bb-4ba1-438b-bdb1-062fead53c18")
            .setId2("100")
            .setId3("1")
            .setManufacturer(0x004c)
            .setTxPower(-59)
            .setDataFields(listOf(0))
            .build()

        // Change the layout below for other beacon types
        val beaconParser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")

        Log.d(TAG, "Starting advertising with UUID: " + beacon.id1 + ", major: " + beacon.id2 + ", minor: " + beacon.id3)

        val beaconTransmitter = BeaconTransmitter(applicationContext, beaconParser)
        beaconTransmitter.advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        beaconTransmitter.advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED
        beaconTransmitter.startAdvertising(beacon, object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertisement start failed with code: $errorCode")
            }

            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "Advertisement start succeeded.")
            }
        })
    }

    private fun setupForegroundService() {
        val builder = Notification.Builder(this, "BeaconWearApp")
        builder.setSmallIcon(R.drawable.ic_launcher_background)
        builder.setContentTitle("Beacon transmitting")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent)
        val channel =  NotificationChannel("beacon-wear-notification-channel", "BeaconWear", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager =  getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        builder.setChannelId(channel.id)
        BeaconManager.getInstanceForApplication(this).enableForegroundServiceScanning(builder.build(), 456)
    }

    companion object {
        const val TAG = "BeaconWear"
    }

    override fun didEnterRegion(region: Region?) {}
    override fun didExitRegion(region: Region?) {}
    override fun didDetermineStateForRegion(state: Int, region: Region?) {}

}