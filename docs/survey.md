# EvoWorld survey

Seeds 1-20, measured on commit 79fcb02 with uncommitted changes. Regenerate with `./gradlew survey`.

| Scenario | Ticks | Survived | Close calls | Lowest health | Mean final health | Mended | Ticks to mend | Rests | For health | Turn-backs | Dithering runs | Food/water trips | Abandoned | To nothing | Refused |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 2,880 | 20/20 | 3 | 98.1 | 100.0 | - | - | 0 | 0 | 296 | 0 | 644 | 69 | 3 | 110 |
| hourly | 2,000 | 20/20 | 20 | 76.0 | 99.6 | - | - | 0 | 0 | 46 | 0 | 2,492 | 23 | 2 | 107 |
| wounded | 2,880 | 20/20 | 5 | 30.0 | 100.0 | 20/20 | 864 | 0 | 0 | 294 | 0 | 610 | 58 | 0 | 106 |
| hungry-world | 2,880 | 20/20 | 15 | 12.0 | 99.7 | - | - | 0 | 0 | 275 | 0 | 925 | 129 | 46 | 108 |
| sparse | 2,880 | 20/20 | 15 | 46.7 | 99.9 | - | - | 0 | 0 | 276 | 0 | 849 | 194 | 116 | 112 |
| walled | 2,880 | 20/20 | 0 | 100.0 | 100.0 | - | - | 0 | 0 | 876 | 8 | 709 | 105 | 0 | 120 |

## A day in the life

Averaged over every seed and every simulated day each run lived.

| Scenario | Sleep h/day | Sleeps/day | Meals/day | Drinks/day | Free time | Chunks entered | Water walk h/day | Made | Fires | Fed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 9.4 | 1.20 | 1.5 | 3.2 | 50% | 22.8 | 0.86 | 0.9 | 8.5 | 0.1 |
| hourly | 9.4 | 1.07 | 1.8 | 2.9 | 23% | 24.0 | 2.09 | 0.8 | 5.2 | 0.1 |
| wounded | 9.4 | 1.20 | 1.5 | 3.3 | 51% | 23.0 | 0.81 | 1.0 | 8.7 | 0.1 |
| hungry-world | 9.4 | 1.21 | 2.3 | 4.2 | 45% | 19.8 | 1.60 | 0.9 | 8.5 | 0.0 |
| sparse | 9.4 | 1.21 | 1.4 | 2.5 | 48% | 23.7 | 1.40 | 0.5 | 5.1 | 0.0 |
| walled | 9.4 | 1.20 | 2.4 | 5.4 | 50% | 1.0 | 0.44 | 3.0 | 12.1 | 1.9 |

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

- **baseline**: DRINK 60, EAT 50
- **hourly**: DRINK 60, EAT 47
- **wounded**: DRINK 60, EAT 46
- **hungry-world**: DRINK 60, EAT 48
- **sparse**: DRINK 60, EAT 52
- **walled**: DRINK 60, EAT 60
