package com.salvadormorado.clasificadorpapas

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.cardview.widget.CardView

class ActivityMenu : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        setTitle("Menú")

        val cardView_Historial = findViewById<CardView>(R.id.cardView_Historial)
        val cardView_ClasificacionImagenes =
            findViewById<CardView>(R.id.cardView_ClasificacionImagenes)
        val cardView_ConfiguracionEnbalaje =
            findViewById<CardView>(R.id.cardView_ConfiguracionEnbalaje)
        val cardView_AltasParcelas = findViewById<CardView>(R.id.cardView_AltasParcelas)
        val cardView_ModificarParcelas = findViewById<CardView>(R.id.cardView_ModificarParcelas)

        cardView_Historial.setOnClickListener({
            val cambio: Intent = Intent(applicationContext, ActivityHistorial::class.java).
            apply {
                //putExtra("var1", "hola")
            }
            startActivity(cambio)
        })

        cardView_ClasificacionImagenes.setOnClickListener({
            val cambio: Intent = Intent(applicationContext, ActivityClasificacionImagenes::class.java)
            startActivity(cambio)
        })

        cardView_ConfiguracionEnbalaje.setOnClickListener({
            val cambio: Intent = Intent(applicationContext, ActivityConfiguracionEnbalaje::class.java)
            startActivity(cambio)
        })

        cardView_AltasParcelas.setOnClickListener({
            val cambio: Intent = Intent(applicationContext, ActivityAltas::class.java)
            startActivity(cambio)
        })

        cardView_ModificarParcelas.setOnClickListener({
            val cambio: Intent = Intent(applicationContext, ActivityModificar::class.java)
            startActivity(cambio)
        })
    }
}