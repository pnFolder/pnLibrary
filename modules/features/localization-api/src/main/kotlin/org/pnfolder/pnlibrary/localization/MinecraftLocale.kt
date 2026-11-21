package org.pnfolder.pnlibrary.localization

import java.util.Locale

/**
 * Standard Minecraft locale identifiers, so callers select a language by constant instead of a raw string.
 *
 * [code] is the identifier Mojang ships, for example `ru_ru`. Comparisons never rely on [Enum.ordinal], so
 * adding constants cannot change existing behaviour. Codes not represented here can still be requested as
 * plain strings through [TranslationRequest.Builder.locale].
 *
 * Kotlin:
 * ```kotlin
 * val request = TranslationRequest.builder()
 *     .version(MinecraftVersion.V1_20_4)
 *     .locale(MinecraftLocale.RU_RU)
 *     .fallback(MinecraftLocale.EN_US)
 *     .build()
 * ```
 * Java:
 * ```java
 * MinecraftLocale locale = MinecraftLocale.parse("ru-RU"); // RU_RU
 * ```
 *
 * @property code locale identifier shipped by Mojang, for example `ru_ru`.
 * @property englishName language name in English.
 * @property nativeName language name in its own language, when known; otherwise the English name.
 */
enum class MinecraftLocale(val code: String, val englishName: String, val nativeName: String) {
    AF_ZA("af_za", "Afrikaans", "Afrikaans"),
    AR_SA("ar_sa", "Arabic", "العربية"),
    AST_ES("ast_es", "Asturian", "Asturianu"),
    AZ_AZ("az_az", "Azerbaijani", "Azərbaycanca"),
    BA_RU("ba_ru", "Bashkir", "Башҡортса"),
    BAR("bar", "Bavarian", "Boarisch"),
    BE_BY("be_by", "Belarusian", "Беларуская"),
    BG_BG("bg_bg", "Bulgarian", "Български"),
    BR_FR("br_fr", "Breton", "Brezhoneg"),
    BRB("brb", "Brabantian", "Brabantian"),
    BS_BA("bs_ba", "Bosnian", "Bosanski"),
    CA_ES("ca_es", "Catalan", "Català"),
    CS_CZ("cs_cz", "Czech", "Čeština"),
    CY_GB("cy_gb", "Welsh", "Cymraeg"),
    DA_DK("da_dk", "Danish", "Dansk"),
    DE_AT("de_at", "German (Austria)", "Österreichisches Deutsch"),
    DE_CH("de_ch", "German (Switzerland)", "Schwiizerdütsch"),
    DE_DE("de_de", "German", "Deutsch"),
    EL_GR("el_gr", "Greek", "Ελληνικά"),
    EN_AU("en_au", "English (Australia)", "English (Australia)"),
    EN_CA("en_ca", "English (Canada)", "English (Canada)"),
    EN_GB("en_gb", "English (UK)", "English (UK)"),
    EN_NZ("en_nz", "English (New Zealand)", "English (New Zealand)"),
    EN_PT("en_pt", "Pirate Speak", "Pirate Speak"),
    EN_UD("en_ud", "English (Upside Down)", "English (Upside Down)"),
    EN_US("en_us", "English (US)", "English"),
    ENP("enp", "Anglish", "Anglish"),
    ENWS("enws", "Early Modern English", "Shakespearean English"),
    EO_UY("eo_uy", "Esperanto", "Esperanto"),
    ES_AR("es_ar", "Spanish (Argentina)", "Español (Argentina)"),
    ES_CL("es_cl", "Spanish (Chile)", "Español (Chile)"),
    ES_EC("es_ec", "Spanish (Ecuador)", "Español (Ecuador)"),
    ES_ES("es_es", "Spanish (Spain)", "Español (España)"),
    ES_MX("es_mx", "Spanish (Mexico)", "Español (México)"),
    ES_UY("es_uy", "Spanish (Uruguay)", "Español (Uruguay)"),
    ES_VE("es_ve", "Spanish (Venezuela)", "Español (Venezuela)"),
    ESAN("esan", "Andalusian", "Andaluz"),
    ET_EE("et_ee", "Estonian", "Eesti"),
    EU_ES("eu_es", "Basque", "Euskara"),
    FA_IR("fa_ir", "Persian", "فارسی"),
    FI_FI("fi_fi", "Finnish", "Suomi"),
    FIL_PH("fil_ph", "Filipino", "Filipino"),
    FO_FO("fo_fo", "Faroese", "Føroyskt"),
    FR_CA("fr_ca", "French (Canada)", "Français (Canada)"),
    FR_FR("fr_fr", "French", "Français"),
    FRA_DE("fra_de", "East Franconian", "Fränkisch"),
    FUR_IT("fur_it", "Friulian", "Furlan"),
    FY_NL("fy_nl", "Frisian", "Frysk"),
    GA_IE("ga_ie", "Irish", "Gaeilge"),
    GD_GB("gd_gb", "Scottish Gaelic", "Gàidhlig"),
    GL_ES("gl_es", "Galician", "Galego"),
    HAW_US("haw_us", "Hawaiian", "ʻŌlelo Hawaiʻi"),
    HE_IL("he_il", "Hebrew", "עברית"),
    HI_IN("hi_in", "Hindi", "हिन्दी"),
    HR_HR("hr_hr", "Croatian", "Hrvatski"),
    HU_HU("hu_hu", "Hungarian", "Magyar"),
    HY_AM("hy_am", "Armenian", "Հայերեն"),
    ID_ID("id_id", "Indonesian", "Bahasa Indonesia"),
    IG_NG("ig_ng", "Igbo", "Igbo"),
    IO_EN("io_en", "Ido", "Ido"),
    IS_IS("is_is", "Icelandic", "Íslenska"),
    ISV("isv", "Interslavic", "Medžuslovjansky"),
    IT_IT("it_it", "Italian", "Italiano"),
    JA_JP("ja_jp", "Japanese", "日本語"),
    JBO_EN("jbo_en", "Lojban", "la .lojban."),
    KA_GE("ka_ge", "Georgian", "ქართული"),
    KK_KZ("kk_kz", "Kazakh", "Қазақша"),
    KN_IN("kn_in", "Kannada", "ಕನ್ನಡ"),
    KO_KR("ko_kr", "Korean", "한국어"),
    KSH("ksh", "Kölsch", "Kölsch"),
    KW_GB("kw_gb", "Cornish", "Kernewek"),
    LA_LA("la_la", "Latin", "Latina"),
    LB_LU("lb_lu", "Luxembourgish", "Lëtzebuergesch"),
    LI_LI("li_li", "Limburgish", "Limburgs"),
    LMO("lmo", "Lombard", "Lombard"),
    LOL_US("lol_us", "LOLCAT", "LOLCAT"),
    LT_LT("lt_lt", "Lithuanian", "Lietuvių"),
    LV_LV("lv_lv", "Latvian", "Latviešu"),
    LZH("lzh", "Literary Chinese", "文言"),
    MK_MK("mk_mk", "Macedonian", "Македонски"),
    MN_MN("mn_mn", "Mongolian", "Монгол"),
    MS_MY("ms_my", "Malay", "Bahasa Melayu"),
    MT_MT("mt_mt", "Maltese", "Malti"),
    NAH("nah", "Nahuatl", "Nāhuatl"),
    NDS_DE("nds_de", "Low German", "Plattdüütsch"),
    NL_BE("nl_be", "Dutch (Belgium)", "Vlaams"),
    NL_NL("nl_nl", "Dutch", "Nederlands"),
    NN_NO("nn_no", "Norwegian Nynorsk", "Norsk Nynorsk"),
    NO_NO("no_no", "Norwegian Bokmål", "Norsk Bokmål"),
    OC_FR("oc_fr", "Occitan", "Occitan"),
    OVD("ovd", "Elfdalian", "Övdalska"),
    PL_PL("pl_pl", "Polish", "Polski"),
    PT_BR("pt_br", "Portuguese (Brazil)", "Português (Brasil)"),
    PT_PT("pt_pt", "Portuguese (Portugal)", "Português (Portugal)"),
    QYA_AA("qya_aa", "Quenya", "Quenya"),
    RO_RO("ro_ro", "Romanian", "Română"),
    RPR("rpr", "Russian (Pre-revolutionary)", "Russian (Pre-revolutionary)"),
    RU_RU("ru_ru", "Russian", "Русский"),
    RY_UA("ry_ua", "Rusyn", "Русиньскый"),
    SAH_SAH("sah_sah", "Yakut", "Сахалыы"),
    SE_NO("se_no", "Northern Sami", "Davvisámegiella"),
    SK_SK("sk_sk", "Slovak", "Slovenčina"),
    SL_SI("sl_si", "Slovenian", "Slovenščina"),
    SO_SO("so_so", "Somali", "Soomaali"),
    SQ_AL("sq_al", "Albanian", "Shqip"),
    SR_SP("sr_sp", "Serbian", "Српски"),
    SV_SE("sv_se", "Swedish", "Svenska"),
    SWG("swg", "Swabian", "Schwäbisch"),
    SXU("sxu", "Upper Saxon", "Sächsisch"),
    SZL("szl", "Silesian", "Ślōnski"),
    TA_IN("ta_in", "Tamil", "தமிழ்"),
    TH_TH("th_th", "Thai", "ไทย"),
    TL_PH("tl_ph", "Tagalog", "Tagalog"),
    TLH_AA("tlh_aa", "Klingon", "tlhIngan Hol"),
    TOK("tok", "Toki Pona", "toki pona"),
    TR_TR("tr_tr", "Turkish", "Türkçe"),
    TT_RU("tt_ru", "Tatar", "Татарча"),
    TZL_TZL("tzl_tzl", "Talossan", "Talossan"),
    UK_UA("uk_ua", "Ukrainian", "Українська"),
    VAL_ES("val_es", "Valencian", "Valencià"),
    VEC_IT("vec_it", "Venetian", "Vèneto"),
    VI_VN("vi_vn", "Vietnamese", "Tiếng Việt"),
    VP_VL("vp_vl", "Viossa", "Viossa"),
    YI_DE("yi_de", "Yiddish", "ייִדיש"),
    YO_NG("yo_ng", "Yoruba", "Yorùbá"),
    ZH_CN("zh_cn", "Chinese (Simplified)", "简体中文"),
    ZH_HK("zh_hk", "Chinese (Traditional, Hong Kong)", "繁體中文（香港）"),
    ZH_TW("zh_tw", "Chinese (Traditional, Taiwan)", "繁體中文（台灣）"),
    ZLM_ARAB("zlm_arab", "Malay (Jawi)", "بهاس ملايو"),
    ;

    /** Lookups by locale code. */
    companion object {
        private val byCode = entries.associateBy { it.code }

        /**
         * Returns the locale whose [code] matches [value] after normalization (trimmed, lowercased, `-` to `_`),
         * or `null` when no constant represents it. Unlike [org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion],
         * this never throws, because a locale string may legitimately be one not listed here.
         */
        @JvmStatic
        fun parse(value: String?): MinecraftLocale? {
            val normalized = value?.trim()?.lowercase(Locale.ROOT)?.replace('-', '_') ?: return null
            return byCode[normalized]
        }
    }
}
