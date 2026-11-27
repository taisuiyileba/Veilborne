# CurseForge 发布配置清单（Release Metadata Checklist）

本清单用于填写 CurseForge 新建项目（Create Project）与上传版本文件时的各项元数据，直接复制粘贴即可。

---

## 1. 基本信息（Project Info）

- **Project Name（项目名）：** `Veilborne`（或 `Veilborne · 天幕异闻`）
- **URL Slug（建议）：** `veilborne`
- **Mod ID：** `veilborne`
- **Current Release Version：** `1.0.0`
- **Release Jar：** `build/libs/veilborne-1.21.1-neoforge-1.0.0.jar`
- **Author：** `suiyi`
- **License：** `MIT License`（见仓库根目录 `LICENSE`）

---

## 2. 一句话摘要（Summary — 限约 250 字符以内）

- **English Summary (Recommended for CurseForge):**
  > Six regional, multiplayer-synchronized supernatural weathers for Minecraft 1.21.1 NeoForge—featuring custom-shader skies, celestial whales, cracked porcelain moons, real upside-down mirror skies, sky-suturing golden needles, a colour-draining Black Sun, and upside-down forests with lasting survival rewards.
  *(295 chars — 若平台限制更短，请使用精简版：)*
  > Six regional, synchronized supernatural weathers—an inverted starlit sea, cracking porcelain moons, a live upside-down mirror sky, a crimson stitched firmament, a colour-draining Black Sun, and an upside-down dream forest—each leaving lasting survival utilities.
  *(261 chars — 超精简版 178 chars：)*
  > Six regional, synchronized supernatural weathers with custom shader skies, interactive visitors, an illustrated Field Journal, completeness grades, and lasting survival utilities.

- **中文摘要：**
  > 为 Minecraft 1.21.1（NeoForge）带来六种区域性、多人同步的超自然奇幻天气：星海倒灌、月骸花潮、镜天巡礼、绯线缝世、无声白昼与苍翠梦潮。独立无依赖，不破坏建筑，并附带观测手记与长期实用余泽。

---

## 3. 分类与标签（Categories）

CurseForge 推荐主分类与副分类选择：
- **Primary Category（主分类）：** `World Gen` 或 `Adventure and RPG`（推荐 `Adventure and RPG` 或 `Cosmetic` / `Magic`）
- **Secondary Categories（副分类，建议勾选 2–4 个）：**
  - `Magic`（奇幻与超自然氛围、仪式召唤与道具）
  - `Adventure and RPG`（奇遇遭遇、镜廊、远航寻宝、完整度评级）
  - `Cosmetic`（自定义核心着色器天穹、装饰光源与墙挂残片）
  - `World Gen` / `Dimensions`（区域天候、镜中之塔地下石室与镜廊维度）

---

## 4. 环境与依赖（Environment & Dependencies）

- **Game Version：** `1.21.1`
- **Mod Loader：** `NeoForge`（最低要求 `21.1.248`）
- **Java Version：** `Java 21`
- **Side Requirement：** `Both Client and Server`（客户端与服务端均需安装）
- **Required Dependencies：** None（完全独立，无任何前置模组）

---

## 5. 发布文件与视觉素材清单（Assets Ready to Upload）

| 用途 | 文件路径 | 规格说明 |
| --- | --- | --- |
| **模组主程序（JAR）** | `build/libs/veilborne-1.21.1-neoforge-1.0.0.jar` | 含内置 `logo.png` 与完整中英文本 |
| **CurseForge 模组图标（Logo）** | `release/curseforge/mod_icon.png` | `512×512` PNG，清晰醒目，无文字裁切 |
| **简介顶部宣传横幅（Banner）** | `release/curseforge/banner.png` | `1376×768` 宽幅 PNG（原图无损尺寸，中英文页通用） |
| **英文项目介绍** | `release/curseforge/description_en.md` | 可直接粘贴至 CurseForge Description（Markdown 模式） |
| **中文项目介绍** | `release/curseforge/description_zh.md` | 适用于 MC百科 / 中文镜像页 / 双语折叠块 |
| **游戏实机截图集（20 张）** | `release/curseforge/screenshots/01_*.png` ~ `20_*.png` | 全部 `1280×720` 无 HUD 实机截图，图注见 `SCREENSHOTS.md` |

---

## 6. 首版 Changelog（1.0.0 — 上传文件时填写）

### English Changelog
```markdown
## Veilborne 1.0.0 (Minecraft 1.21.1 · NeoForge 21.1.248+)
Initial public release featuring all six supernatural weathers, lasting survival legacies, and completeness grading:
- **Six Regional Supernatural Weathers:** The Inverted Sea, Moon-Husk Bloom, Mirror-Sky Pilgrimage, Scarlet Seam, Silent Daylight, and Verdant Dream—each with 6 unfolded phases, custom core shaders, procedural entities, bespoke soundscapes, and interactive encounters.
- **Lasting Survival Utilities & Completeness System:** Earn 1–3 star completeness grades, bonus keepsakes, and long-term survival tools (Skiff Voyages & Star-Tide Pools, mob-warding Silver Flower Lanterns & Potion of Moonveil, Mirror Tower Vaults & Heart of the Cracked Mirror, linked Sky-Weaving Lamp warp networks & cross-dimensional Needle Core, Sculk-silencing Silent Lantern & Still Candle, and farm-boosting Dream Tree Heart & Dream Leaves).
- **Field Journal of the Veil:** Illustrated 6-chapter guidebook with timelines, crafting recipes, saved world marks, and completeness tracking.
- **Unified Commands & Configs:** `/veilborne <weather> start|stage|status|stop` plus granular client/server TOML settings.
- **Bilingual Localization:** Full English (`en_us`) and Simplified Chinese (`zh_cn`).
```

### 中文更新日志
```markdown
## 天幕异闻 · Veilborne 1.0.0（Minecraft 1.21.1 · NeoForge 21.1.248+）
首个公开发布版本，完整包含六大天幕异象、长期余泽与完整度体系：
- **六种区域同步超自然天气：** 星海倒灌、月骸花潮、镜天巡礼、绯线缝世、无声白昼与苍翠梦潮，均配备六段渐进演出、自定义核心着色器天穹、程序化实体、原创声景与交互奇遇。
- **长期实用余泽与完整度结算：** 每场异象结算 1–3 星完整度与余赠奖励，并留下实用原版生存工具（无海之舟远航与星潮池、防刷怪银花灯与月隐药水、镜塔地下宝箱室与裂镜之心、织天灯传送网与跨维度针芯、幽匿静音无声提灯与静烛、农牧双倍梦树心与通用饲料梦树叶）。
- **天幕观测手记：** 六章插画手记，内置阶段纪要、工艺配方、世界标记与完整度追踪。
- **统一命令与精细配置：** `/veilborne <异象> start|stage|status|stop` 与完备的客户端/服务端 TOML 配置。
- **完整双语支持：** 内置简体中文与英文全部文本与手记排版。
```
