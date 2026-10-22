package com.mahirex.app

import java.util.Locale

/**
 * Chip identifier -> market name ("MT6769V/CT" -> "MediaTek Helio G80", "SM8550" -> "Snapdragon 8 Gen 2").
 * Lookup order: exact string (variant codes, platform code names) -> base chip code -> vendor patterns.
 * When one code is shared by several market names, all of them are written ("Helio G70 / G80 / G85") - never a guess.
 * A chip that is not listed is reported as unknown by the caller; nothing is invented.
 */
object SocNames {

    // ---- exact strings: variant codes and platform code names (upper case) ----
    private val exact: Map<String, String> = mapOf(
        // MediaTek variants
        "MT6769V/CT" to "MediaTek Helio G80",
        "MT6769V/CU" to "MediaTek Helio G80",
        "MT6769V/CZ" to "MediaTek Helio G85",
        "MT6785T" to "MediaTek Helio G90T",
        "MT6785V/CD" to "MediaTek Helio G95",
        "MT6789V/CD" to "MediaTek Helio G99",
        "MT6765V/CB" to "MediaTek Helio P35",
        "MT6983Z" to "MediaTek Dimensity 9000+",
        // Google Tensor
        "GS101" to "Google Tensor",
        "GS201" to "Google Tensor G2",
        "ZUMA" to "Google Tensor G3",
        "ZUMAPRO" to "Google Tensor G4",
        // Qualcomm platform code names (ro.board.platform)
        "SUN" to "Qualcomm Snapdragon 8 Elite",
        "PINEAPPLE" to "Qualcomm Snapdragon 8 Gen 3",
        "KALAMA" to "Qualcomm Snapdragon 8 Gen 2",
        "KAILUA" to "Qualcomm Snapdragon 8 Gen 2",
        "CAPE" to "Qualcomm Snapdragon 8+ Gen 1",
        "TARO" to "Qualcomm Snapdragon 8 Gen 1",
        "WAIPIO" to "Qualcomm Snapdragon 8 Gen 1",
        "LAHAINA" to "Qualcomm Snapdragon 888 / 888+",
        "KONA" to "Qualcomm Snapdragon 865 / 865+ / 870",
        "MSMNILE" to "Qualcomm Snapdragon 855 / 855+ / 860",
        "SDM845" to "Qualcomm Snapdragon 845",
        "LITO" to "Qualcomm Snapdragon 765 / 765G / 768G",
        "ATOLL" to "Qualcomm Snapdragon 720G",
        "TALOS" to "Qualcomm Snapdragon 675",
        "TRINKET" to "Qualcomm Snapdragon 665",
        "BENGAL" to "Qualcomm Snapdragon 662 / 460",
        "HOLI" to "Qualcomm Snapdragon 480 5G",
        "BLAIR" to "Qualcomm Snapdragon 680 4G",
        "KHAJE" to "Qualcomm Snapdragon 695 5G"
    )

