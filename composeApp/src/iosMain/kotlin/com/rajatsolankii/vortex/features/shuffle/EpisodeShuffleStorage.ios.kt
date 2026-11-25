package com.rajatsolankii.vortex.features.shuffle

import com.rajatsolankii.vortex.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object EpisodeShuffleStorage {
    actual fun load(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of("episode_shuffle", profileId))

    actual fun save(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of("episode_shuffle", profileId))
    }
}
