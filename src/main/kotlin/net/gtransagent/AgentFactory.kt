package net.gtransagent

import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.Constant
import net.gtransagent.internal.InternalConfig
import net.gtransagent.translator.base.ITranslator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import kotlin.system.exitProcess


object AgentFactory {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val options = DumperOptions()
    val yaml = Yaml(options)

    var configPath: String? = null

    lateinit var internalConfig: InternalConfig
    lateinit var publicConfig: PublicConfig

    // Map<engineCode, TranslateEngine>
    private val translateEngineMap: MutableMap<String, PublicConfig.TranslateEngine> = mutableMapOf()

    // Map<engineCode, ITranslator>
    private val translatorWithEngineKeyMap: MutableMap<String, ITranslator> = mutableMapOf()

    // Map<engineCode, translatorCode>
    private val engineCodeAndTranslatorCodeMap: MutableMap<String, String> = mutableMapOf()

    fun getTranslateEngine(engineCode: String): PublicConfig.TranslateEngine? {
        return translateEngineMap[engineCode]
    }

    fun getAllTranslateEngine(): List<PublicConfig.TranslateEngine> {
        return translateEngineMap.map {
            it.value
        }
    }

    fun getTranslatorByEngineKey(engineCode: String): ITranslator? {
        return translatorWithEngineKeyMap[engineCode]
    }

    /**
     * Get the configuration files lookup directorys.
     */
    fun getConfigFilesLookupDir(): List<String> {
        val classPath = if (javaClass.getResource("/")?.path == null) {
            File(this::class.java.getProtectionDomain().codeSource.location.path).canonicalPath
        } else {
            File(javaClass.getResource("/")!!.path).canonicalPath
        }
        logger.debug("getConfigFilesLookupDir classPath: $classPath")

        val sortedPaths = mutableListOf<String>(
            Paths.get(classPath).parent.toString()
        )
        if (Paths.get(classPath).parent.parent != null) {
            sortedPaths.add(Paths.get(classPath).parent.parent.toString())
        }
        if (!sortedPaths.contains(System.getProperty("user.dir"))) {
            sortedPaths.add(System.getProperty("user.dir"))
        }
        return sortedPaths
    }

    fun isRunFromJar(): Boolean {
        val path = File(this::class.java.getProtectionDomain().codeSource.location.path).canonicalPath
        return path.endsWith(".jar")
    }

    fun lookupConfigExistPath(): String? {
        configPath = getConfigFilesLookupDir().firstOrNull { dir ->
            val file = File(dir, Constant.PUBLIC_CONFIG_FILE_NAME)
            file.exists()
        }
        return configPath
    }

    private fun getPublicConfigFilePath(): String {
        return Paths.get(
            (lookupConfigExistPath() ?: System.getProperty("user.dir")), Constant.PUBLIC_CONFIG_FILE_NAME
        ).toString()
    }

    @Throws(Exception::class)
    private fun initInternalConfig() {
        this::class.java.getResourceAsStream("/" + Constant.INTERNAL_CONFIG_FILE_NAME).use {
            try {
                internalConfig = yaml.loadAs(it, InternalConfig::class.java)
                logger.info("load internal config success, agentVersionName: ${internalConfig.agentVersionName}, agentVersionNumber: ${internalConfig.agentVersionNumber}")
                logger.warn("GTransAgent Version: v${internalConfig.agentVersionName}, BuildNumber: ${internalConfig.agentVersionNumber}")
            } catch (e: Exception) {
                logger.error("load internal config error: ${e.message}", e)
                exitProcess(1) // exit
            }
        }
    }

    @Throws(Exception::class)
    private fun initPublicConfig(inputStream: InputStream) {
        inputStream.use {
            try {/*                val constructor: Constructor = Constructor(PublicConfig::class.java, LoaderOptions())
                                val carDescription = TypeDescription(PublicConfig::class.java)
                                carDescription.addPropertyParameters("translators", PublicConfig.TranslatorDefine::class.java)
                                constructor.addTypeDescription(carDescription)
                                val yaml = Yaml(constructor)*/
                publicConfig = yaml.loadAs(it, PublicConfig::class.java)
                logger.info("load public config success")
            } catch (e: Exception) {
                logger.error("load public config error: ${e.message}, path:${getPublicConfigFilePath()}", e)
                throw e
            }
        }
    }

