"""Reproducible original assets: textures, models, language files, recipes and synthesized sounds.

Run from the project root:  python tools/generate_assets.py               everything
                            python tools/generate_assets.py --no-sound    skip the audio
                            python tools/generate_assets.py --moon-sound  audio for 月骸花潮 only
                            python tools/generate_assets.py --no-sound --moon-sky painting.png
                                                                          install a painted moon sky
                            python tools/generate_assets.py --mirror-sound audio for 镜天巡礼 only
                            python tools/generate_assets.py --no-sound --mirror-sky painting.png
                                                                          install a painted inverted horizon
                            python tools/generate_assets.py --thread-sound audio for 绯线缝世 only
                            python tools/generate_assets.py --no-sound --thread-sky painting.png
                                                                          install a painted crimson cloth sky
                            python tools/generate_assets.py --hush-sound  audio for 无声白昼 only
                            python tools/generate_assets.py --no-sound --hush-sky painting.png
                                                                          install a painted pale sky
                            python tools/generate_assets.py --dream-sound audio for 苍翠梦潮 only
                            python tools/generate_assets.py --no-sound --dream-sky painting.png
                                                                          install a painted forest canopy
Textures come from tools/art: geometry-aware skiff, whale and procession atlases, 16x16 item and block pixel
art, additive effect sprites, the moon's environment art and the journal GUI. No external samples or texture
packs are used; the painted skies are documented in docs/design/ASSET_SOURCES.md.
"""
from pathlib import Path
import sys
import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from art import skiff, whale, items, blocks, fx, gui, moon, bearer, moon_items, moon_gui, moon_sound
from art import mirror, mirror_models, mirror_items, mirror_gui, mirror_sound
from art import thread, thread_models, thread_items, thread_gui, thread_sound
from art import hush, hush_models, hush_items, hush_gui, hush_sound
from art import dream, dream_items, dream_gui, dream_sound
from art import legacy, legacy_text
from art.common import ROOT, ASSETS, json_file

if '--moon-sky' in sys.argv:
    import shutil
    moon.PAINTING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(sys.argv[sys.argv.index('--moon-sky') + 1], moon.PAINTING)
if '--mirror-sky' in sys.argv:
    import shutil
    mirror.PAINTING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(sys.argv[sys.argv.index('--mirror-sky') + 1], mirror.PAINTING)
if '--thread-sky' in sys.argv:
    import shutil
    thread.PAINTING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(sys.argv[sys.argv.index('--thread-sky') + 1], thread.PAINTING)
if '--hush-sky' in sys.argv:
    import shutil
    hush.PAINTING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(sys.argv[sys.argv.index('--hush-sky') + 1], hush.PAINTING)
if '--dream-sky' in sys.argv:
    import shutil
    dream.PAINTING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(sys.argv[sys.argv.index('--dream-sky') + 1], dream.PAINTING)

for module in (skiff, whale, items, blocks, fx, gui, moon, bearer, moon_items, moon_gui, mirror, mirror_models, mirror_items, mirror_gui, thread, thread_models, thread_items, thread_gui, hush, hush_models, hush_items, hush_gui, dream, dream_items, dream_gui, legacy):
    module.generate()

zh={'itemGroup.veilborne':'天幕异闻','block.veilborne.tide_lantern':'瓶中星海','entity.veilborne.tide_skiff':'无海之舟',
    'block.veilborne.silver_lantern':'银花灯','entity.veilborne.moon_procession':'送月人','entity.veilborne.moon_decoy':'月纱幻影',
    'block.veilborne.mirror_lantern':'镜灯','block.veilborne.sky_shard':'天镜残片','entity.veilborne.mirror_traveler':'迟到的旅人',
    'entity.veilborne.mirror_frames':'错位路标','entity.veilborne.mirror_beast':'镜兽',
    'block.veilborne.tuning_anchor':'调律锚','block.veilborne.loom_lantern':'织天灯','block.veilborne.robe_scrap':'天衣残片',
    'entity.veilborne.needle_walker':'走针者','entity.veilborne.thread_spirit':'线灵',
    'block.veilborne.still_candle':'静烛','block.veilborne.silent_lantern':'无声提灯','block.veilborne.still_feather':'罩中羽',
    'entity.veilborne.hush_procession':'无声行列','entity.veilborne.hush_coat':'敲门的空位',
    'block.veilborne.dream_sapling':'梦树苗','block.veilborne.dream_log':'梦树原木','block.veilborne.dream_leaves':'梦树叶',
    'block.veilborne.flower_lamp':'花灯','block.veilborne.antler_twig':'鹿角枝',
    'entity.veilborne.dream_seed':'梦种','entity.veilborne.leaf_school':'失路叶鱼'}
en={'itemGroup.veilborne':'Veilborne','block.veilborne.tide_lantern':'Bottled Sea','entity.veilborne.tide_skiff':'Shoreless Skiff',
    'block.veilborne.silver_lantern':'Silver Flower Lantern','entity.veilborne.moon_procession':'Moon Bearers','entity.veilborne.moon_decoy':'Gauze Likeness',
    'block.veilborne.mirror_lantern':'Mirror Lamp','block.veilborne.sky_shard':'Shard of the Mirror Sky','entity.veilborne.mirror_traveler':'Late Traveller',
    'entity.veilborne.mirror_frames':'Misaligned Mirrors','entity.veilborne.mirror_beast':'Glass Beast',
    'block.veilborne.tuning_anchor':'Tuning Anchor','block.veilborne.loom_lantern':'Sky-Weaving Lamp','block.veilborne.robe_scrap':'Scrap of the Heavenly Robe',
    'entity.veilborne.needle_walker':'Needle Walker','entity.veilborne.thread_spirit':'Thread Spirit',
    'block.veilborne.still_candle':'Still Candle','block.veilborne.silent_lantern':'Silent Lantern','block.veilborne.still_feather':'Feather Under Glass',
    'entity.veilborne.hush_procession':'Silent Procession','entity.veilborne.hush_coat':'The Empty Coat',
    'block.veilborne.dream_sapling':'Dream Sapling','block.veilborne.dream_log':'Dream Tree Log','block.veilborne.dream_leaves':'Dream Tree Leaves',
    'block.veilborne.flower_lamp':'Flower Lamp','block.veilborne.antler_twig':'Antler Twig',
    'entity.veilborne.dream_seed':'Dream Seed','entity.veilborne.leaf_school':'Lost Leaf-Fish'}
names={'tide_bottle':('星潮瓶','Bottled Starlight'),'drift_bottle':('抵岸的漂流瓶','Astral Drift Bottle'),'whale_scale':('星鲸鳞','Celestial Whale Scale'),'star_conch':('星螺','Star Conch'),'whale_mantle':('鲸纱披风','Whalewoven Mantle'),'field_journal':('天幕观测手记','Field Journal'),'tide_attuner':('听潮信标','Tide Attuner'),
       'moon_dew':('月露','Moon Dew'),'moon_shard':('月瓷片','Moonshell Shard'),'moon_gauze':('月纱','Moon Gauze'),'moon_dial':('银针月仪','Silver-Needle Moon Dial'),'moonward_bell':('望月铃','Moonward Bell'),
       'mirror_sand':('镜砂','Mirror Sand'),'observation_prism':('观测棱镜','Observation Prism'),'echo_mirror':('回响镜','Echo Mirror'),'sky_mirror':('透光镜','Light-Through Mirror'),
       'scarlet_thread':('绯线','Scarlet Thread'),'seam_silk':('时缝残丝','Time-Seam Silk'),'binding_charm':('系缚结','Binding Knot'),'heaven_needle':('天衣针','Needle of the Heavenly Robe'),
       'daylight_wax':('白昼蜡','Daylight Wax'),'hollow_bell':('空响铃','Hollow Bell'),'stopped_clock':('停摆钟','Stopped Clock'),
       'dream_seed':('梦种','Dream Seed'),'full_dream_seed':('饱满梦种','Full Dream Seed'),'golden_leaf':('金叶','Golden Leaf'),
       'drifting_leaf':('漂叶坠','Drifting Leaf Charm'),'deer_flute':('鹿鸣笛','Deer-Call Flute')}
