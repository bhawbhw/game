# MoonTentacles

Smooth moon-coloured tentacles attached to the back of every player. Visible to everyone.

Target: Paper 26.2, Java 25.

## Build

Needs JDK 25 and Maven with internet access (to fetch paper-api from repo.papermc.io):

    mvn package

The jar is written to `target/MoonTentacles-1.0.0.jar`. Put it in your server's `plugins/` folder and restart.

## Commands

- `/tentacles [on|off|toggle]` switches your own tentacles (permission `moontentacles.use`, default everyone)
- `/tentacles <on|off|toggle> <player>` switches another player's (`moontentacles.admin`, default op)
- `/tentacles reload` reloads `config.yml` (`moontentacles.admin`)

A player's choice is stored on the player and survives restarts.

## How it works

Each tentacle is a chain of BlockDisplay entities. Every tick the curve is recomputed in the
player's body frame (so it follows your turning and sneaking) and each segment gets a new
transformation, which the client interpolates. The segments themselves only teleport with the player.
Moving makes the tentacles trail behind you.

Tentacles are removed while a player is dead, in spectator mode, invisible, swimming, gliding or sleeping.

## Tuning

Everything is in `config.yml`: number of tentacles (1-8), segments, length, thickness, sway,
inertia, glow and the moon palette. Entities per player = tentacles x segments (default 4 x 14 = 56).
Lower `segments` if you have many players online.

## Limits

- The shape is built from stretched blocks, so it is smooth in motion but not perfectly round.
  A truly round tube needs a custom model and a resource pack.
- Not Folia compatible.
