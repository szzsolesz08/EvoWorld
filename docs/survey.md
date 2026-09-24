# EvoWorld survey

Seeds 1-20, measured on commit dc2e096 with uncommitted changes. Regenerate with `./gradlew survey`.

| Scenario | Ticks | Survived | Close calls | Lowest health | Mean final health | Mended | Ticks to mend | Rests | For health | Turn-backs | Dithering runs | Food/water trips | Abandoned | To nothing | Refused |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 2,880 | 20/20 | 0 | 100.0 | 100.0 | - | - | 0 | 0 | 475 | 2 | 564 | 66 | 0 | 107 |
| hourly | 2,000 | 20/20 | 13 | 83.8 | 100.0 | - | - | 0 | 0 | 66 | 1 | 2,803 | 23 | 0 | 106 |
| wounded | 2,880 | 20/20 | 0 | 30.0 | 100.0 | 20/20 | 885 | 0 | 0 | 473 | 2 | 563 | 64 | 0 | 106 |
| hungry-world | 2,880 | 20/20 | 8 | 17.3 | 100.0 | - | - | 0 | 0 | 346 | 0 | 756 | 71 | 17 | 105 |
| sparse | 2,880 | 20/20 | 6 | 78.6 | 100.0 | - | - | 0 | 0 | 360 | 0 | 674 | 94 | 22 | 102 |
| walled | 2,880 | 20/20 | 0 | 100.0 | 100.0 | - | - | 0 | 0 | 906 | 0 | 411 | 18 | 0 | 120 |

## A day in the life

Averaged over every seed and every simulated day each run lived.

| Scenario | Sleep h/day | Sleeps/day | Meals/day | Drinks/day | Free time | Chunks entered | Water walk h/day | Made |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 8.8 | 1.15 | 1.5 | 3.2 | 51% | 22.3 | 0.87 | 0.9 |
| hourly | 8.6 | 1.07 | 1.9 | 2.9 | 25% | 27.8 | 2.37 | 0.6 |
| wounded | 8.8 | 1.15 | 1.5 | 3.2 | 51% | 22.3 | 0.88 | 0.9 |
| hungry-world | 8.9 | 1.16 | 2.3 | 4.4 | 46% | 20.2 | 1.55 | 0.8 |
| sparse | 8.8 | 1.15 | 1.4 | 2.8 | 50% | 21.8 | 1.37 | 0.7 |
| walled | 8.8 | 1.15 | 2.5 | 5.4 | 51% | 1.0 | 0.57 | 3.0 |

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

- **baseline**: DRINK 60, EAT 47
- **hourly**: DRINK 60, EAT 46
- **wounded**: DRINK 60, EAT 46
- **hungry-world**: DRINK 60, EAT 45
- **sparse**: DRINK 60, EAT 42
- **walled**: DRINK 60, EAT 60