for key,(cn,eng) in names.items():zh['item.veilborne.'+key]=cn;en['item.veilborne.'+key]=eng
strings={
'tooltip.veilborne.conch':('使用：聆听星海留下的鲸鸣。','Use to hear the call left by the celestial sea.'),
'tooltip.veilborne.mantle':('使用：缓落 30 秒；冷却 60 秒。','Use: slow falling for 30 seconds. Cooldown: 60 seconds.'),
'tooltip.veilborne.journal':('使用：展开天幕观测手记。','Use to read your notes on the veil.'),
'tooltip.veilborne.message':('一束星潮把它送到岸边。使用以开启。','A current brought it ashore. Use to open.'),
'tooltip.veilborne.attuner':('露天使用，消耗信标唤来一场完整星海。','Use under open sky to consume it and call a complete Inverted Sea.'),
'message.veilborne.bottle':('瓶中的纸条写着：「海在天上。鲸背上那艘船，一直在找回家的岸。」你留住了一瓶星潮。','The note reads: "The sea is above us. The ship upon the whale still seeks its home shore." You keep a bottle of the tide.'),
'message.veilborne.skiff':('无海之舟 · 蓑笠翁向你点头。按潜行键可随时安全下船','The Shoreless Skiff · The old fisherman nods. Sneak to disembark safely'),
'message.veilborne.conch_active':('星螺轻轻回应：海还没有退去。','The conch replies softly: the sea has not yet receded.'),
'message.veilborne.conch_quiet':('遥远的鲸鸣，像一段仍未结束的梦。','A distant whale call, like a dream that never quite ended.'),
'message.veilborne.attuner_blocked':('需要主世界的露天位置，且当前没有星海异象。','Find open sky in the Overworld while no Inverted Sea is active.'),
'message.veilborne.record.1':('观测记录 · 第一滴逆流的星雨','Observation · The first drop of rising starlight'),
'message.veilborne.record.2':('观测记录 · 见证古鲸越过天穹','Observation · Witnessed the celestial whale'),
'message.veilborne.record.4':('观测记录 · 接住鲸留下的一枚光鳞','Observation · A scale from beyond the sky'),
'message.veilborne.record.8':('观测记录 · 收到来自无海之岸的信','Observation · A letter from the shoreless coast'),
'message.veilborne.record.16':('观测记录 · 搭乘无海之舟','Observation · Aboard the shoreless skiff'),
# Field journal
'journal.veilborne.title':('星海倒灌','The Inverted Sea'),
'journal.veilborne.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.epigraph':('夜空先漏下一滴向上流的水。随后，海在头顶张开。','First a single drop falls upward into the night. Then the sea opens overhead.'),
'journal.veilborne.status':('此刻的天幕','The veil now'),
'journal.veilborne.quiet':('天幕平静。留意水面上升起的第一滴光。','The veil is quiet. Watch for the first drop rising from the water.'),
'journal.veilborne.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.phase.omen':('前兆','Omen'),
'journal.veilborne.phase.omen.text':('水滴离开河面升空，远处一声鲸鸣。','Drops leave the water and rise. A whale calls far away.'),
'journal.veilborne.phase.opening':('开幕','Opening'),
'journal.veilborne.phase.opening.text':('云层推开，倒悬的星海张开。','The clouds part and the inverted sea unfolds.'),
'journal.veilborne.phase.flood':('潮盛','High tide'),
'journal.veilborne.phase.flood.text':('光瀑垂落，来客陆续抵岸。','Light falls pour down; visitors come ashore.'),
'journal.veilborne.phase.whale':('鲸游','Whalepassage'),
'journal.veilborne.phase.whale.text':('古鲸背负沉船越过天幕。','The whale crosses the sky, a wreck upon its back.'),
'journal.veilborne.phase.afterglow':('余辉','Afterglow'),
'journal.veilborne.phase.afterglow.text':('光柱落地，鲸鳞在等你。','A beam touches down where a scale awaits.'),
'journal.veilborne.phase.ebb':('退潮','Ebb'),
'journal.veilborne.phase.ebb.text':('星海卷回天上，潮声渐远。','The sea rolls back into the sky; the tide fades.'),
'journal.veilborne.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.bottle':('抵岸的漂流瓶','A drift bottle'),
'journal.veilborne.bottle.text':('潮盛时，一束星潮把它送到你附近的岸上。开启后留下一瓶星潮。','At high tide a thin current brings it ashore near you. Open it to keep a bottle of the tide.'),
'journal.veilborne.skiff':('无海之舟','The shoreless skiff'),
'journal.veilborne.skiff.text':('蓑笠翁在离地数格处垂钓星星。右键同行，潜行即可下船。','An old fisherman angles for stars a few blocks above the ground. Board with use; sneak to step off.'),
'journal.veilborne.vortex':('浮潮涡','The floating tide'),
'journal.veilborne.vortex.text':('金环是预告，转青后圈内的人缓缓浮起，结束时附带缓落。不想参与就退到圈外。','The gold ring is a warning. When it turns cyan, anyone inside rises gently and lands with slow falling. Step out to decline.'),
'journal.veilborne.head.keepsakes':('鲸落一鳞','What the whale leaves'),
'journal.veilborne.scale':('星鲸鳞','A whale scale'),
'journal.veilborne.scale.text':('古鲸经过后，沿落地的光柱去寻找。','After the whale passes, follow the beam to where it fell.'),
'journal.veilborne.conch':('星螺','A star conch'),
'journal.veilborne.conch.text':('贴近耳边，还能听见这一场的鲸鸣。',"Hold it close and tonight's whale song still sounds."),
'journal.veilborne.mantle':('鲸纱披风','Whalewoven mantle'),
'journal.veilborne.mantle.text':('鲸鳞与幻翼膜织成。缓落 30 秒，冷却 60 秒。','Woven from scales and phantom membrane. Slow falling for 30 s, 60 s cooldown.'),
'journal.veilborne.head.crafting':('工艺','Crafting'),
'journal.veilborne.attuner':('在主世界露天使用，唤来一场完整的星海。已有异象或头顶有遮挡时不会消耗。','Use under open Overworld sky to call a complete sea. It is not consumed if a sea is already here or the sky is covered.'),
'journal.veilborne.lantern':('瓶中星海','Bottled sea'),
'journal.veilborne.lantern.text':('把星潮瓶放在家里，夜里发光，飘出细小星屑。','Set the bottle down at home; it glows and breathes out tiny motes.'),
'journal.veilborne.head.records':('观测记录','Observations'),
'journal.veilborne.count':('%s / %s','%s / %s'),
'journal.veilborne.unknown':('？？？ 尚未观测','??? Not yet observed'),
'journal.veilborne.hint.1':('在异象中亲眼看见逆流的光。','See the rising light for yourself.'),
'journal.veilborne.hint.2':('鲸游时，站在露天处仰望。','Stand under open sky as the whale passes.'),
'journal.veilborne.hint.4':('接住鲸落下的光鳞。','Catch the scale the whale lets fall.'),
'journal.veilborne.hint.8':('打开一只抵岸的漂流瓶。','Open a bottle that came ashore.'),
'journal.veilborne.hint.16':('登上无海之舟。','Ride the shoreless skiff.'),
'journal.veilborne.head.notes':('守则','Field notes'),
'journal.veilborne.note.0':('异象不会破坏建筑、清空容器，也不会强行带走任何人。','The weather never breaks buildings, empties chests or carries anyone off.'),
'journal.veilborne.note.1':('屋檐下同样能听潮；靠近门窗，潮声更清楚。','You can hear the tide indoors; it is clearer by doors and windows.'),
'journal.veilborne.note.2':('离开区域，天空会慢慢恢复原样。','Leave the region and the sky slowly returns to normal.'),
'journal.veilborne.closing':('海退去了。你的屋顶上，留下一枚仍在呼吸的贝壳。','The sea withdraws. On your roof, a shell is still breathing.'),
}
moon_strings={
'tooltip.veilborne.moon_dew':('饮用：夜视 60 秒，驱散黑暗与失明。','Drink: night vision for 60 seconds; clears darkness and blindness.'),
'tooltip.veilborne.moon_shard':('从月壳上剥落的一片，断口还留着金色的光。',"A flake of the moon's shell; its broken edge still holds gold light."),
'tooltip.veilborne.moon_gauze':('使用：留下一个引开敌意的月光幻影，并短暂隐身；冷却 30 秒。','Use: leave a moonlit likeness that draws hostile attention, and turn briefly invisible. Cooldown 30 s.'),
'tooltip.veilborne.moon_dial':('三月同天时，对准你认定的真月并使用。','When three moons hang, aim at the one you believe is real and use it.'),
'tooltip.veilborne.moon_bell':('露天使用，消耗铃铛唤来一场完整的月骸花潮。','Use under open sky to consume it and call a complete Moon-Husk Bloom.'),
'message.veilborne.bell_blocked':('需要主世界的露天位置，且当前没有其他天幕异象。','Find open Overworld sky while no other veil weather is under way.'),
'message.veilborne.dial_quiet':('指针只是缓缓游移。今夜天上没有需要辨认的月亮。','The needle only drifts. There is no moon to tell apart tonight.'),
'message.veilborne.dial_waiting':('指针微微发颤——还不到辨月的时候。','The needle trembles; it is not yet time.'),
'message.veilborne.dial_settled':('天上只剩一轮月亮了。','Only one moon remains in the sky.'),
'message.veilborne.dial_done':('倒影已经碎了。','The reflections have already broken.'),
'message.veilborne.dial_aim':('先对准其中一轮月亮，再使用月仪。','Aim at one of the moons first, then use the dial.'),
'message.veilborne.dial_false':('银针偏开了：那是一轮倒影。','The needle swings away: that one is a reflection.'),
'message.veilborne.dial_true':('银针定住了。两轮倒影像碎瓷一样剥落，真正的月亮回到原处。','The needle holds still. The two reflections flake away like broken porcelain, and the true moon returns to its place.'),
'message.veilborne.dew':('你接住了一瓶月露。','You catch a bottle of moon dew.'),
'message.veilborne.dew_empty':('小月亮已经把能给的都给你了。','The little moon has already given you all it can.'),
'message.veilborne.record.32':('观测记录 · 第一片落地的月瓷','Observation · The first shard to reach the ground'),
'message.veilborne.record.64':('观测记录 · 目送月壳剥落','Observation · Watched the moon shed its shell'),
'message.veilborne.record.128':('观测记录 · 与送月人同行','Observation · Walked beside the moon bearers'),
'message.veilborne.record.256':('观测记录 · 只在一处成立的星座','Observation · A constellation true from one place only'),
'message.veilborne.record.512':('观测记录 · 辨认真月','Observation · Told the true moon from its reflections'),
'message.veilborne.record.1024':('观测记录 · 空位里有人向你挥手','Observation · Someone waved from the empty place'),
'journal.veilborne.hint.32':('在异象中亲眼看见落下的花瓣。','See the falling petals for yourself.'),
'journal.veilborne.hint.64':('蜕月时，站在露天处仰望。','Stand under open sky when the shell comes away.'),
'journal.veilborne.hint.128':('与送月人保持距离，同行一段路。','Keep your distance from the bearers and walk with them a while.'),
'journal.veilborne.hint.256':('站进小月亮旁的金环，看向它。','Stand in the gold ring beside the little moon and look at it.'),
'journal.veilborne.hint.512':('三月同天时，用银针月仪辨认真月。','When three moons hang, use the silver dial on the true one.'),
'journal.veilborne.hint.1024':('提着银花灯，走近那个始终空着的位置。','Carry a silver lantern close to the place that is always empty.'),
'journal.veilborne.moon.title':('月骸花潮','Moon-Husk Bloom'),
'journal.veilborne.moon.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.moon.epigraph':('月亮的外壳裂成薄瓷，落进夜里，便成了花。',"The moon's shell cracks into thin porcelain, and what falls into the night becomes flowers."),
'journal.veilborne.moon.quiet':('天幕平静。留意月亮周围是否出现一圈光环。','The veil is quiet. Watch for a ring around the moon.'),
'journal.veilborne.moon.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.moon.phase.halo':('月晕','Halo'),
'journal.veilborne.moon.phase.halo.text':('月亮戴上一圈光环，影子开始拉长。','A ring forms around the moon; shadows begin to lengthen.'),
'journal.veilborne.moon.phase.crack':('裂壳','Cracking'),
'journal.veilborne.moon.phase.crack.text':('金光从裂缝里渗出，第一批瓷片剥落。','Gold light seeps through the cracks; the first flakes fall.'),
'journal.veilborne.moon.phase.bloom':('花潮','Bloom'),
'journal.veilborne.moon.phase.bloom.text':('瓷片化作花瓣，落地处绽开银白花影。','Flakes turn to petals; silver flowers open where they land.'),
'journal.veilborne.moon.phase.molt':('蜕月','Molt'),
'journal.veilborne.moon.phase.molt.text':('月壳大片剥落，天上出现三轮月亮。','The shell comes away, and three moons hang in the sky.'),
'journal.veilborne.moon.phase.farewell':('送月','Farewell'),
'journal.veilborne.moon.phase.farewell.text':('送月人在路边留下一盏灯。','The bearers leave a lantern by the road.'),
'journal.veilborne.moon.phase.close':('合壳','Closing'),
'journal.veilborne.moon.phase.close.text':('月壳重新合拢，花影一圈圈闭合。','The shell closes again; the flowers close ring by ring.'),
'journal.veilborne.moon.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.moon.bearers':('送月人','The moon bearers'),
'journal.veilborne.moon.bearers.text':('无面的披纱身影抬着一片月壳走过。保持距离同行，花路会指向他们的终点；走进队列，他们会散成花瓣。','Faceless, veiled figures carry a piece of the shell. Walk beside them at a distance and a flower road shows where they are going; walk into their line and they scatter into petals.'),
'journal.veilborne.moon.little_moon':('落地的小月亮','The fallen little moon'),
'journal.veilborne.moon.little_moon.text':('林间漂浮的小小月体。拿空瓶靠近它可以接住月露；站进金环里看向它。','A tiny moon floating among the trees. Hold an empty bottle near it to catch moon dew, and stand in the gold ring to look at it.'),
'journal.veilborne.moon.three_moons':('三月同天','Three moons'),
'journal.veilborne.moon.three_moons.text':('花瓣总从真月那边吹来，月影背向真月。用银针月仪对准它，倒影便会碎去。','Petals always blow from the true moon, and moon-shadows point away from it. Aim the silver dial at it and the reflections break.'),
'journal.veilborne.moon.head.keepsakes':('月骸余物','What the moon leaves'),
'journal.veilborne.moon.dew':('月露','Moon dew'),
'journal.veilborne.moon.dew.text':('小月亮给的一瓶光。饮用得夜视，也是月纱与银花灯的材料。',"A bottle of the little moon's light. Drink for night vision; also used for moon gauze and silver lanterns."),
'journal.veilborne.moon.shard':('月瓷片','Moonshell shards'),
'journal.veilborne.moon.shard.text':('辨出真月、小月亮离去、送月人留灯时，地上都会留下几片。','A few are left when the true moon is found, when the little moon leaves, and where the lantern is set down.'),
'journal.veilborne.moon.lantern':('银花灯','The silver lantern'),
'journal.veilborne.moon.lantern.text':('最后一名送月人放在路边的灯。纯装饰，拆下即可带回家。','The lantern the last bearer sets by the road. Purely decorative; break it to take it home.'),
'journal.veilborne.moon.head.crafting':('工艺','Crafting'),
'journal.veilborne.moon.bell':('在主世界露天使用，唤来一场完整的月骸花潮。已有异象或头顶有遮挡时不会消耗。','Use under open Overworld sky to call a complete bloom. It is not consumed while another weather is under way or the sky is covered.'),
'journal.veilborne.moon.lantern_craft':('银花灯','Silver flower lantern'),
'journal.veilborne.moon.lantern_craft.text':('月瓷片、月露与一段锁链也能做一盏，放在家中发出银白的光。','Moonshell shards, moon dew and a chain make one too; at home it gives a soft silver light.'),
'journal.veilborne.moon.head.records':('观测记录','Observations'),
'journal.veilborne.moon.head.notes':('守则','Field notes'),
'journal.veilborne.moon.note.0':('异象不会破坏建筑、清空容器，也不会强行带走任何人。送月人不会进屋。','The weather never breaks buildings, empties chests or carries anyone off. The bearers never enter a house.'),
'journal.veilborne.moon.note.1':('月影的动作会比你慢半拍。若觉得不适，可在客户端设置中调小或关闭。','Your moon-shadow moves half a beat behind you. If that feels unsettling, shorten or turn it off in the client settings.'),
'journal.veilborne.moon.note.2':('花潮里偶尔能听见一段不完整的摇篮曲。','Inside the flower tide you may hear pieces of a lullaby.'),
'journal.veilborne.moon.closing':('月壳合上了。路边那盏灯还亮着。','The shell has closed. The lantern by the road is still lit.'),
}
strings.update(moon_strings)

