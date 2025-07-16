package com.example.childlocate.unuse


import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Address
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.ChildLocationViewModel
import com.example.childlocate.MapActivity
import com.example.childlocate.MyFirebaseManager
import com.example.childlocate.databinding.ActivitySecondBinding
import com.example.childlocate.service.MyFirebaseMessagingService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


const val CHANNEL_ID="Location Update"


class SecondActivity : AppCompatActivity() {

    //Khai bao
    private lateinit var binding: ActivitySecondBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //private lateinit var alarmManager: AlarmManager
    //private lateinit var alarmIntent: PendingIntent
    private val viewModel: ChildLocationViewModel by lazy {
        ViewModelProvider(this)[ChildLocationViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Inflate, set layout cho activity
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //khoi tao bien de truy cap vi tri
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        MyFirebaseManager.initFirebase(this)
        requestLocationPermissions()

        binding.getLocation.setOnClickListener {
            //getLocation()
            //Log.d("Click","Get Location")
            sendLocationRequestViaFCM()
        }
        binding.shareLocation.setOnClickListener{
            //shareLocation()
            //ham chia se vi tri 15 phut 1 lan
            //scheduleLocationWorker()

            //ham chia se vi tri lien tuc
            //startLocationService()

            startLocationByFCM()

        }
        binding.stopLocationShare.setOnClickListener {
            stopLocationSharing()
            //openMapActivity()
            //getLocation()
            //Log.d("Click","Get Location")
        }

    }
    private fun openMapActivity(){
        val intent = Intent(this, MapActivity::class.java)
        startActivity(intent)
    }
    private fun stopLocationSharing() {

        val stopIntent = Intent(this, MyFirebaseMessagingService::class.java)
        stopService(stopIntent)


    }
    /*private fun startLocationService() {
        val serviceIntent = Intent(this, LocationForegroundService::class.java)
        startService(serviceIntent)
    }*/



    private fun startLocationByFCM(){
        val serviceIntent = Intent(this, MyFirebaseMessagingService::class.java)
        startService(serviceIntent)
        Log.d("FCM","Chuyen sang intent thanh cong")

    }
    private fun sendLocationRequestViaFCM(){
        val projectId = "childlocatedemo-6c5da"
        //viewModel.sendLocationRequest(projectId)
    }


    /* private fun sendLocationRequestViaFCM() {
         FirebaseMessaging.getInstance().token
             .addOnSuccessListener { token ->
                 Log.d("FCM", "FCM token: $token")

                 val projectId = "childlocatedemo-6c5da"
                 try {
                     val serviceAccount: InputStream = applicationContext.assets.open("childlocatedemo.json")
                     Log.d("FCM", "Service account file opened successfully")
                     val credentials = GoogleCredentials.fromStream(serviceAccount)
                         .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                     AsyncTask.execute {
                         try {
                             val accessToken = credentials.refreshAccessToken().tokenValue
                             Log.d("FCM","accessToken: $accessToken")
                             val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")

                             val connection = url.openConnection() as HttpURLConnection
                             connection.requestMethod = "POST"
                             connection.setRequestProperty("Authorization", "Bearer $accessToken")
                             connection.setRequestProperty("Content-Type", "application/json")
                             connection.doOutput = true

                             val requestData = "{\"message\": {\"token\": \"$token\", \"data\": {\"request_type\": \"location_request\"}}}"

                             val outputStream: OutputStream = connection.outputStream
                             outputStream.write(requestData.toByteArray(StandardCharsets.UTF_8))
                             outputStream.flush()

                             val responseCode = connection.responseCode
                             Log.d("FCM", "FCM HTTP response code: $responseCode")

                             val responseMessage = connection.inputStream.bufferedReader().readText()
                             Log.d("FCM", "FCM HTTP response message: $responseMessage")

                             connection.disconnect()

                         } catch (e: IOException) {
                             Log.d("FCM", "Error connecting to FCM: ${e.message}")
                         }
                     }

                 } catch (e: FileNotFoundException) {
                     Log.d("FCM", "File not found: ${e.message}")
                 } catch (e: IOException) {
                     Log.d("FCM", "Error reading file: ${e.message}")
                 } catch (e: Exception) {
                     Log.e("FCM", "Error sending FCM message via HTTP: ${e.message}")
                 }
             }
             .addOnFailureListener { e ->
                 Log.e("FCM", "Failed to get FCM token: ${e.message}")
             }
     }*/


    private fun getLocation() {
        val userId = "1" // Replace with actual userId of the user sharing location
        viewModel.getSharedLocation(userId)
        viewModel.locationInfo.observe(this, Observer { address ->
            address?.let {
                updateLocationInfo(address)
            } ?: run {
                Toast.makeText(this, "Failed to get shared location", Toast.LENGTH_SHORT).show()
            }
        })
    }

    //ham cap nhat giao dien
    @SuppressLint("SetTextI18n")
    private fun updateLocationInfo(address: Address) {
        binding.lattitude.text = "Latitude: ${address.latitude}"
        binding.longitude.text = "Longitude: ${address.longitude}"
        binding.address.text = "Address: ${address.getAddressLine(0)}"
        binding.city.text = "City: ${address.locality}"
        binding.country.text = "Country: ${address.countryName}"
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }


    private fun requestLocationPermissions(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onStart() {
        super.onStart()
        requestLocationPermissions()
    }

    /* private fun scheduleLocationSharing() {
         val intervalMillis: Long =  10 * 1000 // 5 minutes
         val firstMillis = System.currentTimeMillis() // get time now
         alarmManager.setInexactRepeating(
             AlarmManager.RTC_WAKEUP,
             firstMillis + intervalMillis,
             intervalMillis,
             alarmIntent
         )
     }*/

    /*
private fun shareLocation(){
    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    viewModel.shareLocation(location.latitude, location.longitude)
                    Toast.makeText(this, "Location shared successfully", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this, "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                }
            }
    } else {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            SecondActivity.REQUEST_CODE_LOCATION_PERMISSION
        )
    }
}*/
}