    // ---- base chip code (letters + digits, variant suffix removed) ----
    private val code: Map<String, String> = mapOf(
        // ===== MediaTek Helio / older =====
        "MT6795" to "MediaTek Helio X10",
        "MT6797" to "MediaTek Helio X20 / X25 / X27",
        "MT6755" to "MediaTek Helio P10 / P15 / P18",
        "MT6757" to "MediaTek Helio P20 / P25",
        "MT6758" to "MediaTek Helio P30",
        "MT6763" to "MediaTek Helio P23",
        "MT6761" to "MediaTek Helio A22",
        "MT6762" to "MediaTek Helio P22 / A25 / G25",
        "MT6765" to "MediaTek Helio P35 / G35 / G37",
        "MT6768" to "MediaTek Helio G70 / G80 / G85 family",
        "MT6769" to "MediaTek Helio G70 / G80 / G85 / G88 family",
        "MT6771" to "MediaTek Helio P60 / P70",
        "MT6779" to "MediaTek Helio P90",
        "MT6781" to "MediaTek Helio G96",
        "MT6785" to "MediaTek Helio G90 / G90T / G95",
        "MT6789" to "MediaTek Helio G99",
        // ===== MediaTek Dimensity =====
        "MT6833" to "MediaTek Dimensity 700",
        "MT6853" to "MediaTek Dimensity 720 / 800U",
        "MT6855" to "MediaTek Dimensity 930",
        "MT6873" to "MediaTek Dimensity 800",
        "MT6875" to "MediaTek Dimensity 820",
        "MT6877" to "MediaTek Dimensity 900 / 920 / 1080 family",
        "MT6878" to "MediaTek Dimensity 7300",
        "MT6886" to "MediaTek Dimensity 7200",
        "MT6891" to "MediaTek Dimensity 1100",
        "MT6893" to "MediaTek Dimensity 1200 / 1300",
        "MT6895" to "MediaTek Dimensity 8000 / 8100",
        "MT6896" to "MediaTek Dimensity 8200",
        "MT6897" to "MediaTek Dimensity 8300",
        "MT6899" to "MediaTek Dimensity 8400",
        "MT6983" to "MediaTek Dimensity 9000 / 9000+",
        "MT6985" to "MediaTek Dimensity 9200 / 9200+",
        "MT6989" to "MediaTek Dimensity 9300 / 9300+",
        "MT6991" to "MediaTek Dimensity 9400",
        "MT6993" to "MediaTek Dimensity 9500",

        // ===== Qualcomm Snapdragon (modern SM codes) =====
        "SM8850" to "Qualcomm Snapdragon 8 Elite Gen 5",
        "SM8750" to "Qualcomm Snapdragon 8 Elite",
        "SM8735" to "Qualcomm Snapdragon 8s Gen 4",
        "SM8650" to "Qualcomm Snapdragon 8 Gen 3",
        "SM8635" to "Qualcomm Snapdragon 8s Gen 3",
        "SM8550" to "Qualcomm Snapdragon 8 Gen 2",
        "SM8475" to "Qualcomm Snapdragon 8+ Gen 1",
        "SM8450" to "Qualcomm Snapdragon 8 Gen 1",
        "SM8350" to "Qualcomm Snapdragon 888 / 888+",
        "SM8250" to "Qualcomm Snapdragon 865 / 865+ / 870",
        "SM8150" to "Qualcomm Snapdragon 855 / 855+ / 860",
        "SM7675" to "Qualcomm Snapdragon 7+ Gen 3",
        "SM7550" to "Qualcomm Snapdragon 7 Gen 3",
        "SM7475" to "Qualcomm Snapdragon 7+ Gen 2",
        "SM7450" to "Qualcomm Snapdragon 7 Gen 1",
        "SM7435" to "Qualcomm Snapdragon 7s Gen 2",
        "SM7325" to "Qualcomm Snapdragon 778G / 778G+ / 782G",
        "SM7250" to "Qualcomm Snapdragon 765 / 765G / 768G",
        "SM7150" to "Qualcomm Snapdragon 730 / 730G",
        "SM7125" to "Qualcomm Snapdragon 720G",
        "SM6475" to "Qualcomm Snapdragon 6 Gen 3",
        "SM6450" to "Qualcomm Snapdragon 6 Gen 1",
        "SM6375" to "Qualcomm Snapdragon 695 5G",
        "SM6350" to "Qualcomm Snapdragon 690 5G",
        "SM6225" to "Qualcomm Snapdragon 680 4G",
        "SM6150" to "Qualcomm Snapdragon 675",
        "SM6125" to "Qualcomm Snapdragon 665",
        "SM6115" to "Qualcomm Snapdragon 662",
        "SM4450" to "Qualcomm Snapdragon 4 Gen 2",
        "SM4375" to "Qualcomm Snapdragon 4 Gen 1",
        "SM4350" to "Qualcomm Snapdragon 480 5G",
        "SM4250" to "Qualcomm Snapdragon 460",
        // ===== Qualcomm Snapdragon (SDM / MSM / APQ) =====
        "SDM845" to "Qualcomm Snapdragon 845",
        "SDM712" to "Qualcomm Snapdragon 712",
        "SDM710" to "Qualcomm Snapdragon 710",
        "SDM670" to "Qualcomm Snapdragon 670",
        "SDM660" to "Qualcomm Snapdragon 660",
        "SDM636" to "Qualcomm Snapdragon 636",
        "SDM632" to "Qualcomm Snapdragon 632",
        "SDM630" to "Qualcomm Snapdragon 630",
        "SDM450" to "Qualcomm Snapdragon 450",
        "SDM439" to "Qualcomm Snapdragon 439",
        "SDM429" to "Qualcomm Snapdragon 429",
        "MSM8998" to "Qualcomm Snapdragon 835",
        "MSM8996" to "Qualcomm Snapdragon 820 / 821",
        "MSM8994" to "Qualcomm Snapdragon 810",
        "MSM8992" to "Qualcomm Snapdragon 808",
        "APQ8084" to "Qualcomm Snapdragon 805",
        "MSM8974" to "Qualcomm Snapdragon 800 / 801",
        "MSM8976" to "Qualcomm Snapdragon 652 / 653",
        "MSM8956" to "Qualcomm Snapdragon 650 / 652",
        "MSM8953" to "Qualcomm Snapdragon 625 / 626",
        "MSM8952" to "Qualcomm Snapdragon 617",
        "MSM8940" to "Qualcomm Snapdragon 435",
        "MSM8939" to "Qualcomm Snapdragon 615 / 616",
        "MSM8937" to "Qualcomm Snapdragon 430",
        "MSM8929" to "Qualcomm Snapdragon 415",
        "MSM8917" to "Qualcomm Snapdragon 425",
        "MSM8916" to "Qualcomm Snapdragon 410 / 412",
        "MSM8909" to "Qualcomm Snapdragon 210 / 212",

        // ===== Samsung Exynos (internal S5E codes) =====
        "S5E3830" to "Samsung Exynos 850",
        "S5E8535" to "Samsung Exynos 1330",
        "S5E8825" to "Samsung Exynos 1280",
        "S5E8835" to "Samsung Exynos 1380",
        "S5E8845" to "Samsung Exynos 1480",
        "S5E8855" to "Samsung Exynos 1580",
        "S5E9610" to "Samsung Exynos 9610",
        "S5E9611" to "Samsung Exynos 9611",
        "S5E9815" to "Samsung Exynos 1080",
        "S5E9820" to "Samsung Exynos 9820",
        "S5E9830" to "Samsung Exynos 990",
        "S5E9840" to "Samsung Exynos 2100",
        "S5E9925" to "Samsung Exynos 2200",
        "S5E9945" to "Samsung Exynos 2400",
        "S5E9955" to "Samsung Exynos 2500",

        // ===== HiSilicon Kirin =====
        "HI3650" to "HiSilicon Kirin 950",
        "HI3660" to "HiSilicon Kirin 960",
        "HI3670" to "HiSilicon Kirin 970",
        "HI3680" to "HiSilicon Kirin 980",
        "HI3690" to "HiSilicon Kirin 990",
        "HI6250" to "HiSilicon Kirin 650 / 655",
        "HI6260" to "HiSilicon Kirin 710",

        // ===== Unisoc =====
        "SC9863" to "Unisoc SC9863A",
        "SC9832" to "Unisoc SC9832E",
        "SC9820" to "Unisoc SC9820E",
        "UMS312" to "Unisoc Tiger T310",
        "UMS512" to "Unisoc Tiger T610 / T618",
        "UMS9230" to "Unisoc T606 / T612 / T616 family",
        "UMS9620" to "Unisoc Tiger T820"
    )

