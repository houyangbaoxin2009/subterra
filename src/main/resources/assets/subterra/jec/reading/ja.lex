// ja.lex: Japanese kana -> romaji reading pack (hand-authored for Subterra).
// PinIn line format: <char>: r1, r2 (tone digits stripped; lowercased on load).
// Covers seion (clean syllables), dakuon/handakuon (voiced), youon-building
// small kana, sokuon (small tsu) and the full katakana mirror. Youon digraphs
// (e.g. きゃ) are NOT single entries: the engine consumes one character per
// reading, so き(ki) + ゃ(ya) composes to "kya" via name-search typing.
// Hiragana
あ: a
い: i
う: u
え: e
お: o
か: ka
き: ki
く: ku
け: ke
こ: ko
さ: sa
し: shi
す: su
せ: se
そ: so
た: ta
ち: chi
つ: tsu
て: te
と: to
な: na
に: ni
ぬ: nu
ね: ne
の: no
は: ha
ひ: hi
ふ: fu
へ: he
ほ: ho
ま: ma
み: mi
む: mu
め: me
も: mo
や: ya
ゆ: yu
よ: yo
ら: ra
り: ri
る: ru
れ: re
ろ: ro
わ: wa
を: o
ん: n
が: ga
ぎ: gi
ぐ: gu
げ: ge
ご: go
ざ: za
じ: ji
ず: zu
ぜ: ze
ぞ: zo
だ: da
ぢ: ji
づ: zu
で: de
ど: do
ば: ba
び: bi
ぶ: bu
べ: be
ぼ: bo
ぱ: pa
ぴ: pi
ぷ: pu
ぺ: pe
ぽ: po
// Small kana (youon/sokuon builders)
ゃ: ya
ゅ: yu
ょ: yo
ぁ: a
ぃ: i
ぅ: u
ぇ: e
ぉ: o
っ: tu
// Katakana
ア: a
イ: i
ウ: u
エ: e
オ: o
カ: ka
キ: ki
ク: ku
ケ: ke
コ: ko
サ: sa
シ: shi
ス: su
セ: se
ソ: so
タ: ta
チ: chi
ツ: tsu
テ: te
ト: to
ナ: na
ニ: ni
ヌ: nu
ネ: ne
ノ: no
ハ: ha
ヒ: hi
フ: fu
ヘ: he
ホ: ho
マ: ma
ミ: mi
ム: mu
メ: me
モ: mo
ヤ: ya
ユ: yu
ヨ: yo
ラ: ra
リ: ri
ル: ru
レ: re
ロ: ro
ワ: wa
ヲ: o
ン: n
ガ: ga
ギ: gi
グ: gu
ゲ: ge
ゴ: go
ザ: za
ジ: ji
ズ: zu
ゼ: ze
ゾ: zo
ダ: da
ヂ: ji
ヅ: zu
デ: de
ド: do
バ: ba
ビ: bi
ブ: bu
ベ: be
ボ: bo
パ: pa
ピ: pi
プ: pu
ペ: pe
ポ: po
// Small katakana (youon/sokuon builders)
ャ: ya
ュ: yu
ョ: yo
ァ: a
ィ: i
ゥ: u
ェ: e
ォ: o
ッ: tu
// ---------------------- Kanji (curated common on'yomi + a few kun'yomi) -----
// Single-character kanji readings so pinyin-style typed romaji finds Japanese
// item names too. On'yomi first; kun'yomi (native reading) appended after.
山: san, yama
川: sen, kawa
水: sui, mizu
火: hi, ka
木: moku, ki
土: do, tsuchi
金: kin, kane
石: seki, ishi
田: den, ta
力: riki, chikara
口: kou, kuchi
目: moku, me
耳: ji, mimi
手: shu, te
足: soku, ashi
心: shin, kokoro
人: jin, hito
日: nichi, hi
月: getsu, tsuki
星: sei, hoshi
天: ten, ama
地: chi, ji
空: kuu, sora
海: kai, umi
雲: un, kumo
雨: u, ame
雪: setsu, yuki
風: fuu, kaze
雷: rai, kaminari
森: shin, mori
林: rin, hayashi
草: sou, kusa
花: ka, hana
竹: chiku, take
米: bei, kome
麦: baku, mugi
豆: tou, mame
牛: gyuu, ushi
馬: ba, uma
犬: ken, inu
猫: byou, neko
鳥: chou, tori
魚: gyo, sakana
虫: chuu, mushi
羊: you, hitsuji
豚: ton, buta
門: mon, kado
車: sha, kuruma
刀: tou, katana
剣: ken, tsurugi
弓: kyuu, yumi
矢: ya
槍: sou, yari
鎧: gai, yoroi
盾: jun, tate
鉄: tetsu, kurogane
鋼: kou, hagane
銀: gin, shirogane
銅: dou, akagane
玉: gyoku, tama
宝: hou, takara