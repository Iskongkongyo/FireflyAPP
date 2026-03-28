package com.fireflyapp.lite.core.pack

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.fireflyapp.lite.data.model.ProjectManifest
import com.fireflyapp.lite.data.model.TemplatePackArtifactCheck
import com.fireflyapp.lite.data.model.TemplatePackArtifactCheckStatus
import com.fireflyapp.lite.data.model.TemplatePackExecutionResult
import com.fireflyapp.lite.data.model.TemplatePackExecutionStatus
import com.fireflyapp.lite.data.model.TemplatePackWorkspace
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import java.util.zip.ZipFile

class TemplateApkSigner(
    private val manifestPatcher: BinaryManifestPatcher = BinaryManifestPatcher()
) {
    data class SigningInspection(
        val summary: String,
        val fingerprints: List<String>,
        val isReady: Boolean,
        val message: String
    ) {
        val fingerprintSummary: String
            get() = fingerprints.joinToString()
    }

    private data class SigningMaterial(
        val signerName: String,
        val privateKey: PrivateKey,
        val certificates: List<X509Certificate>,
        val createdBy: String,
        val logDescription: String
    )

    private data class PackageParsingCheck(
        val accepted: Boolean,
        val detail: String
    )

    fun inspectSigningConfiguration(
        projectDir: File,
        projectManifest: ProjectManifest
    ): SigningInspection {
        val signing = projectManifest.signing
        if (signing.mode != SIGNING_MODE_CUSTOM) {
            return runCatching {
                val (_, certificate) = ensureDefaultSigningKey()
                SigningInspection(
                    summary = DEFAULT_SIGNING_SUMMARY,
                    fingerprints = listOf(certificate.sha256Fingerprint()),
                    isReady = true,
                    message = "Local AndroidKeyStore signer is ready."
                )
            }.getOrElse { throwable ->
                SigningInspection(
                    summary = DEFAULT_SIGNING_SUMMARY,
                    fingerprints = emptyList(),
                    isReady = false,
                    message = throwable.message ?: "Unable to load the local AndroidKeyStore signer."
                )
            }
        }

        val alias = signing.keyAlias.trim()
        val keystorePath = signing.keystorePath.trim()
        if (keystorePath.isBlank()) {
            return SigningInspection(
                summary = CUSTOM_SIGNING_SUMMARY,
                fingerprints = emptyList(),
                isReady = false,
                message = "Custom keystore mode is enabled but no keystore file was selected."
            )
        }
        if (signing.storePassword.isEmpty()) {
            return SigningInspection(
                summary = customSigningSummary(alias),
                fingerprints = emptyList(),
                isReady = false,
                message = "Custom keystore store password is required before Pack can run."
            )
        }
        if (alias.isBlank()) {
            return SigningInspection(
                summary = CUSTOM_SIGNING_SUMMARY,
                fingerprints = emptyList(),
                isReady = false,
                message = "Custom keystore key alias is required before Pack can run."
            )
        }

        return runCatching {
            val signingMaterial = resolveSigningMaterial(
                projectDir = projectDir,
                projectManifest = projectManifest
            )
            SigningInspection(
                summary = customSigningSummary(alias),
                fingerprints = signingMaterial.certificates.map { it.sha256Fingerprint() }.distinct().sorted(),
                isReady = true,
                message = "Custom keystore signer is ready."
            )
        }.getOrElse { throwable ->
            SigningInspection(
                summary = customSigningSummary(alias),
                fingerprints = emptyList(),
                isReady = false,
                message = throwable.message ?: "Unable to inspect the custom keystore signer."
            )
        }
    }

    fun alignAndSign(
        context: Context,
        workspace: TemplatePackWorkspace,
        projectDir: File,
        projectManifest: ProjectManifest
    ): TemplatePackExecutionResult {
        val unsignedApkFile = File(workspace.unsignedApkPath)
        if (!unsignedApkFile.exists()) {
            return writeBlockedResult(workspace, "Unsigned APK is missing. Run Pack first.")
        }

        val alignedApkFile = File(workspace.alignedApkPath)
        val signedApkFile = File(workspace.signedApkPath)
        alignedApkFile.parentFile?.mkdirs()
        signedApkFile.parentFile?.mkdirs()

        return runCatching {
            prepareAlignedInput(unsignedApkFile, alignedApkFile)
            appendLog(workspace, "Prepared aligned APK input via copy fallback at ${alignedApkFile.absolutePath}")
            val signingMaterial = resolveSigningMaterial(
                projectDir = projectDir,
                projectManifest = projectManifest
            )
            appendLog(workspace, "Signing config: ${signingMaterial.logDescription}")
            signApk(
                inputApk = alignedApkFile,
                outputApk = signedApkFile,
                signingMaterial = signingMaterial
            )
            val artifactCheck = runArtifactSelfCheck(
                context = context,
                signedApkFile = signedApkFile,
                workspace = workspace
            )
            val message = when (artifactCheck.status) {
                TemplatePackArtifactCheckStatus.PASSED -> "Signed APK generated and passed artifact self-check."
                TemplatePackArtifactCheckStatus.WARNING -> "Signed APK generated with artifact self-check warnings."
                TemplatePackArtifactCheckStatus.FAILED -> error("Artifact self-check failed.")
            }
            appendLog(workspace, message)
            TemplatePackExecutionResult(
                status = TemplatePackExecutionStatus.SUCCEEDED,
                message = message,
                logPath = workspace.packLogPath,
                artifactPath = signedApkFile.absolutePath,
                artifactCheck = artifactCheck
            )
        }.getOrElse { throwable ->
            appendLog(workspace, "APK signing failed: ${throwable.message}")
            TemplatePackExecutionResult(
                status = TemplatePackExecutionStatus.FAILED,
                message = throwable.message ?: "APK signing failed.",
                logPath = workspace.packLogPath,
                artifactPath = signedApkFile.takeIf { it.exists() }?.absolutePath
            )
        }
    }

    private fun prepareAlignedInput(unsignedApkFile: File, alignedApkFile: File) {
        if (alignedApkFile.exists()) {
            alignedApkFile.delete()
        }
        unsignedApkFile.copyTo(alignedApkFile, overwrite = true)
    }

    private fun resolveSigningMaterial(
        projectDir: File,
        projectManifest: ProjectManifest
    ): SigningMaterial {
        val signing = projectManifest.signing
        if (signing.mode != SIGNING_MODE_CUSTOM) {
            val (privateKey, certificate) = ensureDefaultSigningKey()
            return SigningMaterial(
                signerName = DEFAULT_SIGNER_NAME,
                privateKey = privateKey,
                certificates = listOf(certificate),
                createdBy = DEFAULT_CREATED_BY,
                logDescription = "default AndroidKeyStore alias=$KEY_ALIAS"
            )
        }

        val keystorePath = signing.keystorePath.trim()
        require(keystorePath.isNotBlank()) { "Custom keystore mode is enabled but no keystore file was selected." }
        require(signing.storePassword.isNotEmpty()) { "Custom keystore store password is required." }
        val alias = signing.keyAlias.trim()
        require(alias.isNotBlank()) { "Custom keystore key alias is required." }
        val keyPassword = signing.keyPassword.ifEmpty { signing.storePassword }
        val keystoreFile = projectDir.resolve(keystorePath)
        require(keystoreFile.exists() && keystoreFile.isFile) {
            "Custom keystore file is missing: ${keystoreFile.absolutePath}"
        }

        val (keyStoreType, keyStore) = loadCustomKeyStore(
            keystoreFile = keystoreFile,
            storePassword = signing.storePassword
        )
        val privateKey = keyStore.getKey(alias, keyPassword.toCharArray()) as? PrivateKey
            ?: error(
                "Unable to load private key for alias '$alias'. Check the key alias and key password."
            )
        val certificates = keyStore.getCertificateChain(alias)
            ?.mapNotNull { it as? X509Certificate }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                keyStore.getCertificate(alias) as? X509Certificate
                    ?: error("Unable to load certificate chain for alias '$alias'.")
            )
        return SigningMaterial(
            signerName = alias,
            privateKey = privateKey,
            certificates = certificates,
            createdBy = CUSTOM_CREATED_BY,
            logDescription = "custom keystore type=$keyStoreType alias=$alias file=${keystoreFile.absolutePath}"
        )
    }

    private fun ensureDefaultSigningKey(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(2048, java.security.spec.RSAKeyGenParameterSpec.F4))
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=FireflyApp Local Packager"))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - ONE_DAY_MS))
                .setCertificateNotAfter(Date(now + CERT_VALIDITY_MS))
                .build()
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEY_STORE
            )
            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        }

        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("Unable to load signing private key")
        val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: error("Unable to load signing certificate")
        return privateKey to certificate
    }

    private fun loadCustomKeyStore(
        keystoreFile: File,
        storePassword: String
    ): Pair<String, KeyStore> {
        val extension = keystoreFile.extension.lowercase()
        val candidates = if (extension in setOf("p12", "pfx")) {
            listOf("PKCS12", "JKS")
        } else {
            listOf("JKS", "PKCS12")
        }
        val failures = mutableListOf<String>()
        candidates.forEach { keyStoreType ->
            runCatching {
                val keyStore = KeyStore.getInstance(keyStoreType)
                keystoreFile.inputStream().use { input ->
                    keyStore.load(input, storePassword.toCharArray())
                }
                keyStoreType to keyStore
            }.onSuccess { return it }
                .onFailure { throwable ->
                    failures += "$keyStoreType: ${throwable.message ?: "load failed"}"
                }
        }
        error(
            "Unable to open custom keystore. Supported types tried: ${candidates.joinToString()}. ${failures.joinToString(" | ")}"
        )
    }

    private fun signApk(
        inputApk: File,
        outputApk: File,
        signingMaterial: SigningMaterial
    ) {
        if (outputApk.exists()) {
            outputApk.delete()
        }
        val signerConfig = ApkSigner.SignerConfig.Builder(
            signingMaterial.signerName,
            signingMaterial.privateKey,
            signingMaterial.certificates
        ).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setMinSdkVersion(MIN_SDK)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .setCreatedBy(signingMaterial.createdBy)
            .build()
            .sign()
    }

    private fun runArtifactSelfCheck(
        context: Context,
        signedApkFile: File,
        workspace: TemplatePackWorkspace
    ): TemplatePackArtifactCheck {
        appendLog(workspace, "Artifact self-check started.")
        val signatureCheck = verifySignedApk(signedApkFile, workspace)
        val manifestCheck = verifyBinaryManifestStructure(signedApkFile, workspace)
        val packageParsingCheck = verifyPackageParsing(context, signedApkFile, workspace)
        val status = if (packageParsingCheck.accepted) {
            TemplatePackArtifactCheckStatus.PASSED
        } else {
            TemplatePackArtifactCheckStatus.WARNING
        }
        appendLog(
            workspace,
            "Artifact self-check completed: status=${status.name.lowercase()} manifest='${manifestCheck}' signature='${signatureCheck}' packageParser='${packageParsingCheck.detail}'"
        )
        return TemplatePackArtifactCheck(
            status = status,
            manifestCheck = manifestCheck,
            signatureCheck = signatureCheck,
            packageParserCheck = packageParsingCheck.detail
        )
    }

    private fun verifySignedApk(
        signedApkFile: File,
        workspace: TemplatePackWorkspace
    ): String {
        val result = ApkVerifier.Builder(signedApkFile)
            .setMinCheckedPlatformVersion(MIN_SDK)
            .build()
            .verify()

        result.warnings
            .take(5)
            .forEach { warning ->
                appendLog(workspace, "APK verify warning: $warning")
            }

        if (!result.isVerified) {
            val errorMessage = result.errors
                .take(5)
                .joinToString(separator = " | ") { issue -> issue.toString() }
                .ifBlank { "Unknown APK verification failure." }
            appendLog(workspace, "APK verify failed: $errorMessage")
            error("Signed APK verification failed: $errorMessage")
        }

        return if (result.warnings.isEmpty()) {
            "ApkVerifier accepted the signed APK."
        } else {
            "ApkVerifier accepted the signed APK with warnings. See pack.log for details."
        }
    }

    private fun verifyBinaryManifestStructure(
        signedApkFile: File,
        workspace: TemplatePackWorkspace
    ): String {
        val manifestBytes = readBinaryManifestBytes(signedApkFile)
        val manifestCheck = manifestPatcher.validateStructure(manifestBytes)
        appendLog(workspace, "Binary manifest structure: ${manifestCheck.message}")
        require(manifestCheck.isValid) {
            "Artifact self-check failed: ${manifestCheck.message}"
        }
        return manifestCheck.message
    }

    private fun verifyPackageParsing(
        context: Context,
        signedApkFile: File,
        workspace: TemplatePackWorkspace
    ): PackageParsingCheck {
        val binaryFlagState = inspectBinaryManifestFlags(signedApkFile)
        appendLog(
            workspace,
            "Binary manifest flags: debuggable=${binaryFlagState.debuggable ?: "absent"} testOnly=${binaryFlagState.testOnly ?: "absent"}"
        )
        val packageInfo = loadPackageArchiveInfo(
            context = context,
            apkPath = signedApkFile.absolutePath,
            flags = PackageManager.GET_ACTIVITIES.toLong()
        ) ?: loadPackageArchiveInfo(
            context = context,
            apkPath = signedApkFile.absolutePath,
            flags = 0L
        )
        if (packageInfo == null) {
            val detail = "PackageManager rejected the signed APK archive on this device. Review pack.log before installing."
            appendLog(workspace, "Package parser warning: $detail")
            require(binaryFlagState.testOnly != true) {
                "Generated APK is marked testOnly in its binary manifest."
            }
            return PackageParsingCheck(
                accepted = false,
                detail = detail
            )
        }

        val applicationInfo = packageInfo.applicationInfo
        applicationInfo?.sourceDir = signedApkFile.absolutePath
        applicationInfo?.publicSourceDir = signedApkFile.absolutePath
        val isDebuggable = (applicationInfo?.flags ?: 0 and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isTestOnly = (applicationInfo?.flags ?: 0 and ApplicationInfo.FLAG_TEST_ONLY) != 0
        val detail = "PackageManager accepted the signed APK archive."
        appendLog(
            workspace,
            "Package parser accepted APK: package=${packageInfo.packageName} versionCode=${resolveVersionCode(packageInfo)} debuggable=$isDebuggable testOnly=$isTestOnly"
        )
        require(binaryFlagState.testOnly != true) {
            "Generated APK is marked testOnly in its binary manifest."
        }
        return PackageParsingCheck(
            accepted = true,
            detail = detail
        )
    }

    private fun loadPackageArchiveInfo(
        context: Context,
        apkPath: String,
        flags: Long
    ): PackageInfo? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.PackageInfoFlags.of(flags)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                apkPath,
                flags.toInt()
            )
        }
    }

    private fun inspectBinaryManifestFlags(apkFile: File): BinaryManifestPatcher.ManifestFlagState {
        val manifestBytes = readBinaryManifestBytes(apkFile)
        val tempManifest = kotlin.io.path.createTempFile(prefix = "firefly-pack-", suffix = ".bin").toFile()
        try {
            tempManifest.writeBytes(manifestBytes)
            return manifestPatcher.inspectFlags(tempManifest)
        } finally {
            tempManifest.delete()
        }
    }

    private fun readBinaryManifestBytes(apkFile: File): ByteArray {
        ZipFile(apkFile).use { zipFile ->
            val manifestEntry = zipFile.getEntry(ANDROID_MANIFEST_ENTRY)
                ?: error("AndroidManifest.xml is missing from the signed APK.")
            zipFile.getInputStream(manifestEntry).use { input ->
                return input.readBytes()
            }
        }
    }

    private fun resolveVersionCode(packageInfo: PackageInfo): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun writeBlockedResult(
        workspace: TemplatePackWorkspace,
        message: String
    ): TemplatePackExecutionResult {
        appendLog(workspace, message)
        return TemplatePackExecutionResult(
            status = TemplatePackExecutionStatus.BLOCKED,
            message = message,
            logPath = workspace.packLogPath
        )
    }

    private fun appendLog(workspace: TemplatePackWorkspace, message: String) {
        val logFile = File(workspace.packLogPath)
        logFile.parentFile?.mkdirs()
        logFile.appendText("[${TemplatePackLog.timestamp()}] $message\n", Charsets.UTF_8)
    }

    private fun X509Certificate.sha256Fingerprint(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString(separator = ":") { byte -> "%02X".format(byte) }
    }

    private fun customSigningSummary(alias: String): String {
        return if (alias.isBlank()) CUSTOM_SIGNING_SUMMARY else "Custom keystore ($alias)"
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "firefly_local_pack_signing_key"
        const val SIGNING_MODE_CUSTOM = "custom"
        const val DEFAULT_SIGNER_NAME = "firefly-local"
        const val DEFAULT_SIGNING_SUMMARY = "Firefly local AndroidKeyStore"
        const val CUSTOM_SIGNING_SUMMARY = "Custom keystore"
        const val DEFAULT_CREATED_BY = "FireflyApp Local Packager"
        const val CUSTOM_CREATED_BY = "FireflyApp Custom Packager"
        const val MIN_SDK = 24
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        const val CERT_VALIDITY_MS = 30L * 365L * ONE_DAY_MS
        const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"
    }
}