mirror_strings={
'tooltip.veilborne.mirror_sand':('每一粒都是一面极小的镜子。','Every grain is a very small mirror.'),
'tooltip.veilborne.mirror_prism':('按住使用举到眼前：看见镜中才有的东西。','Hold use to raise it to your eye and see what only the mirror shows.'),
'tooltip.veilborne.mirror_echo':('使用：照出你最近半分钟走过的路；冷却 30 秒。','Use: show the path you walked in the last half minute. Cooldown 30 s.'),
'tooltip.veilborne.mirror_sky_mirror':('露天举起，消耗铜镜唤来一场完整的镜天巡礼。','Raise it under open sky to consume it and call a complete Mirror-Sky Pilgrimage.'),
'message.veilborne.sky_mirror_blocked':('需要主世界的露天位置，且当前没有其他天幕异象。','Find open Overworld sky while no other veil weather is under way.'),
'message.veilborne.door_revealed':('棱镜里，墙上浮出一道光框——镜中那扇门，这里也有了。','In the prism a frame of light rises on the wall: the door from the mirror is here too.'),
'message.veilborne.door_closed':('光框在变淡。镜子快要碎了，门不再开。','The frame is fading. The mirror is about to break; the door will not open now.'),
'message.veilborne.door_sealed':('门没有回应。（镜廊在这个世界里被关闭了）','The door does not answer. (The gallery is disabled in this world.)'),
'message.veilborne.gallery_enter':('镜廊 · 镜子里的你会慢一拍。回到门口右键即可离开','The Mirror Gallery · Your reflection runs a beat late. Use the door you came in by to leave'),
'message.veilborne.gallery_warn':('镜廊的光在变暗，该回去了。','The gallery light is dimming; time to go back.'),
'message.veilborne.gallery_time':('镜廊把你送回了门外。','The gallery set you back outside its door.'),
'message.veilborne.gallery_closing':('镜子要碎了，镜廊把你送了回来。','The mirror is breaking; the gallery has sent you back.'),
'message.veilborne.gallery_reward':('镜中的你终于走到了台前，替你把它取了下来。','Your reflection reaches the pedestal at last and takes it down for you.'),
'message.veilborne.frames_true':('只有这一面照着此刻。另外两面碎成了光。','Only this one shows the present. The other two break into light.'),
'message.veilborne.frames_late':('这面镜子里的你，慢了半拍。','The you in this mirror is half a beat late.'),
'message.veilborne.frames_elsewhere':('这面镜子照的是别处——你身后并没有人。','This mirror shows somewhere else; there is no one behind you.'),
'message.veilborne.record.2048':('观测记录 · 天空镀上了银','Observation · The sky turned to silver'),
'message.veilborne.record.4096':('观测记录 · 巡礼者从镜中走过','Observation · The pilgrim walked through the mirror'),
'message.veilborne.record.8192':('观测记录 · 多出来的那扇门','Observation · The door that was one too many'),
'message.veilborne.record.16384':('观测记录 · 镜中的你替你取来了它','Observation · Your reflection fetched it for you'),
'message.veilborne.record.32768':('观测记录 · 照出此刻的那面镜子','Observation · The mirror that showed the present'),
'message.veilborne.record.65536':('观测记录 · 跟上了迟到的旅人','Observation · Caught up with the late traveller'),
'message.veilborne.record.131072':('观测记录 · 镜中的巡礼者认出了你','Observation · The pilgrim in the mirror knew you'),
'journal.veilborne.hint.2048':('在异象中亲眼看见天空变成镜子。','See the sky become a mirror.'),
'journal.veilborne.hint.4096':('巡礼时站在露天处，等它从镜中大地走近你。','Stand under open sky while the pilgrim walks the mirrored land toward you.'),
'journal.veilborne.hint.8192':('找到镜中多出来的门，用观测棱镜在现实里对准它。','Find the door only the mirror has, and hold the prism on that spot in the world.'),
'journal.veilborne.hint.16384':('穿过那扇门，等镜中的自己走到台前。','Go through that door and wait for your reflection to reach the pedestal.'),
'journal.veilborne.hint.32768':('三面镜框里只有一面照着此刻，用棱镜认出它。','Of three standing mirrors only one shows the present; find it with the prism.'),
'journal.veilborne.hint.65536':('跟着那个走你走过的路的人，走到它停下的地方。','Follow the one walking the path you walked, to where it stops.'),
'journal.veilborne.mirror.title':('镜天巡礼','Mirror-Sky Pilgrimage'),
'journal.veilborne.mirror.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.mirror.epigraph':('天空成了一面不太平整的镜子。镜里的大地倒悬着，只比地上多了一扇门。','The sky became a not quite even mirror. The land hangs in it upside down, with one more door than the ground has.'),
'journal.veilborne.mirror.quiet':('天幕平静。留意水面是否照出一片不属于此刻的晴空。','The veil is quiet. Watch for still water showing a clear sky that does not belong to now.'),
'journal.veilborne.mirror.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.mirror.phase.omen':('镜兆','Omen'),
'journal.veilborne.mirror.phase.omen.text':('静水照出天气过后的晴空，有些脚步声晚一拍回来。','Still water shows the clear sky of afterwards; some footsteps come back late.'),
'journal.veilborne.mirror.phase.silver':('覆镜','Silvering'),
'journal.veilborne.mirror.phase.silver.text':('银从天顶结霜般铺下，云散去，镜片在半空转动。','Silver creeps down from the zenith like frost; the clouds go; shards turn in the air.'),
'journal.veilborne.mirror.phase.reflect':('倒影','Reflection'),
'journal.veilborne.mirror.phase.reflect.text':('倒悬的大地浮现，多出一座塔、一扇门。','The inverted land appears, with one more tower and one more door.'),
'journal.veilborne.mirror.phase.pilgrim':('巡礼','Pilgrimage'),
'journal.veilborne.mirror.phase.pilgrim.text':('巡礼者穿过镜中大地，在你身旁停下，转身低头看你。','The pilgrim crosses the mirrored land, stops beside you, turns and looks down at you.'),
'journal.veilborne.mirror.phase.crack':('裂镜','Cracking'),
'journal.veilborne.mirror.phase.crack.text':('裂纹像极慢的闪电，贯穿整片天穹。','Cracks run across the whole sky like very slow lightning.'),
'journal.veilborne.mirror.phase.clear':('归天','Clearing'),
'journal.veilborne.mirror.phase.clear.text':('镜片一块块落下化作光，真正的天空回来了。','The facets fall one by one as light, and the real sky returns.'),
'journal.veilborne.mirror.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.mirror.door':('多出来的门','The door that is one too many'),
'journal.veilborne.mirror.door.text':('抬头找：镜里某面墙上有一扇地上没有的门。举起观测棱镜对准那面墙，光框就会出现；推开它，进入镜廊。','Look up: in the mirror some wall has a door the ground lacks. Hold the observation prism on that wall and a frame of light appears; step through into the gallery.'),
'journal.veilborne.mirror.traveler':('迟到的旅人','The late traveller'),
'journal.veilborne.mirror.traveler.text':('一个淡色的你，在你不久前走过的地方重走那一段路。它会多走几步，指向附近值得一看的东西。','A pale you walks again a stretch of the path you took a little while ago. It goes a few steps further and points at something worth seeing.'),
'journal.veilborne.mirror.frames':('错位路标','The misaligned mirrors'),
'journal.veilborne.mirror.frames.text':('三面立着的镜子都照得见你，但只有一面照着此刻：一面慢半拍，一面照的是别处。站在中间看清楚，再用棱镜认出它。','Three standing mirrors all show you, but only one shows the present: one runs half a beat late, one shows somewhere else. Stand in the middle, watch, then pick it out with the prism.'),
'journal.veilborne.mirror.head.keepsakes':('镜中余物','What the mirror leaves'),
'journal.veilborne.mirror.beasts':('镜兽','Glass beasts'),
'journal.veilborne.mirror.beasts.text':('低处的裂片上爬着玻璃般的小兽。靠近时先有高鸣和一圈碎光；那时退开就没事，留下来裂片就会炸开。','Little glass creatures crawl on the low cracked shards. Come close and a high ringing and a ring of glints warn you; step back then and nothing happens, stay and the shard bursts.'),
'journal.veilborne.mirror.sand':('镜砂','Mirror sand'),
'journal.veilborne.mirror.sand.text':('认出照着此刻的镜子、打碎镜兽、走完镜廊都会得到。','Earned from the mirror that shows the present, from glass beasts, and from the gallery.'),
'journal.veilborne.mirror.shard':('天镜残片','Shard of the mirror sky'),
'journal.veilborne.mirror.shard.text':('镜子碎尽时落在你脚边的一片，挂到墙上，它还照着那片倒悬的大地。','The piece that lands at your feet when the mirror has fallen. Hang it on a wall; it still shows the land upside down.'),
'journal.veilborne.mirror.head.crafting':('工艺','Crafting'),
'journal.veilborne.mirror.sky_mirror':('在主世界露天举起，唤来一场完整的镜天巡礼。已有异象或头顶有遮挡时不会消耗。','Raise it under open Overworld sky to call a complete pilgrimage. It is not consumed while another weather is under way or the sky is covered.'),
'journal.veilborne.mirror.lantern':('镜灯','Mirror lamp'),
'journal.veilborne.mirror.lantern.text':('火苗前的一面铜镜把光只往一个方向送：放下时，光朝向你。','A bronze mirror behind a small flame throws the light one way only: toward you when you set it down.'),
'journal.veilborne.mirror.head.records':('观测记录','Observations'),
'journal.veilborne.mirror.head.notes':('守则','Field notes'),
'journal.veilborne.mirror.note.0':('异象不会破坏建筑，也不会强行带走任何人。镜廊只在你自己推门时才进得去，出口永远通回原处。','The weather never breaks buildings or carries anyone off. You only enter the gallery by opening its door yourself, and its way out always leads back.'),
'journal.veilborne.mirror.note.1':('脚步的回声和身后的残影只是镜子的毛病，若觉得不适，可在客户端设置中关闭。','Late echoes and afterimages are only the mirror misbehaving; turn them off in the client settings if they bother you.'),
'journal.veilborne.mirror.note.2':('镜兽只在你靠近它们的裂片、并在预警后仍不退开时才会醒来。','Glass beasts wake only if you come to their shard and stay after its warning.'),
'journal.veilborne.mirror.closing':('镜子碎尽了。墙上那一小片里，倒悬的大地还在慢慢漂。','The mirror has fallen. In the small piece on the wall, the inverted land still drifts.'),
}
strings.update(mirror_strings)

