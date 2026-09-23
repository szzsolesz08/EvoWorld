# EvoWorld survey

Seeds 1-20, measured on commit 0c6cf4a with uncommitted changes. Regenerate with `./gradlew survey`.

| Scenario | Ticks | Survived | Close calls | Lowest health | Mean final health | Mended | Ticks to mend | Rests | For health | Turn-backs | Dithering runs | Food/water trips | Abandoned | To nothing | Refused |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 2,880 | 20/20 | 0 | 100.0 | 100.0 | - | - | 0 | 0 | 502 | 0 | 978 | 96 | 5 | 94 |
| hourly | 2,000 | 20/20 | 12 | 84.4 | 100.0 | - | - | 0 | 0 | 101 | 0 | 3,336 | 19 | 0 | 88 |
| wounded | 2,880 | 20/20 | 0 | 30.0 | 100.0 | 20/20 | 864 | 0 | 0 | 506 | 0 | 969 | 90 | 5 | 96 |
| hungry-world | 2,880 | 20/20 | 13 | 44.0 | 100.0 | - | - | 0 | 0 | 392 | 0 | 1,231 | 106 | 18 | 95 |
| sparse | 2,880 | 20/20 | 6 | 78.6 | 100.0 | - | - | 0 | 0 | 415 | 0 | 931 | 118 | 27 | 93 |
| walled | 2,880 | 20/20 | 0 | 100.0 | 100.0 | - | - | 0 | 0 | 627 | 0 | 1,527 | 103 | 0 | 102 |

## A day in the life

Averaged over every seed and every simulated day each run lived.

| Scenario | Sleep h/day | Sleeps/day | Meals/day | Drinks/day | Free time | Chunks entered |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 8.8 | 1.15 | 1.5 | 2.1 | 47% | 21.2 |
| hourly | 8.7 | 1.08 | 1.8 | 2.6 | 23% | 25.5 |
| wounded | 8.8 | 1.15 | 1.5 | 2.1 | 47% | 21.2 |
| hungry-world | 8.8 | 1.15 | 2.3 | 2.7 | 41% | 19.4 |
| sparse | 8.8 | 1.15 | 1.4 | 1.9 | 47% | 22.5 |
| walled | 8.8 | 1.15 | 2.5 | 3.8 | 40% | 1.0 |

## Scenarios

- **baseline**: the default run: 10-minute ticks, 20 days.
- **hourly**: 60-minute ticks, 2,000 of them (83 days).
- **wounded**: starts at health 30, fed and rested.
- **hungry-world**: hunger and thirst rise 1.5 times as fast.
- **sparse**: hard country: chunks half as rich, and more variable.
- **walled**: a finite 16x16 world, generated whole.

## Deaths

None.

## Refused actions

- **baseline**: DRINK 60, EAT 34
- **hourly**: DRINK 60, EAT 28
- **wounded**: DRINK 60, EAT 36
- **hungry-world**: DRINK 60, EAT 35
- **sparse**: DRINK 60, EAT 33
- **walled**: DRINK 60, EAT 42
