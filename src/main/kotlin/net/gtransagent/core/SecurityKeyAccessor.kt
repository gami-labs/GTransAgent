package net.gtransagent.core

import net.gtransagent.internal.Constant
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object SecurityKeyAccessor {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var mSecurityKey: String

    fun getSecurityKey(): String {
        return mSecurityKey
    }

    fun getFilePath(): String {
        val file = System.getProperty("user.dir") + File.separator + Constant.SECURITY_KEY_FILE_NAME
        return file
    }

    /**
     * Check and create security key file
     * @return true if create a new security key file
     */
    fun checkAndCreateSecurityKey(): Boolean {
        val file = getFilePath()
        return if (!File(file).exists()) {
            val k = RandomStringUtils.randomAlphanumeric(16)
            FileUtils.write(File(file), k, Charsets.UTF_8)
            mSecurityKey = k
            logger.warn("A security key has been generated and saved to the $file file.")
            true
        } else {
            mSecurityKey = FileUtils.readFileToString(File(file), Charsets.UTF_8)
            false
        }
    }

}