thread_strings={
'tooltip.veilborne.thread_thread':('从天上垂下的一截线头，还带着一点余温。','A thread end let down from the sky, still faintly warm.'),
'tooltip.veilborne.thread_silk':('缝进过时间的丝：摸上去，像摸到刚刚过去的几秒。','Silk that has been sewn through time: touching it is like touching the seconds just gone.'),
'tooltip.veilborne.thread_charm':('带在身上：异象再也牵不动、推不开你。','Carried: the weather can no longer pull you or throw you.'),
'tooltip.veilborne.thread_needle':('露天举起，消耗金针唤来一场完整的绯线缝世。','Raise it under open sky to consume it and call a complete Scarlet Seam.'),
'message.veilborne.needle_blocked':('需要主世界的露天位置，且当前没有其他天幕异象。','Find open Overworld sky while no other veil weather is under way.'),
'message.veilborne.knot_undone':('线结散开了——三个线头飞了出去，走针者转向了你！','The knot comes undone: three loose ends fly out, and the walker turns on you!'),
'message.veilborne.knot_caught':('你抢先接住了三个线头。线结的芯留在你手里：一缕时缝残丝。',"You caught all three ends first. The knot's heart is left in your hand: a strand of time-seam silk."),
'message.veilborne.anchor_tuning':('调律锚嗡嗡作响，口袋里漂浮的旧物开始向它聚拢……','The tuning anchor hums, and the old things floating in the pocket begin to drift toward it...'),
'message.veilborne.record.262144':('观测记录 · 天幕裂开了','Observation · The veil tore open'),
'message.veilborne.record.524288':('观测记录 · 见证世界被缝上','Observation · Watched the world being sewn'),
'message.veilborne.record.1048576':('观测记录 · 读懂了落针的预告',"Observation · Read the needle's warning"),
'message.veilborne.record.2097152':('观测记录 · 接住一截游离的线头','Observation · Caught a loose thread end'),
'message.veilborne.record.4194304':('观测记录 · 解开走针者的线结',"Observation · Undid the needle walker's knot"),
'message.veilborne.record.8388608':('观测记录 · 调好了未缝合的口袋','Observation · Tuned the unsewn pocket'),
'message.veilborne.record.16777216':('观测记录 · 你看见了牵着你的那根线','Observation · You saw the thread that holds you'),
'journal.veilborne.hint.262144':('在异象中亲眼看见天幕裂开。','See the veil tear open for yourself.'),
'journal.veilborne.hint.524288':('缝世时站在露天处，看金针落进大地。','Stand under open sky when the needle comes down into the land.'),
'journal.veilborne.hint.1048576':('站进地上亮起的预告符环，在针落下前走出来。','Step into a warned ring on the ground, and out again before the needle falls.'),
'journal.veilborne.hint.2097152':('接住一截从天上垂落的线头。','Catch a thread end drifting down from the sky.'),
'journal.veilborne.hint.4194304':('碰一碰走针者的线结，抢在它之前接住三个线头。',"Touch the needle walker's knot, then catch its three loose ends before it does."),
'journal.veilborne.hint.8388608':('在未缝合的口袋里放下一只调律锚。','Set a tuning anchor down inside the unsewn pocket.'),
'journal.veilborne.thread.title':('绯线缝世','Scarlet Seam'),
'journal.veilborne.thread.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.thread.epigraph':('天衣本无缝。只是每隔许多年，就有人来补一次。','The robe of heaven has no seams. It is only that, every so many years, someone comes to mend it.'),
'journal.veilborne.thread.quiet':('天幕平静。留意高处是否垂下一根没有来处的红线。','The veil is quiet. Watch for a red thread hanging from nowhere.'),
'journal.veilborne.thread.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.thread.phase.omen':('引线','Threading'),
'journal.veilborne.thread.phase.omen.text':('红线挂上高处，尘埃都往一个方向飘，天上闪过一道金色细缝。','Red threads hang from high places, dust drifts one way, and a gold hairline flickers across the sky.'),
'journal.veilborne.thread.phase.tear':('裂天','Tearing'),
'journal.veilborne.thread.phase.tear.text':('天幕从中间撕开，缝里是没有星星的黑。','The veil tears open from the middle; inside is a dark with no stars.'),
'journal.veilborne.thread.phase.needle':('落针','The needle'),
'journal.veilborne.thread.phase.needle.text':('一根金针从裂缝那边伸出来，一针一针把伤口缝上。','A golden needle comes through from the other side and sutures the wound, stitch by stitch.'),
'journal.veilborne.thread.phase.sew':('缝世','Sewing the world'),
'journal.veilborne.thread.phase.sew.text':('金针落进大地，把世界缝到天上；看不见的手把线拉紧。','The needle comes down into the land and sews the world to the sky; a hand nobody sees pulls the thread tight.'),
'journal.veilborne.thread.phase.knot':('收线','Knotting'),
'journal.veilborne.thread.phase.knot.text':('线在天上打了结，被剪断；针退回缝里。','The thread is knotted in the sky and cut; the needle goes back into the seam.'),
'journal.veilborne.thread.phase.fade':('余线','Remnant'),
'journal.veilborne.thread.phase.fade.text':('缝线冷成一道金线，慢慢变成一条普通的星带。','The seam cools into a gold line, and slowly into an ordinary band of stars.'),
'journal.veilborne.thread.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.thread.strike':('落针区','Where the needle falls'),
'journal.veilborne.thread.strike.text':('地上先亮起一圈宽阔的符线，尘屑往中间旋，声音越收越紧。那是预告：走出圈外就没事，留在圈里会被击倒。房屋不会受损。','First a broad ring of lines lights on the ground, dust spirals inward and a sound winds tighter. That is the warning: step out of the ring and nothing happens; stay and you are thrown down. Buildings are never harmed.'),
'journal.veilborne.thread.walker':('走针者','The needle walker'),
'journal.veilborne.thread.walker.text':('黄铜的六足之物，顺着落针留下的线爬下来，收拢地上的线头，然后坐在线脚下打结。别碰它的线结——除非你想和它抢那三个线头。',"A six-legged thing of brass that climbs down the thread a strike leaves, gathers loose ends from the ground and sits at the thread's foot tying a knot. Leave the knot alone, unless you want to race it for three loose ends."),
'journal.veilborne.thread.pocket':('未缝合的口袋','The unsewn pocket'),
'journal.veilborne.thread.pocket.text':('金针离开大地后，落针处留下一小块没缝上的地方：风吹不进去，里面漂着旧东西的影子。在里面放下调律锚，能把它们收成时缝残丝，但线灵会醒来。','Where the needle left the land a small place stays unsewn: no wind gets in, and shadows of old things float there. Set a tuning anchor down inside to gather them into time-seam silk, but thread spirits will wake.'),
'journal.veilborne.thread.head.keepsakes':('缝余之物','What the sewing leaves'),
'journal.veilborne.thread.thread':('绯线','Scarlet thread'),
'journal.veilborne.thread.thread.text':('落针与缝世时从天上垂下的线头，接住就是你的。走针者也在捡。','Thread ends let down while the needle sews; catch them and they are yours. The walker gathers them too.'),
'journal.veilborne.thread.silk':('时缝残丝','Time-seam silk'),
'journal.veilborne.thread.silk.text':('来自走针者的线结之芯，或调好的口袋。调律锚、织天灯与系缚结都要用它。',"From the heart of the walker's knot, or a tuned pocket. The tuning anchor, the sky-weaving lamp and the binding knot all need it."),
'journal.veilborne.thread.scrap':('天衣残片','Scrap of the heavenly robe'),
'journal.veilborne.thread.scrap.text':('缝线冷成星带时，落在你身边的一小片天。挂到墙上，上面的星星还在闪。','When the seam cools into stars, a small piece of the sky comes down beside you. Hang it on a wall; its stars still twinkle.'),
'journal.veilborne.thread.head.crafting':('工艺','Crafting'),
'journal.veilborne.thread.needle':('在主世界露天举起，唤来一场完整的绯线缝世。已有异象或头顶有遮挡时不会消耗。','Raise it under open Overworld sky to call a complete sewing. It is not consumed while another weather is under way or the sky is covered.'),
'journal.veilborne.thread.lantern':('织天灯','Sky-weaving lamp'),
'journal.veilborne.thread.lantern.text':('彼此相距十格以内的织天灯，会被一根发光的红线连起来。','Sky-weaving lamps within ten blocks of each other are joined by a glowing red thread.'),
'journal.veilborne.thread.head.records':('观测记录','Observations'),
'journal.veilborne.thread.head.notes':('守则','Field notes'),
'journal.veilborne.thread.note.0':('异象不会破坏建筑，也不会强行带走任何人；针只落在露天的空地上。','The weather never breaks buildings or carries anyone off; the needle only comes down on open ground.'),
'journal.veilborne.thread.note.1':('拉线时，露天的人会被轻轻牵向缝地的那条线。带着系缚结，或站在调律锚旁，就不会被牵动。','When the thread is pulled, anyone out in the open is tugged gently toward the land stitch. Carry a binding knot or stand by a tuning anchor and it cannot move you.'),
'journal.veilborne.thread.note.2':('拉线的那几秒，世界会叠出刚刚过去的影子，每个活物头顶都有一根线。若觉得不适，可在客户端设置中关闭。','For the seconds of the pull the world is overlaid with the moment just past, and every living thing has a thread above its head. If that is unsettling, turn it off in the client settings.'),
'journal.veilborne.thread.closing':('天上只剩一道很细的金线。有人说，银河本来也是这样缝过的。','Only a very fine gold line is left in the sky. Some say the Milky Way was sewn like this once.'),
}
strings.update(thread_strings)

