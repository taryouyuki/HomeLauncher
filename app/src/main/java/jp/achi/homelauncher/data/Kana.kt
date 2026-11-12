package jp.achi.homelauncher.data

import java.text.Normalizer

/**
 * アプリ名検索の正規化。ひらがな・カタカナ・全角半角・大文字小文字の違いを吸収し、
 * よくある漢字の語は読み(ひらがな)でも一致させる。辞書にない漢字は、アプリ一覧の長押しで読み仮名を登録できる。
 */
object Kana {
    private val DICT: List<Pair<String, String>> = listOf(
        "電話" to "でんわ", "連絡先" to "れんらくさき", "設定" to "せってい", "時計" to "とけい",
        "写真" to "しゃしん", "天気" to "てんき", "地図" to "ちず", "計算機" to "けいさんき",
        "電卓" to "でんたく", "音楽" to "おんがく", "動画" to "どうが", "辞書" to "じしょ",
        "辞典" to "じてん", "翻訳" to "ほんやく", "買い物" to "かいもの", "買物" to "かいもの",
        "乗換案内" to "のりかえあんない", "乗り換え" to "のりかえ", "銀行" to "ぎんこう",
        "検索" to "けんさく", "録音" to "ろくおん", "録画" to "ろくが", "地震" to "じしん",
        "家計簿" to "かけいぼ", "通知" to "つうち", "壁紙" to "かべがみ", "文字" to "もじ",
        "漫画" to "まんが", "小説" to "しょうせつ", "将棋" to "しょうぎ", "囲碁" to "いご",
        "麻雀" to "まーじゃん", "予定" to "よてい", "日記" to "にっき", "手帳" to "てちょう",
        "目覚まし" to "めざまし", "歩数" to "ほすう", "健康" to "けんこう", "睡眠" to "すいみん",
        "電車" to "でんしゃ", "路線" to "ろせん", "時刻表" to "じこくひょう", "映画" to "えいが",
        "放送" to "ほうそう", "配信" to "はいしん", "番組" to "ばんぐみ", "画像" to "がぞう",
        "画面" to "がめん", "管理" to "かんり", "整理" to "せいり", "掃除" to "そうじ",
        "電子書籍" to "でんししょせき", "新聞" to "しんぶん", "料理" to "りょうり", "旅行" to "りょこう",
        "宿泊" to "しゅくはく", "航空" to "こうくう", "郵便" to "ゆうびん", "宅配" to "たくはい",
        "荷物" to "にもつ", "支払い" to "しはらい", "決済" to "けっさい", "保険" to "ほけん",
        "証券" to "しょうけん", "英語" to "えいご", "日本語" to "にほんご", "学習" to "がくしゅう",
        "勉強" to "べんきょう", "漢字" to "かんじ", "単語" to "たんご", "運動" to "うんどう",
        "体重" to "たいじゅう", "血圧" to "けつあつ", "病院" to "びょういん", "子供" to "こども",
        "育児" to "いくじ", "編集" to "へんしゅう", "加工" to "かこう", "撮影" to "さつえい",
        "再生" to "さいせい", "時間" to "じかん", "予報" to "よほう", "雨雲" to "あまぐも",
        "地下鉄" to "ちかてつ", "天気予報" to "てんきよほう", "電池" to "でんち", "容量" to "ようりょう",
        "文書" to "ぶんしょ", "表計算" to "ひょうけいさん", "資料" to "しりょう", "共有" to "きょうゆう",
        "保存" to "ほぞん", "拡大" to "かくだい", "公式" to "こうしき", "無料" to "むりょう",
        "招待" to "しょうたい", "会議" to "かいぎ", "仕事" to "しごと", "占い" to "うらない",
        "写経" to "しゃきょう", "紹介" to "しょうかい", "百科事典" to "ひゃっかじてん", "図鑑" to "ずかん",
    ).sortedByDescending { it.first.length }

    /** 比較用に正規化する(全角半角・大文字小文字・カタカナ→ひらがな)。 */
    fun normalize(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().trim()
        val sb = StringBuilder(n.length)
        for (c in n) {
            sb.append(if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c)
        }
        return sb.toString()
    }

    /** 辞書にある漢字語を読みに置き換えた、検索用の読み。 */
    fun reading(label: String): String {
        var t = label
        for ((k, v) in DICT) if (t.contains(k)) t = t.replace(k, v)
        return normalize(t)
    }

    /** アプリ名・辞書の読み・登録した読み仮名のいずれかが、検索語(部分一致)を含むか。 */
    fun matches(label: String, query: String, yomi: String? = null): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        if (normalize(label).contains(q)) return true
        if (reading(label).contains(q)) return true
        return yomi != null && normalize(yomi).contains(q)
    }
}