    @Throws(Exception::class)
    private fun initPublicConfig() {
        if (File(getPublicConfigFilePath()).exists()) {
            initPublicConfig(FileInputStream(getPublicConfigFilePath()))
        } else {
            this::class.java.getResourceAsStream("/" + Constant.EDITABLE_CONFIG_DIR + "/" + Constant.PUBLIC_CONFIG_FILE_NAME)
                .use {
                    if (it != null) {
                        initPublicConfig(it)
                    } else {
                        logger.error("public config file not found, path:${getPublicConfigFilePath()}")
                        throw Exception("public config file not found")
                    }
                }
        }
    }


    @Throws(Exception::class)
    private fun initTranslators() {
        publicConfig.enablesTranslators.forEach { translatorCode ->
            val translatorDefine = publicConfig.translatorDefines.firstOrNull { it.translatorCode == translatorCode }
                ?: throw Exception("translatorDefine not found: $translatorCode")
            val translator =
                Class.forName(translatorDefine.classFile).getDeclaredConstructor().newInstance() as ITranslator
            val configMap = loadTranslatorConfigMap(translatorCode)
            if (translator.init(configMap)) {
                // get all supported engines
                val supportEngines = translator.getSupportedEngines()
                // record valid engines
                val validEngines = mutableListOf<PublicConfig.TranslateEngine>()
                supportEngines.forEach supportEngines@{ engine ->
                    // check if engine is already registered
                    if (engineCodeAndTranslatorCodeMap.containsKey(engine.code)) {
                        val oldTranslatorCode = engineCodeAndTranslatorCodeMap[engine.code]
                        val oldEngine = translateEngineMap[engine.code]!!
                        logger.warn("The $oldTranslatorCode Translator has already registered the ${oldEngine.code} (${oldEngine.name}) engine, the $translatorCode Translator can't register the ${engine.code} (${engine.name}) engine again. ")
                        return@supportEngines
                    }
                    validEngines.add(engine)
                    translateEngineMap[engine.code] = engine
                    translatorWithEngineKeyMap[engine.code] = translator
                    engineCodeAndTranslatorCodeMap[engine.code] = translatorCode
                }
                logger.warn("The $translatorCode Translator has been initialized. Engines: ${validEngines.map { it.name }}")
            } else {
                logger.error("init translator $translatorCode failed")
                throw Exception("init translator $translatorCode failed")
            }
        }
    }

    private fun getTranslatorConfigFilePath(translatorCode: String): String {
        return Paths.get(
            System.getProperty("user.dir"), Constant.TRANSLATOR_CONFIG_DIR, "$translatorCode.yaml"
        ).toString()
    }

    @Throws(Exception::class)
    fun loadTranslatorConfigMap(translatorCode: String): Map<*, *> {
        if (File(getTranslatorConfigFilePath(translatorCode)).exists()) {
            return yaml.load(FileInputStream(getTranslatorConfigFilePath(translatorCode)))
        } else {
            this::class.java.getResourceAsStream("/" + Constant.EDITABLE_CONFIG_DIR + "/" + Constant.TRANSLATOR_CONFIG_DIR + "/" + "$translatorCode.yaml")
                .use {
                    if (it != null) {
                        return yaml.load(it)
                    } else {
                        logger.error(
                            "translator config file not found, path:${
                                getTranslatorConfigFilePath(
                                    translatorCode
                                )
                            }"
                        )
                        throw Exception(
                            "translator config file not found, path:${
                                getTranslatorConfigFilePath(
                                    translatorCode
                                )
                            }"
                        )
                    }
                }
        }
    }

    @Throws(Exception::class)
    fun init() {
        initInternalConfig()
        initPublicConfig()
        initTranslators()
    }

    fun destroy() {
        try {
            logger.info("destroy translators")
            translatorWithEngineKeyMap.forEach { (_, translator) ->
                translator.destroy()
            }
            translatorWithEngineKeyMap.clear()
        } catch (e: Exception) {
            logger.error("destroy error: ${e.message}", e)
        }
    }

}