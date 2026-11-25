package com.rajatsolankii.vortex.features.settings

import com.rajatsolankii.vortex.features.player.DeviceLanguagePreferences
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.lang_arabic
import com.rajatsolankii.vortex.generated.resources.lang_bengali
import com.rajatsolankii.vortex.generated.resources.lang_bulgarian
import com.rajatsolankii.vortex.generated.resources.lang_croatian
import com.rajatsolankii.vortex.generated.resources.lang_czech
import com.rajatsolankii.vortex.generated.resources.lang_dutch
import com.rajatsolankii.vortex.generated.resources.lang_english
import com.rajatsolankii.vortex.generated.resources.lang_french
import com.rajatsolankii.vortex.generated.resources.lang_german
import com.rajatsolankii.vortex.generated.resources.lang_greek
import com.rajatsolankii.vortex.generated.resources.lang_hebrew
import com.rajatsolankii.vortex.generated.resources.lang_hungarian
import com.rajatsolankii.vortex.generated.resources.lang_indonesian
import com.rajatsolankii.vortex.generated.resources.lang_italian
import com.rajatsolankii.vortex.generated.resources.lang_polish
import com.rajatsolankii.vortex.generated.resources.lang_portuguese_brazil
import com.rajatsolankii.vortex.generated.resources.lang_portuguese_portugal
import com.rajatsolankii.vortex.generated.resources.lang_romanian
import com.rajatsolankii.vortex.generated.resources.lang_russian
import com.rajatsolankii.vortex.generated.resources.lang_slovak
import com.rajatsolankii.vortex.generated.resources.lang_spanish
import com.rajatsolankii.vortex.generated.resources.lang_turkish
import com.rajatsolankii.vortex.generated.resources.lang_tamil
import com.rajatsolankii.vortex.generated.resources.lang_ukrainian
import com.rajatsolankii.vortex.generated.resources.lang_urdu
import com.rajatsolankii.vortex.generated.resources.lang_norwegian
import com.rajatsolankii.vortex.generated.resources.lang_japanese
import com.rajatsolankii.vortex.generated.resources.lang_vietnamese
import com.rajatsolankii.vortex.generated.resources.lang_chinese
import com.rajatsolankii.vortex.generated.resources.settings_appearance_app_language_device
import org.jetbrains.compose.resources.StringResource

enum class AppLanguage(
    val code: String,
    val labelRes: StringResource,
) {
    DEVICE("device", Res.string.settings_appearance_app_language_device),
    ARABIC("ar", Res.string.lang_arabic),
    BENGALI("bn", Res.string.lang_bengali),
    BULGARIAN("bg", Res.string.lang_bulgarian),
    CROATIAN("hr", Res.string.lang_croatian),
    CZECH("cs", Res.string.lang_czech),
    DUTCH("nl", Res.string.lang_dutch),
    ENGLISH("en", Res.string.lang_english),
    FRENCH("fr", Res.string.lang_french),
    GERMAN("de", Res.string.lang_german),
    GREEK("el", Res.string.lang_greek),
    HEBREW("he", Res.string.lang_hebrew),
    HUNGARIAN("hu", Res.string.lang_hungarian),
    INDONESIAN("id", Res.string.lang_indonesian),
    ITALIAN("it", Res.string.lang_italian),
    POLISH("pl", Res.string.lang_polish),
    PORTUGUESE_BRAZIL("pt-BR", Res.string.lang_portuguese_brazil),
    PORTUGUESE("pt", Res.string.lang_portuguese_portugal),
    ROMANIAN("ro", Res.string.lang_romanian),
    RUSSIAN("ru", Res.string.lang_russian),
    SLOVAK("sk", Res.string.lang_slovak),
    SPANISH("es", Res.string.lang_spanish),
    TAMIL("ta", Res.string.lang_tamil),
    TURKISH("tr", Res.string.lang_turkish),
    UKRAINIAN("uk", Res.string.lang_ukrainian),
    URDU("ur", Res.string.lang_urdu),
    NORWEGIAN("nb", Res.string.lang_norwegian),
    JAPANESE("ja", Res.string.lang_japanese),
    VIETNAMESE("vi", Res.string.lang_vietnamese),
    CHINESE("zh", Res.string.lang_chinese),
    ;

    fun isRightToLeft(): Boolean {
        val languageCode = if (this == DEVICE) DeviceLanguagePreferences.preferredLanguageCodes().firstOrNull() else code
        return languageCode?.substringBefore('-') in setOf(ARABIC.code, HEBREW.code, URDU.code)
    }

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEVICE
    }
}
