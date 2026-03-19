package com.feifan.fuckingnjit.model

import com.feifan.fuckingnjit.utils.RSAPasswordConverter
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class YiBan(
    @Id
    var uuid: Long = 0,
//    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var id: String = "",
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var password: String = "",
)

