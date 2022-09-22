package com.salvadormorado.clasificadorpapas

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ThumbnailUtils
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import com.amrdeveloper.lottiedialog.LottieDialog
import com.salvadormorado.clasificadorpapas.ml.ModelUnquant
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        button_ClasificarImagen = findViewById(R.id.button_ClasificarImagen)
        textView_Resultado = findViewById(R.id.textView_Resultado)
        textView_Probabilidad = findViewById(R.id.textView_Probabilidad)
        button_TomarImagen = findViewById(R.id.button_TomarImagen)
        spinner_Images = findViewById(R.id.spinner_Imagenes)

        image_thread = HandlerThread("http")
        image_thread!!.start()
        imageHandler = HttpHandler(this, image_thread!!.looper)

        var aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, arraImagenes)
        with(spinner_Images)
        {
            adapter = aa
            setSelection(0, false)
            onItemSelectedListener = this@MainActivity
            prompt = "Selecciona una opación..."
            gravity = Gravity.CENTER

        }

        button_TomarImagen.setOnClickListener({
            imageHandler!!.sendEmptyMessage(ID_IMAGE)
        })

        button_ClasificarImagen.setOnClickListener({
            if (idImage != 0) {
                //var image: Bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.papa)
                var image: Bitmap =
                    BitmapFactory.decodeResource(applicationContext.resources, idImage)
                //var image:Bitmap = bitmap!!
                val dimension = Math.min(image.width, image.height)
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
                imageView.setImageBitmap(image)
                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                classifyImage(image)
                //showDialogSucess()
            } else {
                Toast.makeText(
                    applicationContext,
                    "Debes seleccionar una opción...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private inner class HttpHandler(private val context: Context, looper: Looper) :
        Handler(looper) {
        override fun handleMessage(@NonNull msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                this@MainActivity.ID_IMAGE -> this@MainActivity.captureImage()
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
            val model = ModelUnquant.newInstance(applicationContext)

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
            textView_Resultado.setText("Resultado: \n" + classes[maxPos])
            textView_Probabilidad.setText("Probabilidad: " + s)

            if(classes[maxPos].toString().contentEquals("Papa sana")){
                showDialogg(R.raw.sucess, "Verificado", "¡La papa esta sana!")
            }else{
                showDialogg(R.raw.fail, "No verificado", "¡La papa es deforme o esta enferma!")
            }
            // Releases model resources if no longer used.
            model.close()
        } catch (e: IOException) {
            Toast.makeText(applicationContext, "Error al clasificar la imagen", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        Log.e("Pos", p2.toString())
        when (p2) {
            0 -> {
                idImage = 0
            }
            1 -> {
                idImage = R.drawable.papa_sana
                imageView.setImageResource(idImage)
            }
            2 -> {
                idImage = R.drawable.papa_enferma
                imageView.setImageResource(idImage)
            }
            3 -> {
                idImage = R.drawable.papa_deforme
                imageView.setImageResource(idImage)
            }
            4 -> {
                idImage = R.drawable.papa_verde
                imageView.setImageResource(idImage)
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

}