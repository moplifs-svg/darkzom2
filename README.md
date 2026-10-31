# Dark Zombies (Minecraft 1.20.1, Forge 47.4.10)

From shortly before sunrise (tick 21000) until just after sunset (tick 12500), zombies hide in dark places
(caves, deep shade) and stay there.

- A player standing in the light will NOT pull a zombie out.
- A zombie only comes out to fight if the player is in the dark and the whole route is dark,
  or the player is within ~2.5 blocks.
- If it is caught in light it walks to the nearest dark spot. If none is reachable, it acts like a normal zombie for 5 seconds, then tries again.
- Applies to Zombie, Husk, Zombie Villager (not Drowned / Zombified Piglin).

## Build
1. Install JDK 17.
2. In this folder run: `gradle wrapper --gradle-version 8.1.1` (needs Gradle installed), then `./gradlew build`
   (or open the folder in IntelliJ IDEA and let it import the Gradle project).
3. The jar appears in `build/libs/darkzombies-1.20.1-1.0.0.jar`. Put it in your `mods` folder.

Tuning constants are at the top of `HideInDarkGoal.java`.