hush_strings={
'tooltip.veilborne.hush_wax':('苍白、微凉，捏在手里没有一点声音。','Pale and cool, and it makes no sound at all in your hand.'),
'tooltip.veilborne.hush_bell':('在天幕异象中摇响，记下这一场的声音；之后摇响，便把它放出来。','Ring it inside a veil weather to keep its sound; ring it later to let it out.'),
'tooltip.veilborne.hush_bell_empty':('里面什么也没有。','It holds nothing.'),
'tooltip.veilborne.hush_bell_holds':('里面是%s的声音。','It holds the sound of %s.'),
'tooltip.veilborne.hush_clock':('露天举起，消耗停摆钟唤来一场完整的无声白昼。','Raise it under open sky to consume it and call a complete Silent Daylight.'),
'weather.veilborne.sea':('星海倒灌','the Inverted Sea'),'weather.veilborne.moon':('月骸花潮','the Moon-Husk Bloom'),'weather.veilborne.mirror':('镜天巡礼','the Mirror-Sky Pilgrimage'),
'weather.veilborne.thread':('绯线缝世','the Scarlet Seam'),'weather.veilborne.hush':('无声白昼','the Silent Daylight'),
'message.veilborne.clock_blocked':('需要主世界的露天位置，且当前没有其他天幕异象。','Find open Overworld sky while no other veil weather is under way.'),
'message.veilborne.bell_recorded':('空响铃记下了%s的声音。','The hollow bell keeps the sound of %s.'),
'message.veilborne.record2.1':('观测记录 · 世界失声了','Observation · The world went silent'),
'message.veilborne.record2.2':('观测记录 · 黑日盖住了太阳','Observation · The black sun covered the sun'),
'message.veilborne.record2.4':('观测记录 · 回应了门外的敲门声','Observation · Answered the knock at the door'),
'message.veilborne.record2.8':('观测记录 · 找到了唯一的声音','Observation · Found the only sound'),
'message.veilborne.record2.16':('观测记录 · 无声行列向你提灯','Observation · The silent procession raised its lantern to you'),
'message.veilborne.record2.32':('观测记录 · 等到了迟来的雷','Observation · Waited out the late thunder'),
'message.veilborne.record2.64':('观测记录 · 黑日听见了你','Observation · The black sun heard you'),
'journal.veilborne.hint2.1':('在异象中亲眼看见世界褪色。','See the world lose its colour for yourself.'),
'journal.veilborne.hint2.2':('蚀的时候，站在露天处仰望。','Stand under open sky while the black sun covers the sun.'),
'journal.veilborne.hint2.4':('听见敲门声时开门，对门外举起灯。','When the knocking comes, open the door and raise a light to what stands outside.'),
'journal.veilborne.hint2.8':('循着铃声，走到唯一还在响的那只铃跟前。','Follow the ringing to the one bell that still sounds.'),
'journal.veilborne.hint2.16':('对走过的无声行列举起灯。','Raise a light to the silent procession as it passes.'),
'journal.veilborne.hint2.32':('看见地平线上的白线后，留在原地，等它的雷。','When the white line stands on the horizon, stay where you are and wait for its thunder.'),
'journal.veilborne.hush.title':('无声白昼','Silent Daylight'),
'journal.veilborne.hush.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.hush.epigraph':('世界彻底安静以后，门外传来了唯一的一次轻敲。','When the world had gone quite silent, there came a single soft knock at the door.'),
'journal.veilborne.hush.quiet':('天幕平静。留意音乐是否在某一刻无故停下。','The veil is quiet. Notice if the music ever stops for no reason.'),
'journal.veilborne.hush.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.hush.phase.omen':('失声','Hush'),
'journal.veilborne.hush.phase.omen.text':('音乐先停，然后是风、洞穴与动物，最后连你的脚步也没有了声音；空中的尘埃停在原处。','First the music stops, then the wind, the caves and the animals, and at last your own footsteps; the dust in the air stops where it is.'),
'journal.veilborne.hush.phase.fade':('褪色','Fading'),
'journal.veilborne.hush.phase.fade.text':('灰色像潮水一样从地平线漫到脚下，世界褪成浅灰，只有灯火旁留下一点旧照片般的暖黄。','Grey comes in like a tide from the horizon to your feet; the world pales, and only lamps keep a little warmth, sepia, like an old photograph.'),
'journal.veilborne.hush.phase.sun':('黑日','Black sun'),
'journal.veilborne.hush.phase.sun.text':('一颗不发光的黑日从北方升起，天空在它周围弯曲，世界的颜色一缕缕流进它。','A black sun that gives no light rises in the north; the sky bends around it, and the colours of the world drain into it in threads.'),
'journal.veilborne.hush.phase.eclipse':('蚀','Eclipse'),
'journal.veilborne.hush.phase.eclipse.text':('黑日盖住太阳：世界没有变暗，而是一片无影的白。万籁俱寂，它周围只剩一圈黑色的日冕。','The black sun covers the sun: nothing darkens; everything goes white and shadowless. Nothing sounds, and a black corona hangs about it.'),
'journal.veilborne.hush.phase.thunder':('迟雷','Late thunder'),
'journal.veilborne.hush.phase.thunder.text':('黑日沉回北方。地平线上立起一道笔直的白线，半分钟后雷声才到。','The black sun sinks back into the north. A straight white line stands on the horizon, and its thunder arrives half a minute later.'),
'journal.veilborne.hush.phase.return':('复声','Return'),
'journal.veilborne.hush.phase.return.text':('先是一只鸟，再是脚下的草；颜色从你身边向外回来，最后才是天空。','First a bird, then the grass underfoot; colour comes back outward from where you stand, and the sky last of all.'),
'journal.veilborne.hush.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.hush.procession':('无声行列','The silent procession'),
'journal.veilborne.hush.procession.text':('一列旧时代打扮的旅人沿着地面走过：提灯的、打伞的、提着空鸟笼的孩子、拄杖的老人、提箱子的高个子。他们的嘴在动，却没有声音。对着空处举起灯，他们会停下，把灯举起来回应你。','A file of travellers in the clothes of another age walks the land: a lantern-bearer, a woman under an umbrella, a child with an empty birdcage, an old man with a stick, a tall man with a suitcase. Their mouths move; nothing is heard. Raise a light and they stop, and their lantern goes up in answer.'),
'journal.veilborne.hush.knock':('敲门的空位','The knock at the door'),
'journal.veilborne.hush.knock.text':('你在房子附近时，门外会站着一件没有人的长外套，隔一会儿轻敲三下。打开门，对它举起灯：它会鞠躬，在门槛上留下白昼蜡，然后像被橡皮擦去一样消失。它从不自己开门。','When you are near a house, a long coat with nobody in it stands outside the door and every so often knocks three times. Open the door and raise a light to it: it bows, leaves daylight wax on the threshold, and is rubbed out like a pencil drawing. It never opens the door itself.'),
'journal.veilborne.hush.bell':('唯一的声音','The only sound'),
'journal.veilborne.hush.bell.text':('远处还有一只铃在响，悬在树下、岩边，或什么也没有的空中。它每响一次，就有一圈颜色穿过灰色的大地。走到它跟前，它周围会恢复声音和颜色，成为一座有声的小岛。','Somewhere one bell is still ringing, hung under a tree, from a rock edge, or from nothing at all. Each time it rings, a ring of colour goes out through the grey. Walk up to it and sound and colour come back around it: a small island of sound.'),
'journal.veilborne.hush.head.keepsakes':('白昼余物','What the day leaves'),
'journal.veilborne.hush.wax':('白昼蜡','Daylight wax'),
'journal.veilborne.hush.wax.text':('空外套留在门槛上的一块蜡，苍白、微凉。无声行列里的孩子也会给你一块。','A pale, cool lump of wax the empty coat leaves on the threshold. The child in the procession gives you one too.'),
'journal.veilborne.hush.hollow':('空响铃','The hollow bell'),
'journal.veilborne.hush.hollow.text':('有声岛合上时落下的那只铃。在任意一场天幕异象中摇响，它会记下这一场的声音；之后在别处摇响，便把声音还给你。什么都没记下时，它响出的是一小段寂静。',"The bell that comes down when the island of sound closes. Ring it inside any veil weather and it keeps a little of that weather's sound; ring it anywhere afterwards and it gives the sound back. Holding nothing, it rings a moment of silence."),
'journal.veilborne.hush.feather':('罩中羽','Feather under glass'),
'journal.veilborne.hush.feather.text':('褪色之后悬在你身旁半空、一动不动的那根松鸦蓝羽，直到第一只鸟叫了才落下来。罩在玻璃里，它仍然一动不动；偶尔，里面会传出一声鸟鸣。','The jay’s blue feather that hung motionless in mid-air beside you once the colour had gone, and only fell when the first bird sang. Under its glass it still does not move; now and then something in there chirps.'),
'journal.veilborne.hush.head.crafting':('工艺','Crafting'),
'journal.veilborne.hush.clock':('在主世界露天举起，唤来一场完整的无声白昼。已有异象或头顶有遮挡时不会消耗。','Raise it under open Overworld sky to call a complete silent daylight. It is not consumed while another weather is under way or the sky is covered.'),
'journal.veilborne.hush.raise':('举灯','Raising a light'),
'journal.veilborne.hush.raise.text':('在寂静里，灯就是你的声音：手持火把、灯笼、萤石之类发光的东西，对着空处（不是方块）使用，便举灯闪一下。无声行列和敲门的空位都会回应；无声提灯照得更远，还能照出行列从不留下的脚印。',"In the silence a light is your voice: hold a torch, a lantern, glowstone or anything that glows, and use it at the open air (not at a block) to raise it. The procession and the empty coat both answer; a silent lantern reaches further, and shows the footprints the procession never leaves."),
'journal.veilborne.hush.head.records':('观测记录','Observations'),
'journal.veilborne.hush.head.notes':('守则','Field notes'),
'journal.veilborne.hush.note.0':('异象不会破坏建筑，也不会强行带走任何人。生物、红石、投射物和时间都照常运行；离开的只有声音和颜色。','The weather never breaks buildings or carries anyone off. Creatures, redstone, arrows and time all go on as usual; only sound and colour leave.'),
'journal.veilborne.hush.note.1':('敌对生物的声音、受伤声和字幕始终保留。若不想让世界失声或褪色，可在客户端设置中关闭。','Hostile mobs, hurt sounds and subtitles always stay. If you would rather the world did not go silent or grey, turn either off in the client settings.'),
'journal.veilborne.hush.note.2':('把静烛放在家里，它周围会安静一点、灰一点，像那一天留下了一小块。','Set a still candle down at home and the world near it is a little quieter and a little greyer, as if a piece of that day had stayed.'),
'journal.veilborne.hush.closing':('先是一只鸟恢复了叫声，再是脚下草地的沙沙声，最后天空恢复了颜色。','First a bird found its voice again, then the grass underfoot, and last the sky got back its colour.'),
}
strings.update(hush_strings)
dream_strings={
'tooltip.veilborne.dream_seed':('种在露天的泥土、草地或苔藓上，它会长成一棵小小的梦树。','Plant it in open-sky soil, grass or moss and it grows into a small dream tree.'),
'tooltip.veilborne.dream_full_seed':('饱满得发亮。种下去，长出的梦树更高，叶缘镶金。','So full it glows. Planted, it grows a taller dream tree with gold-edged leaves.'),
'tooltip.veilborne.dream_leaf':('那条金色大鱼身上落下的一片叶子。','A leaf that came off the great golden fish.'),
'tooltip.veilborne.dream_charm':('带在身上：坠落时像一片叶子那样缓缓飘下，每次消耗一点。','Carried: when you fall, you drift down like a leaf. Each fall uses a little of it.'),
'tooltip.veilborne.dream_flute':('露天吹响，消耗鹿鸣笛唤来一场完整的苍翠梦潮。','Blow it under open sky to consume it and call a complete Verdant Dream.'),
'message.veilborne.flute_blocked':('需要主世界的露天位置，且当前没有其他天幕异象。','Find open sky in the Overworld while no other veil weather is under way.'),
'message.veilborne.seed_hint':('梦种在头顶悬停。蹲下，或拿着花、树苗或一点光靠近，它会跟着你；把它带到泥土上。','A dream seed hovers overhead. Crouch, or come near holding a flower, a sapling or a light, and it follows you; bring it down to the soil.'),
'message.veilborne.enclosure':('藤影围了上来，身后留着一道出口。跟上亮起的光。','Vine shadows grow round you, a gap left behind you. Follow the lights as they come.'),
'weather.veilborne.dream':('苍翠梦潮','the Verdant Dream'),
'message.veilborne.record2.128':('观测记录 · 森林从云里垂了下来','Observation · The forest came down out of the clouds'),
'message.veilborne.record2.256':('观测记录 · 巨鹿从你身边走过','Observation · The great deer walked past you'),
'message.veilborne.record2.512':('观测记录 · 沿花路走到了梦树下','Observation · Followed the petal path to the dream tree'),
'message.veilborne.record2.1024':('观测记录 · 巨鹿低头看了你一眼','Observation · The deer lowered its head and looked at you'),
'message.veilborne.record2.2048':('观测记录 · 把一颗梦种引到了泥土上','Observation · Guided a dream seed down to the soil'),
'message.veilborne.record2.4096':('观测记录 · 把失路的叶鱼带回了天上','Observation · Led the lost leaf-fish back to the sky'),
'message.veilborne.record2.8192':('观测记录 · 走完了梦藤围场','Observation · Finished the vine enclosure'),
'message.veilborne.record2.16384':('观测记录 · 梦种落进了巨鹿的蹄印','Observation · A dream seed landed in the deer\'s hoofprint'),
'journal.veilborne.hint2.128':('在异象中亲眼看见倒悬的森林。','See the upside-down forest for yourself.'),
'journal.veilborne.hint2.256':('巨鹿经过时，站在露天处，离它的路不要太远。','When the deer passes, be under open sky and not too far from its road.'),
'journal.veilborne.hint2.512':('发光的花瓣会排成一条路，沿着它走到尽头。','Glowing petals lay out a path; follow it to its end.'),
'journal.veilborne.hint2.1024':('巨鹿在梦树旁停下时，你已经在树下安静地站了一会儿。','When the deer stops by the dream tree, have been standing quietly beneath it a while.'),
'journal.veilborne.hint2.2048':('落种时，把悬停的梦种引到露天的泥土上。','In the seedfall, lead a hovering seed down to open-sky soil.'),
'journal.veilborne.hint2.4096':('找到困在窗边的叶鱼，拿着花或花灯把它们引到开阔处。','Find the leaf-fish stuck at a window and lead them out into the open with a flower or a flower lamp.'),
'journal.veilborne.hint2.8192':('碰一碰游荡的梦种，在藤影合拢前追上五道光。','Touch the wandering seed, then catch its five lights before the vines close.'),
'journal.veilborne.dream.title':('苍翠梦潮','Verdant Dream'),
'journal.veilborne.dream.subtitle':('天幕观测手记','Field Journal of the Veil'),
'journal.veilborne.dream.epigraph':('云里长出了一片倒悬的森林。有一头鹿，托着整片林子，从树根上走了过去。','A forest grew upside down out of the clouds. A deer carrying a whole wood in its antlers walked by along its roots.'),
'journal.veilborne.dream.quiet':('天幕平静。留意云是否变得像绒毛一样软，边缘泛起绿色。','The veil is quiet. Watch for clouds gone soft as down, green at their edges.'),
'journal.veilborne.dream.head.phases':('异象纪要','How it unfolds'),
'journal.veilborne.dream.phase.omen':('绒云','Downy clouds'),
'journal.veilborne.dream.phase.omen.text':('云变得像绒毛一样软，边缘泛绿；种子的绒絮从天上慢慢落下来。','The clouds go soft as down and green at the edges; seed fluff drifts slowly down.'),
'journal.veilborne.dream.phase.roots':('垂根','Roots'),
'journal.veilborne.dream.phase.roots.text':('树根从云里垂下来，一直伸到山顶；树冠在它们上方一片片合拢。','Roots come down out of the clouds as far as the hilltops, and the canopy closes over them piece by piece.'),
'journal.veilborne.dream.phase.leaves':('叶海','Leaf-sea'),
'journal.veilborne.dream.phase.leaves.text':('倒悬的森林遮住整片天，叶影在地上流动。叶鱼成群游过，附近长出一棵发光的梦树。','The upside-down forest covers the sky and leaf-light moves over the land. Schools of leaf-fish swim past, and a glowing dream tree comes up nearby.'),
'journal.veilborne.dream.phase.deer':('鹿行','The deer'),
'journal.veilborne.dream.phase.deer.text':('一条树根铺成的路垂到地上。巨鹿沿着它走来，在梦树旁停下，低头看一看。','A road of roots comes down to the land. The great deer walks along it, stops by the dream tree and lowers its head to look.'),
'journal.veilborne.dream.phase.seedfall':('落种','Seedfall'),
'journal.veilborne.dream.phase.seedfall.text':('叶子一片片转成金色落下，露出枝脉；梦种从天上缓缓降下，悬在你头顶。','Leaves turn gold and fall one by one, baring the veins of the branches; dream seeds come slowly down and hover over you.'),
'journal.veilborne.dream.phase.return':('归云','Return'),
'journal.veilborne.dream.phase.return.text':('树根收回云里，梦树合上叶子，最后天上只剩一片普通的云。','The roots draw back into the clouds, the dream tree folds its leaves, and at last only an ordinary cloud is left.'),
'journal.veilborne.dream.head.visitors':('来客与奇遇','Visitors'),
'journal.veilborne.dream.deer':('托林巨鹿','The deer that carries a wood'),
'journal.veilborne.dream.deer.text':('它的身体几乎透明，鹿角上长着一整片小树林，林中还有灯。它的每一步落下，地上都开出一圈花影。梦树下的花路是给它走的：若你在树下安静地站着，它会低头看你一眼，从鹿角间落下一颗梦种。','Its body is almost clear, and in its antlers grows a whole small wood with lamps in it. Wherever a hoof comes down a ring of flower-shadow opens on the land. The petal path is for it: stand quietly under the dream tree and it lowers its head to look at you, and a dream seed falls from its antlers.'),
'journal.veilborne.dream.school':('失路叶鱼','The lost school'),
'journal.veilborne.dream.school.text':('一小群金色叶鱼游进了房子，挤在窗边出不去。拿着花、树苗或花灯靠近，它们会跟着你；把它们带到开阔的露天，它们会汇成一条金色大鱼游回天上，落下两片金叶。','A small school of golden leaf-fish swam into a house and is pressed against a window, unable to find the way out. Come near holding a flower, a sapling or a flower lamp and they follow you; bring them out under open sky and they join into one great golden fish that swims back up, letting fall two golden leaves.'),
'journal.veilborne.dream.wander':('游荡的梦种','The wandering seed'),
'journal.veilborne.dream.wander.text':('一颗梦种绕着农田或你身边慢慢游动，从不碰作物。碰一碰它，藤影会围着你长出一圈，身后留着出口；在藤影合拢前追上接连亮起的五道光，就能得到一颗饱满梦种。走出圈外就算放弃，什么也不会失去。','A dream seed drifts slowly round the crops or around you, never touching them. Touch it and vine shadows grow in a ring round you, a gap left behind; catch the five lights that come one after another before the vines close, and a full dream seed is yours. Walk out of the ring to give it up; nothing is lost by it.'),
'journal.veilborne.dream.head.keepsakes':('梦中余物','What the dream leaves'),
'journal.veilborne.dream.seed':('梦种','Dream seed'),
'journal.veilborne.dream.seed.text':('落种时每人会有三颗从天上降下。蹲下或拿着花靠近，它就跟你走；落到露天的泥土上，它会发芽，留给你一颗梦种。落进巨鹿的蹄印里，也许会不一样。','In the seedfall three come down for each person. Crouch, or come near with a flower, and one follows you; brought down to open-sky soil it sprouts, and leaves you a seed. Landed in one of the deer\'s hoofprints, it might come up differently.'),
'journal.veilborne.dream.leaf':('金叶','Golden leaf'),
'journal.veilborne.dream.leaf.text':('金色大鱼游回天上时落下的叶子，形状像一条带着尾鳍的小鱼。花灯要用它。','A leaf that falls from the great golden fish as it swims back to the sky, shaped like a small fish with a forked tail. The flower lamp needs it.'),
'journal.veilborne.dream.twig':('鹿角枝','Antler twig'),
'journal.veilborne.dream.twig.text':('树根收回时，巨鹿的鹿角上落下一截小枝，枝头还长着一棵小树。挂在墙上，夜里会有萤火来。','As the roots draw back, a small twig falls from the deer\'s antlers with a tiny tree still growing on it. Hang it on a wall and fireflies come to it at night.'),
'journal.veilborne.dream.head.crafting':('工艺','Crafting'),
'journal.veilborne.dream.flute':('在主世界露天吹响，唤来一场完整的苍翠梦潮。已有异象或头顶有遮挡时不会消耗。','Blow it under open Overworld sky to call a complete verdant dream. It is not consumed while another weather is under way or the sky is covered.'),
'journal.veilborne.dream.full':('饱满梦种','Full dream seed'),
'journal.veilborne.dream.full.text':('梦藤围场的奖励，偶尔也从巨鹿的蹄印里长出来。种下后长成的梦树更高，叶缘镶金，有的开着蜜色的花。梦树不会蔓延，也不会掉叶。','The reward of the vine enclosure, and now and then what comes up in a hoofprint. It grows a taller dream tree with gold-edged leaves, some in honey-coloured flower. Dream trees never spread and never shed.'),
'journal.veilborne.dream.head.records':('观测记录','Observations'),
'journal.veilborne.dream.head.notes':('守则','Field notes'),
'journal.veilborne.dream.note.0':('森林、树根、梦树与花路都只是天上的景象，不会放下真的方块，也不会碰你的作物。只有你亲手种下的梦种会长成真的树。','The forest, the roots, the dream tree and the petal path are sights in the sky; they place no real blocks and never touch your crops. Only a seed you plant yourself grows a real tree.'),
'journal.veilborne.dream.note.1':('叶影、叶鱼和地上的花纹都可以在客户端设置中关闭。花灯可以随时点一下，把它的小鱼群收回或放出来。','The leaf-light, the leaf-fish and the flower patterns on the land can each be turned off in the client settings. Use a flower lamp any time to call its little school in or let it out.'),
'journal.veilborne.dream.note.2':('带着漂叶坠，从高处落下时会像叶子一样慢慢飘下；它会一点点磨损。','Carry a drifting leaf charm and a fall from a height becomes a slow drift, like a leaf; it wears a little each time.'),
'journal.veilborne.dream.closing':('最后一片叶子落下以后，天上只剩一片普通的云。可你记得，它曾经是一片森林。','When the last leaf had fallen only an ordinary cloud was left in the sky. But you remember it was once a forest.'),
}
strings.update(dream_strings)
# What the weathers leave behind (1.7.0), merged last so that it can reword the entries it changes.
strings.update(legacy_text.STRINGS)
for key,(cn,eng) in strings.items():zh[key]=cn;en[key]=eng
for key,cn,eng in [('omen','前兆 · 水滴开始逆流','Omen · The first rising drops'),('opening','开幕 · 星海正在张开','Opening · The sea unfolds'),('flood','潮盛 · 寻找抵岸的来客','High tide · Visitors approach'),('whale','鲸游 · 古鲸越过天幕','Whalepassage · Look to the sky'),('afterglow','余辉 · 留意落下的光鳞','Afterglow · Follow the falling scale'),('ebb','退潮 · 海正在回到远方','Ebb · The sea returns beyond'),('quiet','天幕平静','The veil is quiet')]:zh['phase.veilborne.'+key]=cn;en['phase.veilborne.'+key]=eng
for key,cn,eng in [('omen','镜兆 · 静水照出了晴空','Omen · Still water shows a clear sky'),('silver','覆镜 · 天空正在变成镜子','Silvering · The sky is turning to glass'),('reflect','倒影 · 抬头，找多出来的东西','Reflection · Look up for what is extra'),('pilgrim','巡礼 · 巡礼者正走过镜中','Pilgrimage · The pilgrim walks the mirror'),('crack','裂镜 · 裂纹正在蔓延','Cracking · The cracks are spreading'),('clear','归天 · 镜子正在落下','Clearing · The mirror is falling')]:zh['phase.veilborne.mirror.'+key]=cn;en['phase.veilborne.mirror.'+key]=eng
for key,cn,eng in [('halo','月晕 · 月亮戴上了光环','Halo · A ring around the moon'),('crack','裂壳 · 月壳正在开裂','Cracking · The shell is splitting'),('bloom','花潮 · 花影沿地面铺开','Bloom · Flowers spread across the land'),('molt','蜕月 · 仰望，辨认真月','Molt · Look up; find the true moon'),('farewell','送月 · 最后的灯放在了路边','Farewell · A lantern by the road'),('close','合壳 · 花影一圈圈闭合','Closing · The flowers close ring by ring')]:zh['phase.veilborne.moon.'+key]=cn;en['phase.veilborne.moon.'+key]=eng
THREAD_PHASES=[('omen','引线 · 红线挂上了高处','Threading · Red threads hang from high places'),('tear','裂天 · 天幕正在撕开','Tearing · The veil is tearing open'),
    ('needle','落针 · 留意地上的符环','The needle · Watch the ground for warning rings'),('sew','缝世 · 金针落进了大地','Sewing · The needle comes down to the land'),
    ('knot','收线 · 去找未缝合的口袋','Knotting · Look for the unsewn pocket'),('fade','余线 · 缝线正在变成星带','Remnant · The seam becomes a band of stars')]
