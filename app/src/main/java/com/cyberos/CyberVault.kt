package com.cyberos

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class VaultPayload(val name: String, val mimeType: String, val bytes: ByteArray)

internal object CyberVaultCrypto {
    private const val KEY_ALIAS = "cyberos_vault_aes"
    private const val VAULT_DIR = ".cyber-vault"
    private const val MAX_ITEM_BYTES = 16 * 1024 * 1024
    private const val GCM_TAG_BITS = 128

    fun isBiometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticateShell(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (!isBiometricAvailable(activity)) {
            onFailure("No enrolled strong biometric is available.")
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
        })
        prompt.authenticate(promptInfo("Unlock CyberOS shell"))
    }

    fun saveWithBiometric(
        activity: FragmentActivity,
        payload: VaultPayload,
        onComplete: (String) -> Unit
    ) {
        runCatching { newCipher(Cipher.ENCRYPT_MODE) }
            .onFailure { onComplete("Vault key unavailable: ${it.message ?: "error"}") }
            .onSuccess { cipher ->
                authenticateCipher(activity, cipher, "Seal item in Cyber-Vault") { authorizedCipher ->
                    runCatching {
                        val packed = pack(payload)
                        require(packed.size <= MAX_ITEM_BYTES) { "Item exceeds the 16 MB vault limit." }
                        val ciphertext = authorizedCipher.doFinal(packed)
                        val destination = vaultDirectory(activity).resolve("${UUID.randomUUID()}.cvlt")
                        destination.writeBytes(authorizedCipher.iv + ciphertext)
                    }.onSuccess { onComplete("Encrypted item stored in .cyber-vault.") }
                        .onFailure { onComplete("Vault save failed: ${it.message ?: "error"}") }
                }
            }
    }

    fun openWithBiometric(activity: FragmentActivity, file: File, onComplete: (VaultPayload?, String) -> Unit) {
        runCatching {
            val sealed = file.readBytes()
            require(sealed.size > 12) { "Encrypted item is incomplete." }
            val cipher = newCipher(Cipher.DECRYPT_MODE, sealed.copyOfRange(0, 12))
            cipher to sealed.copyOfRange(12, sealed.size)
        }.onFailure { onComplete(null, "Vault item unavailable: ${it.message ?: "error"}") }
            .onSuccess { (cipher, ciphertext) ->
                authenticateCipher(activity, cipher, "Open encrypted Cyber-Vault item") { authorizedCipher ->
                    runCatching { unpack(authorizedCipher.doFinal(ciphertext)) }
                        .onSuccess { onComplete(it, "Vault item decrypted in memory.") }
                        .onFailure { onComplete(null, "Decryption failed: ${it.message ?: "error"}") }
                }
            }
    }

    fun entries(context: Context): List<File> = vaultDirectory(context).listFiles()
        ?.filter { it.isFile && it.extension == "cvlt" }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

    private fun authenticateCipher(activity: FragmentActivity, cipher: Cipher, title: String, onSuccess: (Cipher) -> Unit) {
        if (!isBiometricAvailable(activity)) return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorized = result.cryptoObject?.cipher
                if (authorized != null) onSuccess(authorized)
            }
        })
        prompt.authenticate(promptInfo(title), BiometricPrompt.CryptoObject(cipher))
    }

    private fun promptInfo(title: String): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("CyberOS biometric verification")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Cancel")
        .build()

    private fun newCipher(mode: Int, iv: ByteArray? = null): Cipher {
        val key = getOrCreateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (mode == Cipher.ENCRYPT_MODE) init(mode, key)
            else init(mode, key, GCMParameterSpec(GCM_TAG_BITS, requireNotNull(iv)))
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(spec.build())
        return generator.generateKey()
    }

    private fun vaultDirectory(context: Context): File = File(context.noBackupFilesDir, VAULT_DIR).apply { mkdirs() }

    private fun pack(payload: VaultPayload): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(payload.name.take(240))
            output.writeUTF(payload.mimeType.take(160))
            output.writeInt(payload.bytes.size)
            output.write(payload.bytes)
        }
        bytes.toByteArray()
    }

    private fun unpack(bytes: ByteArray): VaultPayload = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val name = input.readUTF()
        val mimeType = input.readUTF()
        val size = input.readInt()
        require(size in 0..MAX_ITEM_BYTES) { "Vault item has an invalid size." }
        val content = ByteArray(size)
        input.readFully(content)
        VaultPayload(name, mimeType, content)
    }
}

@Composable
internal fun CyberVaultPanel(theme: CyberTheme) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("BIOMETRIC AUTH REQUIRED") }
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<VaultPayload?>(null) }
    val entries = remember(refresh) { CyberVaultCrypto.entries(context) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && activity != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("File could not be opened.")
                require(bytes.size <= 16 * 1024 * 1024) { "Choose a file smaller than 16 MB." }
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "private-image"
                } ?: "private-image"
                Triple(name, context.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
            }.onSuccess { (name, mimeType, bytes) ->
                CyberVaultCrypto.saveWithBiometric(activity, VaultPayload(name, mimeType, bytes)) {
                    status = it
                    refresh++
                }
            }.onFailure { status = "Import failed: ${it.message ?: "error"}" }
        }
    }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("CYBER-VAULT // AES-256 GCM", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(status, color = Color(0xFFA8C5B3), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(8000) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            placeholder = { Text("private note", color = Color(0xFF71877A), fontSize = 10.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.accent,
                unfocusedBorderColor = theme.accent.copy(alpha = 0.35f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = theme.accent
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WindowTool("SEAL NOTE", theme) {
                if (activity == null) status = "Biometric host activity unavailable."
                else if (note.isBlank()) status = "Enter a note before sealing."
                else CyberVaultCrypto.saveWithBiometric(activity, VaultPayload("private-note.txt", "text/plain", note.toByteArray())) {
                    status = it
                    if (it.startsWith("Encrypted")) note = ""
                    refresh++
                }
            }
            WindowTool("SEAL IMAGE", theme) {
                if (!CyberVaultCrypto.isBiometricAvailable(context)) status = "Enroll a strong biometric in Android Settings first."
                else picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        Text("SEALED ITEMS // ${entries.size}", color = theme.accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        entries.take(5).forEachIndexed { index, file ->
            WindowTool("ITEM ${index + 1}  /  ${file.name.take(8)}  /  OPEN", theme) {
                if (activity == null) status = "Biometric host activity unavailable."
                else CyberVaultCrypto.openWithBiometric(activity, file) { payload, result ->
                    status = result
                    selected = payload
                }
            }
        }
        selected?.let { payload ->
            Text("${payload.name} // ${payload.mimeType} // ${payload.bytes.size} B", color = Color.White, fontSize = 8.sp)
            if (payload.mimeType.startsWith("text/")) Text(payload.bytes.decodeToString().take(360), color = Color(0xFFB5C9BC), fontSize = 8.sp, maxLines = 5)
        }
    }
}

@Composable
internal fun BiometricGate(theme: CyberTheme, onAuthenticate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF07100D)).padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text("CYBEROS / BIOMETRIC GATE", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))
        Text("Authenticate with an enrolled strong biometric to enter the shell.", color = Color(0xFFA8C5B3), fontSize = 10.sp)
        Spacer(Modifier.height(18.dp))
        WindowTool("AUTHENTICATE", theme, onAuthenticate)
    }
}