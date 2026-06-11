package com.flashsms.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: EditText
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvCounter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvCounter = findViewById(R.id.tvCounter)

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                tvCounter.text = "${s?.length ?: 0}/160"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSend.setOnClickListener {
            val phone = etPhoneNumber.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (phone.isEmpty()) {
                Toast.makeText(this, "شماره تلفن را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                Toast.makeText(this, "متن پیام را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.SEND_SMS), 1)
            } else {
                sendFlashSMS(phone, message)
            }
        }
    }

    private fun sendFlashSMS(phoneNumber: String, message: String) {
        try {
            val sentIntent = PendingIntent.getBroadcast(
                this, 0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )

            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (resultCode) {
                        RESULT_OK -> Toast.makeText(context,
                            "Flash SMS ارسال شد!", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context,
                            "خطا در ارسال پیام", Toast.LENGTH_SHORT).show()
                    }
                    unregisterReceiver(this)
                }
            }, IntentFilter("SMS_SENT"))

            // ساخت PDU برای Class 0
            val pdu = buildClass0PDU(phoneNumber, message)
            val smsManager = SmsManager.getDefault()
            smsManager.sendRawPdu(pdu, sentIntent, null)

            Toast.makeText(this, "در حال ارسال...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildClass0PDU(phoneNumber: String, message: String): ByteArray {
        val pdu = mutableListOf<Byte>()

        // SMSC (پیش‌فرض)
        pdu.add(0x00)

        // PDU Type
        pdu.add(0x01)

        // Message Reference
        pdu.add(0x00)

        // شماره مقصد
        val cleanNumber = phoneNumber.replace("+", "").replace(" ", "")
        pdu.add(cleanNumber.length.toByte())
        pdu.add(0x91.toByte()) // فرمت بین‌المللی

        val paddedNumber = if (cleanNumber.length % 2 != 0) "${cleanNumber}F" else cleanNumber
        for (i in paddedNumber.indices step 2) {
            val swapped = "${paddedNumber[i+1]}${paddedNumber[i]}"
            pdu.add(swapped.toInt(16).toByte())
        }

        // Protocol Identifier
        pdu.add(0x00)

        // Data Coding Scheme — 0x10 = Class 0 (Flash SMS)
        pdu.add(0x10)

        // Validity Period
        pdu.add(0xAA.toByte())

        // طول پیام
        pdu.add(message.length.toByte())

        // محتوای پیام (GSM 7-bit)
        val encoded = encodeGSM7Bit(message)
        pdu.addAll(encoded.toList())

        return pdu.toByteArray()
    }

    private fun encodeGSM7Bit(text: String): ByteArray {
        val result = mutableListOf<Byte>()
        var carry = 0
        var carryBits = 0

        for (char in text) {
            val code = char.code and 0x7F
            val combined = carry or (code shl carryBits)
            result.add((combined and 0xFF).toByte())
            carry = code shr (8 - carryBits)
            carryBits = (carryBits + 7) % 8
            if (carryBits == 0) {
                result.add(carry.toByte())
                carry = 0
            }
        }
        if (carryBits > 0) result.add(carry.toByte())
        return result.toByteArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val phone = etPhoneNumber.text.toString().trim()
            val message = etMessage.text.toString().trim()
            sendFlashSMS(phone, message)
        } else {
            Toast.makeText(this, "مجوز SMS رد شد", To
