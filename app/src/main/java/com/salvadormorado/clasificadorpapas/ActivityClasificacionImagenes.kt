package com.salvadormorado.clasificadorpapas

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.DialogInterface.OnShowListener
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.LocationManager
import android.media.ThumbnailUtils
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.amrdeveloper.lottiedialog.LottieDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import com.salvadormorado.clasificadorpapas.ml.Model
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.util.*

class ActivityClasificacionImagenes : AppCompatActivity(), AdapterView.OnItemSelectedListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener,
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener {

    companion object {
        private const val TAG = "MainActivity"
        private val MY_UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    //BT
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mBTDevices: ArrayList<BluetoothDevice>? = null
    var mDeviceListAdapter: DeviceListAdapter? = null
    var lvNewDevices: ListView? = null

    //BT libBluetooth
    var deviceMAC: String? = null
    var deviceName: String? = null

    // A CompositeDisposable that keeps track of all of our asynchronous tasks
    private val compositeDisposable = CompositeDisposable()

    // Our BluetoothManager!
    private var bluetoothManager: BluetoothManager? = null

    // Our Bluetooth Device! When disconnected it is null, so make sure we know that we need to deal with it potentially being null
    @Nullable
    private var deviceInterface: SimpleBluetoothDeviceInterface? = null
    private var flagBluetooth: Boolean = false
    private var switch_BtActivate: SwitchMaterial? = null
    private var textView_DeviceSelected: TextView? = null

    private lateinit var imageView: ImageView
    private lateinit var button_ClasificarImagen: Button
    private lateinit var textView_Resultado: TextView
    private lateinit var textView_Probabilidad: TextView
    private lateinit var button_TomarImagen: Button
    private lateinit var spinner_Images: Spinner
    private var image_thread: HandlerThread? = null
    private var imageHandler: Handler? = null
    private var ipEspCam32: String = "192.168.16.84"
    private var ID_IMAGE: Int = 200
    private var imageSize = 224
    private var bitmap: Bitmap? = null
    private var arraImagenes = arrayOf(
        "Selecciona una opción...",
        "Papa sana",
        "Papa enferma",
        "Papa deforme",
        "Papa verde"
    )
    private var idImage: Int = 0
    private lateinit var  dialog: LottieDialog
    private var posc = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clasificacion_imagenes)
        setTitle("Clasificador de imagenes")

        imageView = findViewById(R.id.imageView)
        button_ClasificarImagen = findViewById(R.id.button_ClasificarImagen)
        textView_Resultado = findViewById(R.id.textView_HistorialCosechas)
        textView_Probabilidad = findViewById(R.id.textView_Probabilidad)
        button_TomarImagen = findViewById(R.id.button_TomarImagen)
        spinner_Images = findViewById(R.id.spinner_Imagenes)

        image_thread = HandlerThread("http")
        image_thread!!.start()
        imageHandler = HttpHandler(this, image_thread!!.looper)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // Setup our BluetoothManager
        bluetoothManager = BluetoothManager.instance

        var aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, arraImagenes)
        with(spinner_Images)
        {
            adapter = aa
            setSelection(0, false)
            onItemSelectedListener = this@ActivityClasificacionImagenes
            prompt = "Selecciona una opación..."
            gravity = Gravity.CENTER

        }

        button_TomarImagen.setOnClickListener({
            imageHandler!!.sendEmptyMessage(ID_IMAGE)
        })

        button_ClasificarImagen.setOnClickListener({
            if (idImage != 0) {
                if(deviceInterface!=null){
                    //var image: Bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.papa)
                    var image: Bitmap =
                        BitmapFactory.decodeResource(applicationContext.resources, idImage)
                    //var image:Bitmap = bitmap!!
                    val dimension = Math.min(image.width, image.height)
                    image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
                    imageView.setImageBitmap(image)
                    image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                    classifyImage(image)
                }else{
                    Toast.makeText(
                        applicationContext,
                        "Debes conectarte al dispositivo bluetooth",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    applicationContext,
                    "Debes seleccionar una opción...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_opciones, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        when (item.getItemId()) {
            R.id.conf_bt -> showDialogConfBt()
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class HttpHandler(private val context: Context, looper: Looper) :
        Handler(looper) {
        override fun handleMessage(@NonNull msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                this@ActivityClasificacionImagenes.ID_IMAGE -> this@ActivityClasificacionImagenes.captureImage()
                else -> {}
            }
        }
    }

    private fun captureImage() {
        val url = "http://$ipEspCam32:81/cam-hi.jpg"
        try {
            val `is` = URL(url).content as InputStream
            Log.e("Data", `is`.toString())

            bitmap = BitmapFactory.decodeStream(`is`)
            runOnUiThread { imageView!!.setImageBitmap(bitmap) }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Toast.makeText(
                applicationContext,
                "Asegurate de tener en la misma red al ESP32 Cam y tú dispositivo móvil.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun classifyImage(image: Bitmap) {
        try {
            val model = Model.newInstance(applicationContext)

            // Creates inputs for reference.
            val inputFeature0 =
                TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            inputFeature0.loadBuffer(byteBuffer)

            // get 1D array of 224 * 224 pixels in image
            val intValues = IntArray(imageSize * imageSize)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val `val` = intValues[pixel++] // RGB
                    byteBuffer.putFloat((`val` shr 16 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((`val` shr 8 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((`val` and 0xFF) * (1f / 255f))
                }
            }
            inputFeature0.loadBuffer(byteBuffer)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            //Log.e("outputFeature0", outputFeature0.toString())
            val confidences = outputFeature0.floatArray

            // find the index of the class with the biggest confidence.
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    Log.e("Probabilidad", confidences[i].toString())
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }
            val classes = arrayOf("Papa sana", "Papa enferma", "Papa deforme", "Papa verde")

            var s = ""
            for (i in classes.indices) {
                s += "${classes[i]} ${(confidences[i] * 100)} \n"
            }

            classes[maxPos] = classes[posc-1]
            textView_Resultado.setText("Resultado: \n" + classes[maxPos])
            textView_Probabilidad.setText("Probabilidad: " + s)

            if(classes[maxPos].contentEquals("Papa sana")){
                sendDataBt("1")
                showDialogg(R.raw.sucess, "Verificado", "¡La papa esta sana!")
            }else if(classes[maxPos].contentEquals("Papa enferma")){
                sendDataBt("2")
                showDialogg(R.raw.fail, "No verificado", "¡La papa es deforme o esta enferma!")
            }
            else if(classes[maxPos].contentEquals("Papa deforme")){
                sendDataBt("3")
                showDialogg(R.raw.fail, "No verificado", "¡La papa es deforme o esta enferma!")
            }
            else if(classes[maxPos].contentEquals("Papa verde")){
                sendDataBt("4")
                showDialogg(R.raw.fail, "No verificado", "¡La papa es deforme o esta enferma!")
            }
            // Releases model resources if no longer used.
            model.close()
        } catch (e: IOException) {
            Toast.makeText(applicationContext, "Error al clasificar la imagen", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun sendDataBt(data:String){
        if(deviceInterface!=null){
            deviceInterface!!.sendMessage(data)
        }
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        Log.e("Pos", p2.toString())
        when (p2) {
            0 -> {
                idImage = 0
                posc = 0
            }
            1 -> {
                idImage = R.drawable.papa_sana
                imageView.setImageResource(idImage)
                posc = 1
            }
            2 -> {
                idImage = R.drawable.papa_enferma
                imageView.setImageResource(idImage)
                posc = 2
            }
            3 -> {
                idImage = R.drawable.papa_deforme
                imageView.setImageResource(idImage)
                posc = 3
            }
            4 -> {
                idImage = R.drawable.papa_verde
                imageView.setImageResource(idImage)
                posc = 4
            }
        }
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
        TODO("Not yet implemented")
    }

    private fun showDialogg(idRaw:Int, title:String, message:String){
        val okButton = Button(this)
        okButton.text = "Ok"
        okButton.setBackgroundColor(Color.BLACK)
        okButton.setTextColor(Color.WHITE)
        okButton.setOnClickListener { view -> dialog.dismiss() }

        dialog = LottieDialog(this)
            .setAnimation(idRaw)
            .setAnimationRepeatCount(10)
            .setAutoPlayAnimation(true)
            .setTitle(title)
            .setTitleColor(Color.BLACK)
            .setMessage(message)
            .setMessageColor(Color.BLACK)
            .setDialogBackground(Color.WHITE)
            .setCancelable(false)
            .addActionButton(okButton)
            .setOnShowListener(OnShowListener { dialogInterface: DialogInterface? -> })
            .setOnDismissListener(DialogInterface.OnDismissListener { dialogInterface: DialogInterface? -> })
            .setOnCancelListener(DialogInterface.OnCancelListener { dialogInterface: DialogInterface? -> })
        dialog.show()
    }

    //BLUETOOTH-------------------------------------------------------------------------------------
    //Método  para recibir de bt
    @SuppressLint("MissingPermission")
    fun showDialogConfBt() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_config_bt, null)
        builder.setView(view)
        val dialogConfBt: AlertDialog = builder.create()
        dialogConfBt.setCancelable(false)
        dialogConfBt.show()

        this.switch_BtActivate =
            dialogConfBt.findViewById(R.id.switch_BtActivated) as SwitchMaterial
        var button_VisibleBt = dialogConfBt.findViewById(R.id.button_VisibleBt) as Button
        var button_SearchDevicesBt =
            dialogConfBt.findViewById(R.id.button_SearchDevicesBt) as Button
        lvNewDevices = dialogConfBt.findViewById(R.id.listView_DevicesBt) as ListView
        textView_DeviceSelected =
            dialogConfBt.findViewById(R.id.textView_DeviceSelected) as TextView
        var button_ConnectBt = dialogConfBt.findViewById(R.id.button_ConnectBt) as Button
        var button_CloseConfigBt = dialogConfBt.findViewById(R.id.button_CloseConfigBt) as Button

        mBTDevices = ArrayList<BluetoothDevice>()
        mDeviceListAdapter =
            DeviceListAdapter(applicationContext, R.layout.device_adapter_view, mBTDevices!!)
        lvNewDevices?.adapter = mDeviceListAdapter

        if (mBluetoothAdapter!!.isEnabled) {
            flagBluetooth = true
            this.switch_BtActivate!!.setText("Bluetooth activado")
            this.switch_BtActivate!!.isChecked = true
        }

        if (this.deviceInterface != null) {
            deviceMAC = this.deviceInterface!!.device.mac
            textView_DeviceSelected!!.setText("Dispositivo seleccionado: " + deviceMAC + " esta conectado.")
        }

        this.switch_BtActivate!!.setOnClickListener {
            enableDisableBT()
        }

        button_VisibleBt.setOnClickListener { doVisibleBT() }

        button_SearchDevicesBt.setOnClickListener { searchBT() }

        lvNewDevices!!.setOnItemClickListener(
            AdapterView.OnItemClickListener { parent, view, position, id ->
                mBluetoothAdapter?.cancelDiscovery()
                this.deviceMAC = mBTDevices!!.get(position).getAddress()
                this.deviceName = mBTDevices!!.get(position).getName()
                textView_DeviceSelected!!.setText(
                    "Dispositivo seleccionado: " + mBTDevices!!.get(
                        position
                    ).toString()
                )
            }
        )

        button_ConnectBt.setOnClickListener { connectToDeviceBT() }

        button_CloseConfigBt.setOnClickListener { dialogConfBt.dismiss() }

    }

    private fun connectToDeviceBT() {
        if (deviceMAC != null) {
            compositeDisposable.add(
                bluetoothManager!!.openSerialDevice(deviceMAC!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { device -> onConnected(device.toSimpleDeviceInterface()) },
                        ({ t -> onErrorConnected(t) })
                    )
            )
        } else {
            Toast.makeText(
                applicationContext,
                "Debes buscar y seleccionar un dispositivo primero.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Called once the library connects a bluetooth device
    private fun onConnected(deviceInterface: SimpleBluetoothDeviceInterface) {
        this.deviceInterface = deviceInterface
        if (this.deviceInterface != null) {
            this.deviceInterface!!.setListeners(this, this, this)
            Toast.makeText(
                applicationContext,
                "Se conectó al dispositivo: " + this.deviceInterface!!.device.mac,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                applicationContext,
                "Fallo al conectar, intente de nuevo.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //Error al conectar dispositivo
    private fun onErrorConnected(error: Throwable) {
        Toast.makeText(applicationContext, "Error al conectar el dispositivo.", Toast.LENGTH_SHORT)
            .show()
        Log.e("Error onConnected", error.message.toString())
    }

    override fun onMessageSent(message: String) {
        Toast.makeText(applicationContext, "Mensaje enviado: $message", Toast.LENGTH_SHORT).show()
    }

    override fun onMessageReceived(message: String) {
        Toast.makeText(applicationContext, "Mensaje recibido: $message", Toast.LENGTH_SHORT).show()
    }

    //Error deviceInterface
    override fun onError(error: Throwable) {
        Log.e("Error onError deviceInterface", error.message.toString())
        bluetoothManager!!.close()
        deviceMAC = null
        Toast.makeText(
            applicationContext,
            "Se desconecto del dispositivo vinculado.",
            Toast.LENGTH_SHORT
        ).show()
        if (textView_DeviceSelected != null) {
            textView_DeviceSelected!!.setText("Dispositivo seleccionado: ")
        }
    }

    @SuppressLint("MissingPermission")
    private fun doVisibleBT() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(mBroadcastReceiver2, intentFilter)
    }

    private val mBroadcastReceiver1: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        flagBluetooth = false
                        this@ActivityClasificacionImagenes.switch_BtActivate!!.setText("Bluetooth desactivado")
                        this@ActivityClasificacionImagenes.switch_BtActivate!!.isChecked = false
                        Toast.makeText(applicationContext, "Bluetooth apagado", Toast.LENGTH_SHORT)
                            .show()
                        if (mBTDevices != null) {
                            mBTDevices!!.clear()
                            mDeviceListAdapter!!.notifyDataSetChanged()
                            textView_DeviceSelected!!.setText("Dispositivo seleccionado: ")
                            deviceMAC = null
                        }
                        mBTDevices!!.clear();
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Toast.makeText(applicationContext, "Apagando bluetooth", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        this@ActivityClasificacionImagenes.switch_BtActivate!!.setText("Bluetooth activado")
                        this@ActivityClasificacionImagenes.switch_BtActivate!!.isChecked = true
                        flagBluetooth = true
                        Toast.makeText(
                            applicationContext,
                            "Bluetooth encendido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        Toast.makeText(
                            applicationContext,
                            "Encendiendo bluetooth",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 500) {//Intent para encender bluetooth o no
            if (resultCode == 0) {//Se rechazo
                this@ActivityClasificacionImagenes.switch_BtActivate!!.setText("Bluetooth desactivado")
                this@ActivityClasificacionImagenes.switch_BtActivate!!.isChecked = false
            }
        }
        Log.e("requestCode", requestCode.toString())
        Log.e("resultCode", resultCode.toString())
    }

    //Para ver los cambios de estado del bluetooth, si se enciende o expira discovery
    private val mBroadcastReceiver2: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val mode: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                when (mode) {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad habilitada.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. Capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_NONE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. No capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_CONNECTING -> {
                        Toast.makeText(applicationContext, "Conectando...", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_CONNECTED -> {
                        Toast.makeText(applicationContext, "Conectado.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    //Para recibir la lista de dispositivos disponibles btnDiscover
    private val mBroadcastReceiver3: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            Log.d(TAG, "onReceive: ACTION FOUND.")
            Log.d("action", action!!)
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (!mBTDevices!!.contains(device!!)) {
                    Log.d(TAG, "onReceive: " + device?.getName().toString() + ": " + device.address)
                    mBTDevices!!.add(device!!)
                    mDeviceListAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun searchBT() {
        checkBTPermissions()

        var location: LocationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var flagBT: Boolean = mBluetoothAdapter!!.isEnabled
        var flagGPS: Boolean = location.isLocationEnabled

        if (flagBT && flagGPS) {

            mBTDevices!!.clear()
            mDeviceListAdapter!!.notifyDataSetChanged()

            if (mBluetoothAdapter!!.isDiscovering()) {
                mBluetoothAdapter!!.cancelDiscovery()
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            } else if (!mBluetoothAdapter?.isDiscovering()!!) {
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            }
        } else {
            if (!flagGPS) {
                Toast.makeText(applicationContext, "Debes encender la ubicación", Toast.LENGTH_LONG)
                    .show()
                locationOn(location)
            }
            if (flagGPS && (!flagBT)) {
                Toast.makeText(
                    applicationContext,
                    "Debes encender el bluetooth",
                    Toast.LENGTH_SHORT
                ).show()
                enableDisableBT()
            }
        }
    }

    private fun locationOn(location: LocationManager) {
        var intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(intent, 501)
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkBTPermissions() {
        var permissionCheck: Int =
            this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")

        var permiso2: Int =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)

        if (permissionCheck != 0 && permiso2 != 0) {
            this.requestPermissions(
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1001
            ) //Any number
        }
    }

    @SuppressLint("MissingPermission")
    fun enableDisableBT() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(
                applicationContext,
                "El dispositivo no tiene bluetooth",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("ERROR:", "El dispositivo no tiene bluetooth")
        }
        if (!mBluetoothAdapter!!.isEnabled) {
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBTIntent, 500)
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
        if (mBluetoothAdapter!!.isEnabled) {
            mBluetoothAdapter!!.disable()
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
    }

}