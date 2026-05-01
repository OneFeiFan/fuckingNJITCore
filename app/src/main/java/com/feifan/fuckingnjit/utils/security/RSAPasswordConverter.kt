package com.feifan.fuckingnjit.utils.security

import io.objectbox.converter.PropertyConverter

class RSAPasswordConverter : PropertyConverter<String?, String?> {
    override fun convertToEntityProperty(databaseValue: String?): String? {
        if (databaseValue == null) {
            return null
        }
        return SecureUtil.rsaDecrypt(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: String?): String? {
        if (entityProperty == null) {
            return null
        }
        return SecureUtil.rsaEncrypt(entityProperty)
    }
}