package com.chelvivl.testterminal

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chelvivl.testterminal.ui.theme.TestTerminalTheme
import com.senter.iot.support.openapi.StBarcodeScanOperator
import com.senter.support.openapi.StBarcodeScanner
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException
import java.util.Date


class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.senter.barcode.scan.action" -> {
                    val result = intent.getStringExtra("scanResult")
                    val saveResult = result?.trim() ?: "Пусто"
                    Log.e("SENTER_BARCODE_SCAN", saveResult)
                    viewModel.setCodes(saveResult)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.e("Button", "onClick: $keyCode $event")
        viewModel.scan()
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        viewModel.setCodes(Build.MODEL)

        initScanModule()

        setContent {
            TestTerminalTheme {
                LazyColumn(
                    modifier = Modifier.padding(10.dp)
                ) {
                    items(items = viewModel.codes) {
                        Text(String.format("%d) %s", it.id, it.code), fontSize = 12.sp)
                    }
                    item {
                        Button(onClick = {
                            viewModel.scan()
                        }) {
                            Text("Сканировать")
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter("com.senter.barcode.scan.action")
        registerReceiver(receiver, intentFilter)
    }

    private fun initScanModule() {

        if (StBarcodeScanOperator.getInstance(this).isLibrarySupport) {
            StBarcodeScanOperator.getInstance(this).init { code ->
                if (code == StBarcodeScanOperator.ErrorCode.SUCCESS) {
                    viewModel.setCodes("Успешно инициализирована")
                } else {
                    viewModel.setCodes("Произошла какая-то ошибка!!!")
                }
            }
        } else {
            viewModel.setCodes("Ошибка во время инициализации библиотеки для сканирования")
        }
    }

    private fun unInitScanModule() {
        if (StBarcodeScanOperator.getInstance(this).isLibrarySupport) {
            StBarcodeScanOperator.getInstance(this).uninit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unInitScanModule()
        unregisterReceiver(receiver)
    }

}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private var _codes = mutableStateOf<List<Code>>(emptyList())
    private val scanner = StBarcodeScanner.getInstance()
    private val scannerOperator = StBarcodeScanOperator.getInstance(app)
    val codes
        get() = _codes.value

    private var currentId = 0

    fun setCodes(code: String) {
        val mutableList = mutableListOf<Code>()
        mutableList.add(Code(currentId++, code))
        if (_codes.value.size > 19) mutableList.addAll(_codes.value.subList(0, 19))
        else mutableList.addAll(_codes.value)
        _codes.value = mutableList
    }


    fun scan() {
        if (scannerOperator.isLibrarySupport) {
            viewModelScope.launch {
                try {
                    val barcodeInfo = scanner.scanBarcodeInfo()
                    try {
                        if (barcodeInfo != null && barcodeInfo.barcodeValueAsBytes != null) {
                            setCodes(
                                String(
                                    barcodeInfo.barcodeValueAsBytes, Charsets.UTF_8
                                )
                            )
                        } else {
                            setCodes("Нет данных")
                        }
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        } else {
            Toast.makeText(
                app,
                "Не поддерживается библиотека для сканирования на данном устройстве",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

data class Code(val id: Int, val code: String)