for key,cn,eng in THREAD_PHASES:zh['phase.veilborne.thread.'+key]=cn;en['phase.veilborne.thread.'+key]=eng
HUSH_PHASES=[('omen','失声 · 声音一层层退去','Hush · Sound is going, layer by layer'),('fade','褪色 · 灰色从地平线漫来','Fading · The grey is coming in from the horizon'),
    ('sun','黑日 · 抬头看北方','Black sun · Look to the north'),('eclipse','蚀 · 黑日盖住了那轮白色','Eclipse · The black sun covers the pale disc'),
    ('thunder','迟雷 · 留意地平线','Late thunder · Watch the horizon'),('return','复声 · 听，第一只鸟','Return · Listen: the first bird')]
for key,cn,eng in HUSH_PHASES:zh['phase.veilborne.hush.'+key]=cn;en['phase.veilborne.hush.'+key]=eng
DREAM_PHASES=[('omen','绒云 · 云变得像绒毛一样软','Downy clouds · The clouds are going soft'),('roots','垂根 · 树根正从云里垂下','Roots · Roots are coming down from the clouds'),
    ('leaves','叶海 · 找一找花路和梦树','Leaf-sea · Look for the petal path and the dream tree'),('deer','鹿行 · 在梦树下安静地等','The deer · Wait quietly under the dream tree'),
    ('seedfall','落种 · 把梦种引到泥土上','Seedfall · Lead the dream seeds down to the soil'),('return','归云 · 森林正在回到云里','Return · The forest is going back into the clouds')]
