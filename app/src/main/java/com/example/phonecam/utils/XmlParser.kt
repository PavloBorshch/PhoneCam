package com.example.phonecam.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

// Робота з XML
object XmlParser {

    // Функція парсить XML рядок і повертає Map з ключами та значеннями
    fun parseLegacyStatus(xmlString: String): Map<String, String> {
        val resultMap = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()

        try {
            parser.setInput(StringReader(xmlString))
            var eventType = parser.eventType
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (currentTag != null && text.isNotBlank()) {
                            resultMap[currentTag] = text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return mapOf("Error" to "XML Parsing Failed")
        }

        return resultMap
    }
}