# NPC interface decisions

## Confirmed scope

The screens are a standalone simulation, not a mod. Rowan represents one persistent owner-bound companion. Jobs can be reordered, paused, stopped or recalled; gathering trips are simulated, return partial resources on recall, continue when dismissed and pause on owner logout. No undo is offered. Settlement layout approval precedes individual building approval; all supplies must be available before an individual building starts. Drafts can be revised, moved, rotated and swapped before approval. Shared designs require admin review; schematic imports are admin-installed. The owner supplies their own client-side key; no key is entered or stored in this prototype.

## Adopt for prototype

- Keep queue state and selected screen while navigating during a session; no persistence or network is implied.
- Show approval gates explicitly: layout, individual design and supplies. Any rotation, move or material change invalidates the individual approval. Ask-player and simulated gather are alternatives for a shortage, not instant material creation.
- Use a manual simulation step for trip progress so the example can be reviewed without waiting. Pausing prevents advancement. Recall, cancellation or switching away from a trip returns its earned logs once; switching or reordering leaves at most one job in progress. Oak plank processing uses an explicitly illustrative rate. Stone bricks are supplied by the player in this demo; no stone-brick recipe or machine integration is simulated.
- Catalog selections show distinct schematic silhouettes. Position, orientation, material and example revisions visibly update the ghost; text revisions use a fixed doorway illustration rather than interpreting the request. Changing material resets the illustrative finished-supply count.
- Show discovered recipes separately from verified adapters. Reserve approved-job inputs, require permission for unfamiliar machines and storage, and expose power/fluid blockers. Machine recipes require actual stations and suitable adapters; portable crafting cannot bypass these requirements. All compatibility values are examples, not claims.
- Use semantic buttons, form labels, text state indicators and native keyboard focus. Square controls, compact panels and a simple block illustration evoke the game without copied assets.

## Defer

Mod loader/version, actual adapters, API integration, enforcement, machine detection, real resource timing, multiplayer permissions and server-side validation need implementation and testing. The proposed owner-client-key integration route is unverified. Sharing/import actions simulate review status only. No real blueprint file is imported or transmitted. No claims of supported packs or machines are made.

## Design tokens

Background `#252b28`; shell `#3c433e`; panel `#58615a`; inset `#303832`; border light `#899388`, dark `#1b211d`; text `#f3f2df`; muted and placeholder `#d1d8ca` at full opacity on input background `#252e28`; selection `#416747`; warning and focus outline `#f2ca77`; danger `#efad9e`. System sans typography; base 15px; 4/8/12/16/24px spacing; square corners; restrained inset bevels. On compact screens panels stack and navigation wraps; a shortage and next-action summary sits directly beneath the ghost preview.