for key,cn,eng in DREAM_PHASES:zh['phase.veilborne.dream.'+key]=cn;en['phase.veilborne.dream.'+key]=eng
SUBTITLES=[('sea','星海潮声','Celestial tide flows'),('whale','远空鲸鸣','Celestial whale calls'),('chime','星屑轻鸣','Starlight chimes'),('vortex','浮潮即将升起','A floating tide gathers'),('ebb','星海渐远','The sea recedes'),
           ('moon_drone','远空弦声','Faint strings in the sky'),('moon_omen','月晕低鸣',"The moon's halo hums"),('moon_crack','月壳开裂',"The moon's shell cracks"),('moon_molt','月壳剥落','The moon sheds its shell'),
           ('moon_bell','送月铃声',"A moon bearer's bell"),('moon_scatter','花瓣散开','Petals scatter'),('moon_close','月壳合拢','The shell closes')]+[(f'lullaby_{i}','不完整的摇篮曲','A fragment of a lullaby') for i in range(6)]+[
           ('mirror_drone','镜天低鸣','The mirror sky hums'),('mirror_omen','倒放的钟声','A bell rings backwards'),('mirror_silver','银霜蔓延','Silver frost spreads'),('mirror_crack','镜面开裂','The sky mirror cracks'),
           ('mirror_step','远处的巨大脚步','A vast footstep far away'),('mirror_door','镜门开启','The mirror door opens'),('mirror_whine','镜兽高鸣','Glass beasts ring'),('mirror_burst','裂片炸开','A shard bursts'),
           ('mirror_shatter','镜雨落下','The mirror falls as glass rain'),('mirror_chime','清亮的和弦','A clear chord'),('mirror_rewind','倒流的风声','A rewinding hush')]+[(f'hymn_{i}','巡礼歌','A pilgrim\'s chant') for i in range(5)]+[
           ('thread_drone','远空的弦与合唱','Strings and a choir far overhead'),('thread_omen','一根弦被拨响','A single string is plucked'),('thread_tear','天幕撕裂','The veil tears'),
           ('thread_loom','远处沉重的织机声','A heavy loom, far away'),('thread_pierce','金针穿过天幕','The needle goes through the sky'),('thread_warn','线越收越紧','A thread winds tighter'),
           ('thread_strike','金针落地','The needle strikes the land'),('thread_tug','天上的线被拉紧','The thread in the sky is pulled tight'),('thread_snap','线被剪断','A thread is cut'),
           ('thread_knot','线结收紧','A knot is drawn tight'),('thread_walker','黄铜的足音','Brass footsteps'),('thread_spirit','线灵低语','A thread spirit whispers'),
           ('thread_anchor','调律锚嗡鸣','A tuning anchor hums'),('thread_chime','线头轻响','A thread end chimes'),('thread_fade','缝线冷却','The seam cools'),
           ('thread_pocket','口袋里的旧时光','An old room inside the pocket')]+[(f'weave_{i}','织歌','A weaving song') for i in range(5)]+[
           ('hush_drone','寂静在耳中低鸣','Silence rings in your ears'),('hush_omen','万物吸了一口气','Everything draws a breath'),('hush_fade','声音滑向远处','A sound slides away'),
           ('hush_knock','有人轻轻敲门','Someone knocks softly'),('hush_bell','唯一的铃声','The only bell rings'),('hush_heart','一声心跳','A single heartbeat'),
           ('hush_ting','一声轻响','A faint ting'),('hush_thunder','迟来的雷声','Thunder, late'),('hush_bird','一只鸟在唱','A bird sings'),
           ('hush_chirp','一声鸟鸣','A bird chirps'),('hush_return','声音回来了','Sound returns'),('hush_erase','有什么被擦去了','Something is rubbed out'),
           ('hush_whisper','（无声的交谈）','(A conversation without sound)'),('hush_hollow','一阵空响','A hollow ring'),('hush_answer','提灯回应','A lantern answers')]+[
           (f'harmony_{i}','几乎听不见的和声','A harmony almost too faint to hear') for i in range(4)]+[
           ('dream_drone','头顶的森林在风里低吟','The forest overhead murmurs in the wind'),('dream_hum','树根在远处低鸣','Roots hum far above'),
           ('dream_omen','云轻轻呼出一口气','The clouds breathe out'),('dream_root','树根在云中生长','Roots grow in the clouds'),
           ('dream_rustle','叶鱼群游过','A school of leaf-fish passes'),('dream_bloom','花开了','Flowers open'),
           ('dream_step','巨鹿的脚步','The deer\'s footfall'),('dream_call','鹿鸣','The deer calls'),('dream_glance','巨鹿看着你','The deer looks at you'),
           ('dream_sprout','一株新芽','A sprout opens'),('dream_chime','一声清响','A clear chime'),('dream_vine','藤蔓伸展','Vines creep'),
           ('dream_fish','金色大鱼游回天上','The golden fish swims up'),('dream_fall','梦种落下','Dream seeds fall'),('dream_return','森林回到云里','The forest withdraws'),
           ('dream_bird','林中一只鸟','A bird in the trees'),('dream_drip','远处的水滴','Water drips somewhere')]+[
           (f'dream_reed_{i}','簧管短句','A reed phrase') for i in range(3)]+[(f'dream_pluck_{i}','拨弦短句','A plucked phrase') for i in range(3)]
