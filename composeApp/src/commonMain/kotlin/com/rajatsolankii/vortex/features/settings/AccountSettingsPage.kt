package com.rajatsolankii.vortex.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajatsolankii.vortex.core.auth.AuthRepository
import com.rajatsolankii.vortex.core.auth.AuthState
import com.rajatsolankii.vortex.core.build.AppFeaturePolicy
import com.rajatsolankii.vortex.core.ui.NuvioPrimaryButton
import com.rajatsolankii.vortex.core.ui.NuvioStatusModal
import com.rajatsolankii.vortex.core.ui.NuvioSurfaceCard
import com.rajatsolankii.vortex.core.ui.NuvioTokens
import com.rajatsolankii.vortex.core.ui.nuvio
import kotlinx.coroutines.launch
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.action_cancel
import com.rajatsolankii.vortex.generated.resources.compose_settings_page_account
import com.rajatsolankii.vortex.generated.resources.auth_account_deletion_failed
import com.rajatsolankii.vortex.generated.resources.settings_account_delete_account
import com.rajatsolankii.vortex.generated.resources.settings_account_delete_account_description
import com.rajatsolankii.vortex.generated.resources.settings_account_delete_confirm_message
import com.rajatsolankii.vortex.generated.resources.settings_account_delete_confirm_title
import com.rajatsolankii.vortex.generated.resources.settings_account_email
import com.rajatsolankii.vortex.generated.resources.settings_account_reset_profiles
import com.rajatsolankii.vortex.generated.resources.settings_account_reset_profiles_message
import com.rajatsolankii.vortex.generated.resources.settings_account_reset_profiles_title
import com.rajatsolankii.vortex.generated.resources.settings_account_sign_out
import com.rajatsolankii.vortex.generated.resources.settings_account_sign_out_confirm_message
import com.rajatsolankii.vortex.generated.resources.settings_account_sign_out_confirm_title
import com.rajatsolankii.vortex.generated.resources.settings_account_status
import com.rajatsolankii.vortex.generated.resources.settings_account_status_local
import com.rajatsolankii.vortex.generated.resources.settings_account_status_signed_in
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
) {
    item {
        AccountSettingsBody(isTablet = isTablet)
    }
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val remoteSignedInState = (authState as? AuthState.Authenticated)?.takeUnless { it.isAnonymous }
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && remoteSignedInState != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (remoteSignedInState != null) {
                AccountInfoRow(
                    label = stringResource(Res.string.settings_account_status),
                    value = stringResource(Res.string.settings_account_status_signed_in),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                remoteSignedInState.email?.let { email ->
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_email),
                        value = email,
                    )
                }
            } else {
                AccountInfoRow(
                    label = stringResource(Res.string.settings_account_status),
                    value = stringResource(Res.string.settings_account_status_local),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        NuvioPrimaryButton(
            text = if (remoteSignedInState != null) {
                stringResource(Res.string.settings_account_sign_out)
            } else {
                stringResource(Res.string.settings_account_reset_profiles)
            },
            onClick = { showSignOutConfirm = true },
        )

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    NuvioStatusModal(
        title = if (remoteSignedInState != null) {
            stringResource(Res.string.settings_account_sign_out_confirm_title)
        } else {
            stringResource(Res.string.settings_account_reset_profiles_title)
        },
        message = if (remoteSignedInState != null) {
            stringResource(Res.string.settings_account_sign_out_confirm_message)
        } else {
            stringResource(Res.string.settings_account_reset_profiles_message)
        },
        isVisible = showSignOutConfirm,
        confirmText = if (remoteSignedInState != null) {
            stringResource(Res.string.settings_account_sign_out)
        } else {
            stringResource(Res.string.settings_account_reset_profiles)
        },
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showSignOutConfirm = false
            scope.launch { AuthRepository.signOut() }
        },
        onDismiss = { showSignOutConfirm = false },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        destructive = true,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
