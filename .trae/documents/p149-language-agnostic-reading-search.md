# p.1.4.9 语言无关读音搜索（读音包）实现计划

## Context（背景）

Subterra 已移植 JEC 式**拼音**搜索（p.1.4.8）：引擎核心 `Lexicon`+`PronounceMatcher`（subterra-optim，纯 JDK，语言无关的"字符→读音"映射）与 MC 层接入（`PinyinSearch`/`PinyinSuffixArray`/`SearchTreeMixin`，subterra-jec 并入根源集）。但加载器、配置、命名、资源组织全部硬编码为"中文拼音"。

用户要求把 JEC 调整为**语言无关架构**，支持导入其他语言的读音包（例如日语罗马音）。已确认：
- bundled 读音包（jar 内置）**导入默认开启**，配置里可显式关闭/指定子集。
- td 顶层节名用 **`reading_search`**（替换 `pinyin_search`，p 阶段破坏性变更可接受）。

目标：多语言读音包并存（如"山"拼音 shan 与日文音读 san 共存、假名→罗马音可搜），引擎不作任何语言假设，数据即插即用。

## 设计决策

1. **读音包（PronunciationPack）**：每个包有 id（`zh`、`ja`），两类数据源——bundled 资源（`/assets/subterra/jec/reading/{id}.lex`，代码内显式注册表，不扫描 jar 目录）+ 用户扩展文件（config 目录，按 id 追加）。多包共享**一个** Lexicon（字符→读音 map；语言间字符集不重叠，同字符多读音**追加去重**共存）。
2. **合并语义**：包之间与扩展统一走**追加去重**（base 读音优先、other 追加、同读音去重、保序）——这样"山"的 shan+san 共存。新增引擎方法 `Lexicon.mergedWith(other)`；保留现有覆盖语义 `withExtension`（PronounceProbe 依赖，不动）。这是相对 p.1.4.8 的可观察行为变更（旧 `extension_lexicon` 是覆盖），CHANGELOG 显式声明。
3. **配置模型**（td，经 subterra-config；TdTable 支持无 key 数组元素，`TdValue.asList()` 可用）：

```td
[
  reading_search = [
    enabled = true,              // 总开关，默认 true；false 时 SearchTreeMixin 走原版路径
    packs = [ "zh", "ja" ],      // bundled 包子集；缺省或空列表 = 全部内置
    extensions = [               // 用户词典，按 id 追加到对应包
      [ id = "ja", lexicon = "config/subterra/ja_extra.lex" ],
    ],
  ],
]
```
4. **处理行为**：未知/缺失/损坏数据只 `LOGGER.warn` + 跳过，绝不崩溃；整个配置解析失败回退 `defaults()`；`enabled=false` 时 `ReadingSearch.enabled()` 返回 false，mixin 走 `SearchTree.plainText(...)` 原路径（语义不变，懒加载单例保留）。

## 文件改动清单

### A. subterra-optim（引擎，纯 JDK）
**`subterra-optim/src/main/java/io/toterra/subterra/optim/logic/pronounce/Lexicon.java`**
- 新增 `mergedWith(Lexicon other)`：追加去重合并，O(总读音数)，确定性，不可变。用 `LinkedHashMap<Character, LinkedHashSet<String>>` 先 base 后 other 收拢（保序去重），再 `List.copyOf` 包装为不可变。类 Javadoc 补充两种合并语义的区分。

### B. subterra-jec（MC 层，并入根源集）
1. **`subterra-jec/.../search/PinyinSearch.java` → `ReadingSearch.java`**
   - 类名改 `ReadingSearch`；`MOD_ID="subterra_jec"` 与日志前缀保留；`NAME="JEC-style reading search"`。
   - 内置包注册表：`record PackedLexicon(String id, String resourcePath)` + `BUNDLED = List.of(zh→/assets/subterra/jec/reading/zh.lex, ja→/assets/subterra/jec/reading/ja.lex)`。
   - `bootstrap(ModContainer)`：`ReadingSearchConfig.load` →（1）`enabled = config.enabled()`；（2）遍历 BUNDLED，若 `config.packs()` 为 null（默认全部）或含该 id → 加载资源 → `acc = acc.mergedWith(l)`，log `reading pack 'xx' loaded`；（3）遍历 `config.extensions()`，按 id 匹配已加载包，文件加载成功 → `acc = acc.mergedWith(ext)`，log；未知 id/损坏 warn 跳过；（4）`lexicon=acc; matcher=new PronounceMatcher(acc)`；收尾 log `reading search enabled/disabled (packs: zh,ja)`。
   - `matcher()` 懒加载单例、`enabled()`、`loadResource`/`loadExtensionFile`（原 loadBundled/loadExtension 改造，失败 warn + 空 Lexicon/跳过）。
2. **`subterra-jec/.../search/PinyinSearchConfig.java` → `ReadingSearchConfig.java`**
   - 字段：`boolean enabled`、`List<String> packs`（null=全部内置）、`List<ExtensionSpec> extensions`；`record ExtensionSpec(String id, Path lexiconPath)`。
   - `fromTd`：`reading_search` 顶层节 → `enabled`（asBool，默认 true）；`packs`（`asList()` 逐项 `asString()` 去空，空/缺省→null）；`extensions`（`asList()` 每项为 TdTable，读 `id`/`lexicon`，相对路径 `gameDir.resolve`）。解析异常→`defaults()`。
   - Javadoc 与 `toString()` 更新为 `reading_search` 结构。
