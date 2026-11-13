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
  * A climb is the last thing on the route: 10 slowing blocks before it, then 11 straight rails after it. A descent gets no slowing blocks.
  * Needs: both legs of a turn ≥ 11 blocks, the last leg ≥ height + 21 for a climb. Cobblestone must be in the cart for slowing blocks (it must NOT be listed in the High-Speed Rail config, so it stays slow).
* **Existing tracks** – when the way ahead crosses an already built track, the route climbs over it (ramp up, flat part, ramp down). If the space above is occupied it goes under it instead.

### 4.0.1
* Fixed stray blocks inside the tunnel at the turn.
* Slopes (and the rails next to them) now have 5 blocks of headroom.
* Torches every 16 rails + three acceleration torches after a turn.
* Speed is limited to 10 bps right before and on slopes (`SLOPE_SPEED` in `TunnelCartEntity`).

### 4.0.2
* Bypass is triggered only by powered rails (plain/other rails and everything behind the starting point are ignored).
