# Tunnel Cart

Fabric-мод для Minecraft **1.20.1**. Вагонетка-проходчик сама роет тоннель 3×3, кладёт энергорельсы, обшивает тоннель колотым глубинным сланцем и собирает руды. Совместима с модом High-Speed Rail (75 бпс).

## Требования
- Minecraft 1.20.1, Fabric Loader 0.15+
- Fabric API 0.92.2+1.20.1
- (по желанию) High-Speed Rail

## Крафты

Все рецепты бесформенные: порядок предметов не важен.

| Предмет | Ингредиенты |
|---|---|
| **Вагонетка-проходчик** | Вагонетка с сундуком + алмазная кирка + плавильная печь + блок редстоуна |
| **Расширение проходчика** (сиденье + склад) | Вагонетка + сундук + дубовые ступеньки |

## Как пользоваться
1. Поставь вагонетку-проходчик на **прямой горизонтальный рельс**. Она поедет туда, куда ты смотришь.
2. Нажми на неё **ПКМ** и положи в инвентарь:
   - энергорельсы,
   - колотый глубинный сланец,
   - (по желанию) блоки редстоуна, по одному на каждый 8-й рельс.
   Можно класть всё это в шалкеры, вагонетка берёт предметы изнутри.
3. Нажми кнопку **«Проложить путь»**. Кнопка **«Стоп»** останавливает вагонетку.
4. **Расширение:** поставь на рельс в радиусе 16 блоков от проходчика, и оно прицепится сзади.
   - ПКМ: сесть.
   - Shift+ПКМ: общий инвентарь на 54 слота.

## Что делает вагонетка
- Роет тоннель 3×3 и кладёт энергорельс на каждый блок.
- Оболочку 5×5 вокруг прохода заменяет на **колотый глубинный сланец** (обычный глубинный сланец не трогает), закрывает пещеры и жидкости.
- В области 5×5 добывает **руды, гравий и глину**.
- В вагонетку и шалкеры попадают только добытые ресурсы и колотый глубинный сланец. Остальное выпадает на пол.
- Останавливается и пишет над хотбаром, если закончились рельсы или сланец, впереди неразрушимый блок или не загружен чанк.

## Ограничения
- Тоннель только прямой и ровный (без поворотов и уклонов).
- Сундуки, спавнеры и бедрок не ломаются и не заменяются.
- В обычном камне (выше уровня 0) уходит около 16 колотого сланца на каждый блок тоннеля, так что запасай шалкеры.
- Выпавший мусор не исчезает мгновенно, при лагах его можно подбирать.

## 4.0

* **Redstone torches** – button "Torches: on/off" under the cart menu. When on, every 16th rail gets a redstone wall torch next to it (plus three extra torches after a turn: right after the turn and 16 and 32 blocks later); redstone blocks stay every 8th rail. Needs redstone torches in the cart.
* **By coordinates** – button "By coordinates" opens a menu with X / Y / Z (Y empty = current level). The route is: straight along the cart's heading → **one** turn → straight to the target → climb/descent at the very end.
  * The turn is a plain curved rail (keep a few plain rails in the cart). Before the turn and after it there are 10 slowing blocks (cobblestone) under the rails.
  * A climb is the last thing on the route: 10 slowing blocks before it, then 11 straight rails after it. A descent gets no slowing blocks, but also 11 straight rails after it.
  * Needs: both legs of a turn ≥ 11 blocks, the last leg ≥ height + 21 for a climb. Cobblestone must be in the cart for slowing blocks (it must NOT be listed in the High-Speed Rail config, so it stays slow).
* **Existing tracks** – when the way ahead crosses an already built track, the route climbs over it (ramp up, flat part, ramp down). If the space above is occupied it goes under it instead.

### 4.0.1
* Fixed stray blocks inside the tunnel at the turn.
* Slopes (and the rails next to them) now have 5 blocks of headroom.
* Torches every 16 rails + three acceleration torches after a turn.
* Speed is limited to 10 bps right before and on slopes (`SLOPE_SPEED` in `TunnelCartEntity`).

### 4.0.2
* Bypass is triggered only by powered rails (plain/other rails and everything behind the starting point are ignored).

### 4.0.3
* The "cannot bypass" message now shows the coordinates of the rail that triggered it.

### 4.0.4
* **Fixed: "cannot bypass the existing track" when an old track runs next to / along the new one.**
  * A foreign powered rail now counts as an obstacle only if the cart would really destroy it (inside the 3-wide bore, in the roof layer above it, or directly under our rail). Tracks that merely run 2+ blocks to the side, or 4+ blocks above/below, no longer trigger a bypass at all; rails in the outer shell are left untouched instead of being replaced by cobbled deepslate.
  * A parallel track that lies inside the bore is bypassed over its whole length (the platform is as long as the old track, not limited to 40 blocks). If the old track continues past the end of the platform, the platform is lengthened instead of trying to start a second bypass (which was impossible: no room for a ramp).
  * Another old track right behind the first one (inside the way back down) is covered by the same platform.
  * The ramp check no longer rejects every bypass because of rails 4 levels away; the under-bypass is now deep enough not to touch the support block of the old rails.
  * Columns that are not loaded yet are never treated as "checked": the cart waits ("waiting for chunks") instead of building into an unknown area, and re-checks them when they load.
  * Logic moved to `DetourPlanner` (pure geometry, no Minecraft calls except through a small `Terrain` interface).

### 4.0.5
* **Water at the start of a climb:** where the roof height changes (flat <-> slope) the cells beside the bore that were above the neighbour's roof were never sealed, so water/caves behind them flowed in. They are now sealed with cobbled deepslate (only air/liquid cells are touched).
* **The trailing (seat) cart no longer ends up inside the wall after a turn:** it is placed 1.5 blocks behind along the built path instead of along the original heading.
* **Torches for acceleration even with the torch button off:** one on the first rail after a turn plus two more 16 blocks apart; on every slope (first rail and every 8th) and on the first rail after a slope plus one 16 further. If the cart has no torches, a redstone block is used instead (never in a slowing zone).
* **A bypass over/under an old track is treated like a climb:** 10 slowing blocks (cobblestone) before its way up, and torches on the slopes (cobblestone must be in the cart).
* The planner looks 34 nodes ahead (was 26) so the slowing zone before a bypass is normally not built yet when the bypass is planned.

### 4.0.6
* After a descent to the target depth there are now 11 straight rails (same as after a climb); descent needs depth+11 blocks of leg.
* More torches for acceleration (always, button-independent): also on the last rail before a turn and on the last rail before every slope (climb, descent, bypass), besides those after a turn / on slopes / after slopes.

### 4.0.7
* Torch layout for acceleration (always placed, button-independent; redstone block if the cart has no torches):
  * turn: before it the last rail + two more 16 apart; after it the first rail + two more 16 apart;
  * slope (climb, descent, bypass over/under an old track): before it the last rail + two more 16 apart; on the slope every 16th rail; after it the first rail + three more 16 apart.

### 4.0.8
* The torch on the last rail before a turn is no longer removed when the turn block is built (the 3x3 bore of the turn used to clear it).
* Speed through a turn and its two slowing zones is capped at 7.5 blocks/s by the cart itself (independent of rail power and High-Speed Rail settings), so the cart cannot leave the curved rail at high speed.