for key,cn,eng in SUBTITLES:zh['subtitles.veilborne.'+key]=cn;en['subtitles.veilborne.'+key]=eng
assert zh.keys()==en.keys()
json_file(ASSETS/'lang/zh_cn.json',zh);json_file(ASSETS/'lang/en_us.json',en)

def shaped(name,pattern,keys,result,count=1):
    json_file(ROOT/'data/veilborne/recipe'/f'{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'id':'veilborne:'+result,'count':count}})
shaped('field_journal',[' A ',' B '],{'A':'minecraft:amethyst_shard','B':'minecraft:book'},'field_journal')
shaped('whale_mantle',['S S','PWP',' P '],{'S':'veilborne:whale_scale','P':'minecraft:phantom_membrane','W':'minecraft:white_wool'},'whale_mantle')
shaped('tide_attuner',[' A ','ECE',' G '],{'A':'minecraft:amethyst_shard','E':'minecraft:ender_pearl','C':'minecraft:nautilus_shell','G':'minecraft:gold_ingot'},'tide_attuner')
shaped('moon_dial',[' A ','ICI',' I '],{'A':'minecraft:amethyst_shard','I':'minecraft:iron_nugget','C':'minecraft:compass'},'moon_dial')
shaped('moon_gauze',[' S ','TDT',' T '],{'S':'veilborne:moon_shard','T':'minecraft:string','D':'veilborne:moon_dew'},'moon_gauze')
shaped('silver_lantern',[' C ','SDS',' S '],{'C':'minecraft:chain','S':'veilborne:moon_shard','D':'veilborne:moon_dew'},'silver_lantern')
shaped('moonward_bell',[' A ','QKQ',' G '],{'A':'minecraft:amethyst_shard','Q':'minecraft:quartz','K':'minecraft:clock','G':'minecraft:gold_ingot'},'moonward_bell')
shaped('observation_prism',[' A ','GCG',' C '],{'A':'minecraft:amethyst_shard','G':'minecraft:glass_pane','C':'minecraft:copper_ingot'},'observation_prism')
shaped('sky_mirror',[' A ','CGC',' K '],{'A':'minecraft:amethyst_shard','C':'minecraft:copper_ingot','G':'minecraft:glass','K':'minecraft:gold_ingot'},'sky_mirror')
shaped('echo_mirror',[' S ','SGS',' C '],{'S':'veilborne:mirror_sand','G':'minecraft:glass_pane','C':'minecraft:copper_ingot'},'echo_mirror')
shaped('mirror_lantern',[' C ','SLS',' C '],{'C':'minecraft:copper_ingot','S':'veilborne:mirror_sand','L':'minecraft:lantern'},'mirror_lantern')
shaped('heaven_needle',[' A ','RGR',' G '],{'A':'minecraft:amethyst_shard','R':'minecraft:red_dye','G':'minecraft:gold_ingot'},'heaven_needle')
shaped('tuning_anchor',[' C ','TST',' I '],{'C':'minecraft:copper_ingot','T':'veilborne:scarlet_thread','S':'veilborne:seam_silk','I':'minecraft:iron_ingot'},'tuning_anchor')
shaped('binding_charm',[' G ','TST',' T '],{'G':'minecraft:gold_nugget','T':'veilborne:scarlet_thread','S':'veilborne:seam_silk'},'binding_charm')
shaped('loom_lantern',[' S ','TLT',' G '],{'S':'veilborne:seam_silk','T':'veilborne:scarlet_thread','L':'minecraft:lantern','G':'minecraft:gold_ingot'},'loom_lantern')
shaped('stopped_clock',[' A ','WCW',' G '],{'A':'minecraft:amethyst_shard','W':'minecraft:white_wool','C':'minecraft:clock','G':'minecraft:gold_ingot'},'stopped_clock')
shaped('silent_lantern',[' I ','WLW',' I '],{'I':'minecraft:iron_nugget','W':'veilborne:daylight_wax','L':'minecraft:lantern'},'silent_lantern')
shaped('still_candle',[' S ',' W ',' W '],{'S':'minecraft:string','W':'veilborne:daylight_wax'},'still_candle')
shaped('flower_lamp',['GTG',' P '],{'G':'veilborne:golden_leaf','T':'minecraft:torch','P':'minecraft:lily_pad'},'flower_lamp',2)
shaped('drifting_leaf',[' S ',' N ',' L '],{'S':'minecraft:string','N':'minecraft:gold_nugget','L':'veilborne:dream_leaves'},'drifting_leaf')
shaped('deer_flute',[' A ','MBM',' G '],{'A':'minecraft:amethyst_shard','M':'minecraft:moss_block','B':'minecraft:bamboo','G':'minecraft:gold_ingot'},'deer_flute')

# Streamed loops, and how far each sound carries.
SOUND_RANGE={'whale':160,'moon_bell':40,'moon_scatter':24,'mirror_whine':24,'mirror_burst':32,'mirror_door':24,'mirror_chime':32,'mirror_rewind':24,
    'thread_warn':40,'thread_strike':64,'thread_walker':16,'thread_spirit':20,'thread_anchor':24,'thread_chime':24,'thread_knot':32,'thread_snap':32,'thread_pocket':16,
    'hush_knock':28,'hush_bell':72,'hush_erase':16,'hush_whisper':22,'hush_chirp':16,'hush_answer':28,
    'dream_step':220,'dream_call':240,'dream_root':200,'dream_bird':40,'dream_drip':28,'dream_rustle':40,'dream_bloom':48,'dream_sprout':24,
    'dream_chime':40,'dream_vine':24,'dream_fish':64,'dream_glance':48}
json_file(ASSETS/'sounds.json',{k:{'subtitle':'subtitles.veilborne.'+k,'sounds':[{'name':'veilborne:'+k,'stream':k in ('sea','moon_drone','mirror_drone','thread_drone','hush_drone','dream_drone','dream_hum'),'attenuation_distance':SOUND_RANGE.get(k,32)}]} for k,_,_ in SUBTITLES})

if '--dream-sound' in sys.argv:
    dream_sound.generate()
    print('Generated textures, models, language files, recipes and the verdant-dream soundscape; other audio untouched.')
    sys.exit(0)
if '--hush-sound' in sys.argv:
    hush_sound.generate()
    print('Generated textures, models, language files, recipes and the silent-daylight soundscape; other audio untouched.')
    sys.exit(0)
if '--thread-sound' in sys.argv:
    thread_sound.generate()
    print('Generated textures, models, language files, recipes and the scarlet-seam soundscape; other audio untouched.')
    sys.exit(0)
if '--mirror-sound' in sys.argv:
    mirror_sound.generate()
    print('Generated textures, models, language files, recipes and the mirror soundscape; other audio untouched.')
    sys.exit(0)
if '--moon-sound' in sys.argv:
    moon_sound.generate()
    print('Generated textures, models, language files, recipes and the moon soundscape; sea audio untouched.')
    sys.exit(0)
if '--no-sound' in sys.argv:
    print('Generated textures, models, language files and recipes; sound skipped.')
    sys.exit(0)

RATE=24000
rng=np.random.default_rng(8023)
sounds=ASSETS/'sounds';sounds.mkdir(parents=True,exist_ok=True)
def write_sound(name,signal,loop=False):
    if not loop:
        n=min(int(RATE*.25),len(signal)//4);signal[:n]*=np.linspace(0,1,n);signal[-n:]*=np.linspace(1,0,n)
    signal=signal/(max(.001,np.max(np.abs(signal))))*.66
    sf.write(sounds/(name+'.ogg'),signal.astype(np.float32),RATE,format='OGG',subtype='VORBIS')
def reverb(signal):
    out=signal.copy()
    for delay,gain in [(.23,.22),(.57,.17),(1.1,.12),(1.73,.09)]:
        n=int(delay*RATE);out[n:]+=signal[:-n]*gain
    return out
# Periodic Fourier noise with an exact seam and gentle harmonic movement.
n=RATE*16;t=np.arange(n)/RATE;freq=np.fft.rfftfreq(n,1/RATE)
spec=(rng.normal(size=len(freq))+1j*rng.normal(size=len(freq)))/np.maximum(freq,25)**.75
spec[(freq<35)|(freq>2400)]=0
noise=np.fft.irfft(spec,n);noise/=np.std(noise)
sea=noise*.065*(.8+.2*np.sin(2*np.pi*t/16))
for f,amp in [(55,.19),(82.5,.09),(110,.04),(164.8125,.025),(220,.018)]:sea+=amp*np.sin(2*np.pi*f*t+np.sin(2*np.pi*t/16)*.16)
write_sound('sea',sea,True)
t=np.arange(RATE*11)/RATE
f=100-45*(t/11)+15*np.sin(t*.5);phase=2*np.pi*np.cumsum(f)/RATE
env=np.sin(np.pi*np.clip(t/10,0,1))**1.5
whale_call=(np.sin(phase)*.6+np.sin(phase*2.003)*.2+np.sin(phase*3)*.08)*env
whale_call+=.09*np.sin(2*np.pi*(210*t+19*np.sin(t*.37)))*np.sin(np.pi*t/11)**3
write_sound('whale',reverb(whale_call))
t=np.arange(RATE*5)/RATE
chime=sum(np.sin(2*np.pi*f*t)*np.exp(-t*(1+i*.14))*.5/(i+1) for i,f in enumerate([523.25,783.99,1046.5,1567.98]))
write_sound('chime',reverb(chime))
t=np.arange(RATE*7)/RATE
vortex=np.sin(2*np.pi*(75*t+9*t*t))*.24*np.sin(np.pi*t/7)+np.sin(2*np.pi*440*t)*np.maximum(0,np.sin(t*4))*.065
write_sound('vortex',reverb(vortex))
t=np.arange(RATE*9)/RATE
ebb=sum(np.sin(2*np.pi*f*t)*.18*np.exp(-t*.37) for f in [110,164.81,220,329.63])
write_sound('ebb',reverb(ebb))
moon_sound.generate()
mirror_sound.generate()
thread_sound.generate()
hush_sound.generate()
dream_sound.generate()
print('Generated original assets:',len(list(ASSETS.rglob('*.png'))),'textures,',len(list(sounds.glob('*.ogg'))),'soundscapes')
