# Lives (Spigot plugin)

Build:  mvn clean package   ->  target/Lives.jar  -> drop in /plugins
Needs Java 17+ to build. Runs on Spigot/Paper 1.16 - latest.

## Rules
| Lives left | Max hearts | Name colour |
|-----------|-----------|-------------|
| 3 | 10 | Green  |
| 2 | 8  | Orange (gold) |
| 1 | 4  | Red    |
| 0 | banned | Black |

## Commands
/lives [check] [player]            - view lives
/lives set <player> <0-3>          - set lives (0 bans, raising from 0 unbans)   [lives.admin]
/lives add|remove <player> <n>                                                   [lives.admin]
/lives toggle [on|off]             - turn losing lives on death on/off           [lives.admin]
/lives give <player> <life|revive> [amount]                                      [lives.admin]
/lives revive <player>             - revive without an item                      [lives.admin]
/lives reload
/revive <player>                   - hold a Revive Token, revives a banned player [lives.revive]

## Items
Extra Life (Heart of the Sea)  - right-click: +1 life (max 3)
Revive Token (Nether Star)     - hold + /revive <player>: unbans them with 1 life (configurable)
Both are craftable (see config.yml / ItemManager.java) or obtainable with /lives give.