    private val tokenRx = Regex("(MT\\d{4}|SM\\d{4}|SDM\\d{3}|MSM\\d{4}|APQ\\d{4}|S5E\\d{4}|HI\\d{4}|UMS\\d{3,4}|SC\\d{4})")
    private val mtVariantRx = Regex("MT\\d{4}[A-Z]*(/[A-Z]+)?")
    private val exynosRx = Regex("(?:EXYNOS|UNIVERSAL)\\s*-?\\s*(\\d{4})")
    private val kirinRx = Regex("KIRIN\\s*-?\\s*(\\d{3})")

    /** first candidate that can be turned into a market name, or null */
    fun find(candidates: List<String?>): String? {
        val cleaned = candidates.mapNotNull { it?.trim()?.uppercase(Locale.US) }
            .filter { it.isNotEmpty() && it != "UNKNOWN" }
        // 1) exact string / full MediaTek variant (most specific)
        for (c in cleaned) {
            exact[c]?.let { return it }
            mtVariantRx.find(c)?.value?.let { v -> exact[v]?.let { return it } }
        }
        // 2) base chip code
        for (c in cleaned) {
            val t = tokenRx.find(c)?.value ?: continue
            code[t]?.let { return it }
        }
        // 3) Exynos / Kirin by number
        for (c in cleaned) {
            exynosRx.find(c)?.let { return "Samsung Exynos " + it.groupValues[1] }
            kirinRx.find(c)?.let { return "HiSilicon Kirin " + it.groupValues[1] }
        }
        return null
    }
}