3. **`subterra-jec/.../search/PinyinSuffixArray.java` → `ReadingSuffixArray.java`**
   - 类名与内部 `PinyinSearch.matcher()` 引用改 `ReadingSearch.matcher()`；线性扫描逻辑不动。
4. **`subterra-jec/.../search/mixin/SearchTreeMixin.java`**（文件名不变）
   - import 与引用改 `ReadingSearch`/`ReadingSuffixArray`；handler 名 `subterra_jec$usePinyinSuffixArray` → `subterra_jec$useReadingSuffixArray`；`enabled=false` 走原版路径的语义不变。
5. **资源**
   - `git mv src/main/resources/assets/subterra/jec/pinyin.lex → src/main/resources/assets/subterra/jec/reading/zh.lex`（26k 行拼音词典，内容不变）。
   - 新增 `src/main/resources/assets/subterra/jec/reading/ja.lex`：手写日语假名→罗马音，PinIn 行格式 `字符: 罗马音`，`//` 注释头。覆盖：清音五十音（あいうえお…わをん）、浊音（がぎぐ…ばびぶ）、半浊音（ぱぴぷぺぽ）、小写假名（ゃゅょ・ぁぃぅぇぉ）、促音（っ: tu）、片假名全量镜像（ア…ヲ・ン・ャ…）。约 100–170 行。**注意**：拗音节（きゃ）不写单字复合读音——引擎逐字消费，`きゃ`=き(ki 首字母 k)+ゃ(ya) 得 `kya`，探针覆盖该路径。
6. **`src/main/java/io/toterra/subterra/SubterraClient.java`**
   - `PinyinSearch.bootstrap(container)` → `ReadingSearch.bootstrap(container)`；注释改"language-agnostic reading packs"。
7. **`src/main/templates/META-INF/neoforge.mods.toml`**：仅注释措辞更新（JEC-style reading search），mixin 配置条目不变。

### C. subterra-probes（确定性验证）
1. **`subterra-probes/.../PinyinSearchProbe.java` → `ReadingSearchProbe.java`**
   - 内嵌词典拆为 `zh`、`ja` 两个 Lexicon，`zh.mergedWith(ja)` 得到共享 matcher。
   - **保留全部 16 个拼音断言**；新增日语/多包用例（总断言 20+）：
     - `ja full romaji kana`（かな→kana）、`ja single kana`（あめ→ame）、`ja simple syllable`（き→ki）
     - `ja youon digraph`（きゃ→kya，组合路径）、`ja romanji initials`（きゅう→ky）
     - `ja negative`（!かな→kuni）、`katakana match`（カナ→kana）
     - `multipack co-exist append`（山→shan 且 山→san，追加语义关键断言）
     - `zh unaffected after ja merge`（铁锭→tieding）
   - PASS 文案改 `[ReadingSearchProbe] PASS (...)`。
2. **`subterra-probes/build.gradle`**
   - `runPinyinSearchProbe` → `runReadingSearchProbe`（mainClass=ReadingSearchProbe），`probeAcceptance` dependsOn 同步替换。

### D. 版本与文档
- **`gradle.properties`**：`mod_version` p.1.4.8 → p.1.4.9。
- **`CHANGELOG.md`**：在 p.1.4.8 条目上方插入双语条目（沿现有单行双语风格）：语言无关化（类/配置/资源重命名）、`mergedWith` 追加去重语义（含 山=shan+san 共存、旧 extension_lexicon 覆盖→追加的行为变更声明）、新增 ja.lex 日语包、探针 20+ 断言。

### 引用核查
全仓 `rg` 确认无其他 `PinyinSearch/PinyinSuffixArray/PinyinSearchConfig/PinyinSearchProbe/pinyin_search/extension_lexicon` 引用（除上述文件与 CHANGELOG）。

## 验证

1. `.\gradlew.bat compileJava` — 编译通过。
2. `.\gradlew.bat :subterra-probes:runReadingSearchProbe` — PASS（20+ 断言，含日语与 zh+ja 共存）。
3. `.\gradlew.bat :subterra-probes:probeAcceptance` — 全探针绿（含开机门禁）。
4. `.\gradlew.bat runClient` — 日志出现 `[subterra_jec] reading pack 'zh' loaded`、`reading pack 'ja' loaded`、`reading search enabled (packs: zh,ja)`；无 mixin type-mismatch；创造模式搜索 拼音 `zs`→钻石、日语 `kana`→かな 类名命中。
5. （反例）`config/subterra/jec.td` 设 `enabled = false` 重启：搜索走原版，日志 `reading search disabled`，无异常。
6. `git add` 具体文件（含 git mv 产生的 rename）→ 单一 commit：`refactor: language-agnostic reading search with bundled pronunciation packs (p.1.4.9)`。

## 风险与既定取舍

- 旧 `pinyin_search` 顶层节静默回退 defaults（p 阶段不写兼容垫片，CHANGELOG 说明）。
- 空 `packs` = 全部内置（"显式零包"用 `enabled=false` 表达）。
- 拗音依赖引擎组合路径（单字不含 `kya`），CHANGELOG 说明。