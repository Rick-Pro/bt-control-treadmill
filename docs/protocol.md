# PitPat BLE protocol (as used by Stride)

Compiled from open source work listed at the end. The frames below are what Stride sends and parses
(`core/.../protocol/PitPatProtocol.kt`). Ricardo's pad is a PitPat HY-BA06B1 that advertises as "BA06-73F6".
The `FBA0` layout was verified by others on PitPat-T01 family pads; the first device test
(docs/plan.md section 8) confirms it on this unit. A pad that advertised as "PitPat-T01" connected but had no
`FBA0` service. That is why Stride now reads the whole GATT table and also knows the two other layouts below.
The Diagnostics log always lists every service and characteristic, so the next pad that does not fit can be read
from a shared log.

## Discovery
- Advertised services: `0xFBA0` (and `0x1910` on some units).
- Name prefixes seen: `PitPat`, `BA0` (for example `BA06-73F6`). Stride matches on either the service or the name.
- The pad takes **one** connection. While the PitPat app is connected, the pad stops advertising and Stride
  cannot find it. Close the PitPat app first.

## GATT
| Role | UUID |
|---|---|
| Service | `0000fba0-0000-1000-8000-00805f9b34fb` |
| Commands (write with response) | `0000fba1-0000-1000-8000-00805f9b34fb` |
| Status (notify) | `0000fba2-0000-1000-8000-00805f9b34fb` |

Enable notifications by writing `01 00` to the CCCD (`0x2902`) of `FBA2`. Request a 517-byte MTU after
connecting so a whole status frame fits one notification; the `FrameReassembler` copes when it does not.
Request high connection priority: the pad drops idle links on the default interval.

## Transport variants
After service discovery Stride writes the whole GATT table to the Diagnostics log (one line per service and
characteristic, with the property bits) and picks the first layout the pad offers, in this order. The log says which
one was picked ("Transport: ..."), and Settings and the shared Diagnostics text show its name.

| # | Name shown | Where | Header on every write and notification | Keep-alive |
|---|---|---|---|---|
| 1 | PitPat | service `FBA0`: write `FBA1`, notify `FBA2` | none | `6A 05 FD F8 43` every 1000 ms |
| 2 | PitPat wrapped | `FF01` and `FF02` in whichever service holds both (`FFFF` on Ricardo's HY-BA06B1, confirmed from a log) | `4D <dir> <counter> <len>` | heartbeat after each notification, no timer |
| 3 | PitPat FFF | `FFF1` and `FFF2` in whichever service holds both (`FFF0` on pads seen) | none | like 1 |

For 2 and 3 the characteristic that can be written is the command one and the one that can notify is the status one,
so a pad that has the pair the other way round still works. A pair whose properties fit neither way is skipped and
detection goes on to the next layout. The frames inside are the same in all three (sections below). Pads whose
command characteristic only supports write without response are written without response; everything else is
written with response, as before.

### Wrapper (variant 2)
`4D <dir> <counter> <len>` then the normal frame, in both directions.
- `dir` is `00` on what Stride writes and **`04` on what the pad notifies** (seen on a HY-BA06B1). Stride sends `00` and
  accepts any value when reading, because an earlier version that insisted on `00` dropped every real status.
- `counter` is one byte. Stride's own starts at 0 on every connection, goes up by one on **every write** (heartbeats
  included) and wraps after 255. The pad numbers its notifications separately (`01`, `02` in the captures below); the
  two counters are never compared or copied.
- `len` is the size of the frame that follows (`17` for a control frame, `05` for the heartbeat, `34` or `2F` for the
  two status frames captured).
- A notification is cut to bytes 4 up to 4 + `len`, then goes through the same `FrameReassembler` and `parseStatus` as
  variant 1, so the status layout is identical (offsets below, counted from the frame, not the header). Any inner
  length of 31 or more is fine as long as the length byte, the XOR and the trailer `43` check out: it is not fixed at
  52. A chunk that continues a frame split over several notifications is never taken for a header.

```
start 1.0 km/h, counter 2 : 4D 00 02 17 6A 17 00 00 00 00 03 E8 01 00 50 00 04 00 00 00 0D BA 9D 76 EF 1A 43
heartbeat, counter 0      : 4D 00 00 05 6A 05 FD F8 43
```

Captured from Ricardo's pad (PitPat-T01 / HY-BA06B1, idle, belt stopped, imperial panel). Both are in
`PadCapturesTest` and decode to: STOPPED, speed 0, target 0, distance 0.009 km (9 m), steps 0, kcal 0, duration 0,
firmware 27, max speed 6.0 km/h.
```
56 bytes, inner frame 52 (0x34), with an 18-byte ASCII device id at inner offsets 32 to 49. The real id is replaced by the
placeholder `EXAMPLESERIAL0001` and the XOR byte (inner offset 50) is redone, because this repo is public (plan.md D24); every
other byte is the capture:
4D 04 01 34 68 34 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00
45 58 41 4D 50 4C 45 53 45 52 49 41 4C 30 30 30 31 00 44 43

51 bytes, inner frame 47 (0x2F), no device id:
4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00
00 00 00 00 00 00 00 00 00 00 00 00 00 16 43
```
Reading them: header `4D 04 <01|02> <34|2F>`; inner byte 0 `68`, byte 1 the length, speed and target `00 00`, distance
`00 00 00 09`, firmware `1B` (27) at offset 25, flags `82` at 26 (bit `80` imperial panel, run-state bits `18` clear =
stopped), max speed `17 70` = 6000 at 27 to 28, XOR `10` or `16`, trailer `43`.

### Heartbeat (variant 2)
The pad is answered with one heartbeat per received notification (valid status or not), instead of a timer. It is
skipped when a command is queued or being written, and when the keep-alive setting is off.

Difference from azmke's client: it holds a command until the next notification and sends it *in place of* that
heartbeat. Stride writes commands at once and only holds back the heartbeat, so STOP is never delayed by up to one
notification interval. If a pad turns out to need strict one-write-per-notification, this is the place to look.

### Speed scale (variants 2 and 3)
Speeds are raw units of 0.001 km/h in every frame above (`03 E8` = 1.0 km/h), which is also what azmke's client uses.
qdomyos-zwift is reported to use 0.01 km/h for some pads. Stride starts with thousandths and switches to hundredths,
logging `speed scale: hundredths (reason)`, when:
1. the pad's reported maximum speed, read as thousandths, is below 1.0 km/h (the lowest speed Stride sends). A 6.0 km/h
   pad in hundredths reads 0.6. This is checked on every status, so it normally switches before the first command; or
2. after a start or speed command, the target speed the pad echoes is about a tenth (0.07 to 0.14) of what was sent.
   A thousandths pad cannot trigger this: it only runs 1.0 to 6.0 km/h, so the echo is never below a sixth.

After the switch, speed fields in statuses are read with 100 units per km/h, and the speed field of start and speed
frames is divided by ten (checksum recomputed). An echo within 10% of the command logs `speed scale: thousandths`;
twenty statuses without a match log "not confirmed" and the scale stays. The 1.0 to 6.0 km/h clamp is applied before
a frame reaches this step and holds in both scales. Variant 1 never has its scale questioned.

Limit: if a pad reads our thousandths as hundredths and echoes the same raw value back, nothing looks wrong and the
belt would run ten times too fast (or the pad clamps to its own maximum). Rule 1 closes this when the pad reports a
maximum. The first device test of variants 2 or 3 should be done with the belt clear and the physical remote in hand.

## Framing
`[6A] [LEN] [payload...] [XOR] [43]`

- `LEN` is the total frame length including the start and end bytes.
- `XOR` is the XOR of every byte from index 1 (the LEN byte) up to and including the last payload byte.
- `43` ends every frame.

Kotlin: `for (i in 1 until frame.size - 2) value = value xor (frame[i].toInt() and 0xFF)`

## Control frame (23 bytes, written to FBA1)
| Offset | Meaning |
|---|---|
| 0 | `6A` |
| 1 | `17` (23) |
| 2 to 5 | `00` |
| 6 to 7 | speed, u16 big-endian, thousandths of km/h (`07 D0` = 2.0 km/h); `00 00` for pause and stop |
| 8 | mode: `01` for start, pause and stop; `05` for a speed change while running |
| 9 | incline, always `00` on this pad |
| 10 | weight in kg, `50` (80) |
| 11 | `00` |
| 12 | op: `04` start or set speed, `02` pause, `00` stop. Bit `08` set = imperial display units (left clear; the pad reports metric either way) |
| 13 to 20 | user id, `00 00 00 0D BA 9D 76 EF` |
| 21 | XOR checksum |
| 22 | `43` |

Example frames (checksums verified):
```
start 1.0 km/h : 6A 17 00 00 00 00 03 E8 01 00 50 00 04 00 00 00 0D BA 9D 76 EF 1A 43
start 2.0 km/h : 6A 17 00 00 00 00 07 D0 01 00 50 00 04 00 00 00 0D BA 9D 76 EF 26 43
speed 3.0 km/h : 6A 17 00 00 00 00 0B B8 05 00 50 00 04 00 00 00 0D BA 9D 76 EF 46 43
pause          : 6A 17 00 00 00 00 00 00 01 00 50 00 02 00 00 00 0D BA 9D 76 EF F7 43
stop           : 6A 17 00 00 00 00 00 00 01 00 50 00 00 00 00 00 0D BA 9D 76 EF F5 43
keep-alive     : 6A 05 FD F8 43
```
Resume after pause is the start frame again. Speed is km/h times 1000; Stride rounds to 0.1 km/h and clamps to
1.0 to 6.0 km/h, further capped by the max the pad reports in its status.

## Keep-alive
`6A 05 FD F8 43` every 1000 ms while nothing else is queued. Other implementations work without it, so it is a
setting (on by default) and never delays a real command.

## Beeper (experimental, decision D7)
Captured on a PitPat BA09-B, not yet verified on the BA06:
```
beeper on  : 6A 17 F1 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 E7 43
beeper off : 6A 17 F1 00 00 00 00 00 00 00 00 00 10 00 00 00 00 00 00 00 00 01 F7 43
```

## Status frame (52 bytes, or 47 on the wrapped pad; notified on FBA2 / FF02)
Offsets are 0-based, values big-endian. The length byte is `34` (52) or `2F` (47) and the last byte `43`. The 47-byte
form is the 52-byte one without the device id at 32 to 49. Anything of 31 bytes or more with a matching length byte,
XOR and trailer is accepted.
| Offset | Meaning |
|---|---|
| 0 | start byte (`68` in captures; not checked) |
| 1 | total length |
| 3 to 4 | current speed, u16, thousandths of km/h |
| 5 to 6 | target speed, u16, thousandths of km/h |
| 7 to 10 | distance, u32, metres |
| 11 | current incline |
| 12 | target incline (low 6 bits) |
| 13 | heart rate, bpm (0 here) |
| 14 to 17 | steps, u32. Some BA06-B1 firmware always sends 0, so Stride estimates steps from distance until a real count appears |
| 18 to 19 | kcal, u16 |
| 20 to 23 | duration, u32. Milliseconds when the firmware byte is above 19, otherwise seconds |
| 25 | firmware version (`1B` = 27 on Ricardo's pad). Shown as `fw=` in the Diagnostics log |
| 24 | session id. Went `CC` to `CD` when a walk started on Ricardo's pad (build 3 log): a counter of walks, not used by Stride |
| 26 | flags: `01` Wi-Fi connected (clear on Ricardo's pad); bit `02` unknown, set in every capture; bits `18` = run state (`18` starting countdown, `08` running, `10` paused, `00` stopped); `80` panel shows imperial. Seen: `82` stopped, `9A` starting, `8A` running |
| 27 to 28 | max speed, u16, thousandths of km/h |
| 29 | max incline (0 = none) |
| 30 | device type, low 5 bits (`05` on Ricardo's pad) |
| 32 to 49 (52-byte frame only) | the pad's ASCII id, read up to the first NUL (17 characters plus a NUL on Ricardo's pad, bytes 48 and 49 included). azmke reads only 32 to 47 as the serial and 48 and 49 as BLE model and brand, so the last character may belong to the model. In the log the 52-byte frame came first (header counter `01`) and every later status had 47 bytes. Stride shows it in Settings; in the Diagnostics log those bytes are shown as `**`, so a shared log does not carry it |
| 41 to 44 (47-byte frame) | an unknown u16 Stride calls **load**. Seen as `01 96` at 41 to 42 while running and `01 58` at 43 to 44 during the countdown, in the range `0122` to `0309` over a walk. It rises with belt load and decays after STOP. It is not steps. Stride logs it and never shows it. Which of the two offsets is the real one is open: the XOR cannot say, because it ignores where a byte sits. azmke's extended layout names them `real_rotate` (41 to 42) and `real_electricity_steps` (43 to 44) |
| 47 | bit `01` = beeper on (reported by one implementation, unverified) |
| 50 | XOR checksum of bytes 1 to 49 |
| 51 | `43` |

## Observed on Ricardo's pad (HY-BA06B1, firmware 27, build 3 log)
A real walk, start to STOP, over the wrapped transport. Times are from the app's own log.

| What | Seen |
|---|---|
| Start command sent | 32.2 s |
| First `STARTING` status (flags `9A`) | 32.3 s |
| First `RUNNING` status (flags `8A`) | 35.4 s |
| So the countdown (the pad beeps, the belt waits) lasts | about 3 s |
| After STOP | the pad reports `STOPPED` (flags `82`) **at once**, while the belt speed ramps from 1.9 km/h to 0 over about 3 s |
| Distance | metres (`00 00 00 09` = 9 m). A leftover 0.172 km from the previous walk showed before the start and reset to 0 on start |
| Duration | milliseconds (`00 00 07 D0` = 2 s) |
| Steps and kcal | stayed 0 for the whole walk, so on this pad both come from Stride |
| Heart rate, incline | 0 |
| Max speed | `17 70` = 6.0 km/h |
| Session id (byte 24) | `CC` then `CD` after the start |

The two frames below were captured in this log (both 51 bytes: header, then a 47-byte frame; both are in
`PadCapturesTest`).
```
STARTING, flags 9A, session CC, load 01 58 at 43 to 44:
4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 CC 1B 9A 17 70 00 05 00
00 00 00 00 00 00 00 00 00 00 00 01 58 59 43

RUNNING, flags 8A, session CD, speed 1.60, target 2.40, 1 m, 2000 ms, load 01 96 at 41 to 42:
4D 04 0A 2F 68 2F 00 06 40 09 60 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 07 D0 CD 1B 8A 17 70 00 05 00
00 00 00 00 00 00 00 00 00 01 96 00 00 7F 43
```
They decode to STARTING with speed 0 and RUNNING at 1.60 km/h (target 2.40, 0.001 km, 2 s).

### Which fields this BA06-B1 fills in (firmware 27)
From the captures and the build 3 log. "Zero" means the pad sends the field but always 0.

| Field | Offset | On this pad | Stride shows it |
|---|---|---|---|
| Speed, target speed | 3, 5 | filled in, thousandths of km/h | yes |
| Distance | 7 to 10 | filled in, metres; a leftover counter until the next start resets it | yes |
| Duration | 20 to 23 | filled in, milliseconds | yes |
| Run state and flags | 26 | filled in (`82`, `9A`, `8A` seen) | yes (Starting, Walking, Paused, Stopping) |
| Max speed | 27 to 28 | filled in, 6.0 km/h | yes, Settings, Treadmill card |
| Firmware | 25 | filled in, 27 | yes, Settings, Treadmill card |
| Device id | 32 to 49 | only in the 52-byte frame | yes, Settings, Treadmill card |
| Session id | 24 | filled in, changes per walk | log only |
| Device type | 30 | `05` | no |
| Load (unknown) | 41 to 42 or 43 to 44 | filled in, follows belt load | log only, as `load` |
| Steps | 14 to 17 | **zero** for the whole walk | Stride estimates them (step length from the profile) |
| Calories | 18 to 19 | **zero** for the whole walk | Stride estimates them (needs the profile) |
| Heart rate | 13 | **zero** (no sensor) | only if a pad sends a value above 0 |
| Incline, target incline | 11, 12 | **zero** (no incline) | only if a pad sends a value above 0 |
| Wi-Fi flag | 26, bit `01` | clear | no: the pad's Wi-Fi only talks to the PitPat cloud |

What this means for the app: the countdown screen runs for about 3 s and ends when the pad says `RUNNING`; a status that
says `STOPPED` with a speed above 0 is the belt still slowing down, not a stopped belt; Stride's distance and duration
need no scaling beyond metres and milliseconds; and calories and steps have to be estimated, because the pad sends 0.

## Quirks
- If the Bluetooth link drops while the belt moves, **the belt keeps running at the same speed**. The physical
  remote still works. Stride vibrates and shows a banner; it never pretends to have stopped the belt.
- One client at a time. Close the PitPat app.
- Split notifications: with the default 23-byte MTU a 52-byte status arrives in pieces. The reassembler uses the
  length byte and drops data older than 300 ms.
- No handshake or unlock is needed on the FBA0 transport. Other PitPat units use `FF01`/`FF02` (with the wrapper)
  or `FFF1`/`FFF2`; Stride now tries both, see Transport variants. They have not been tried on a real pad yet.

## Sources
- https://github.com/Franrodr10/pitpat-controller (MIT): Kotlin implementation, `docs/PROTOCOL.md`, tests. Ported.
- https://github.com/Razzolate99/pitpat-treadmill (no license): Python frame builder and parser, beeper frames. Facts only.
- https://github.com/sirfergy/HomeAssistantWalkingPad (GPL-3.0): lists the BA06-B1 as supported; single-client note. Facts only.
- https://github.com/azmke/pitpat-treadmill-control (MIT): `src/bluetooth_manager.py`, `treadmill_controller.py`,
  `treadmill_data.py`. Source of the FF01/FF02 pair, the `4D <direction> <counter> <len>` wrapper, the heartbeat body, the
  strip-4-bytes status layout and thousandths of km/h. Re-implemented in Kotlin, no code copied; notice in
  THIRD_PARTY_NOTICES.md.
- qdomyos-zwift (GPL-3.0): reports the service as `FFFF` and speeds in hundredths for some pads. Facts only, passed
  on second hand; its code was not read or copied.
- pacekeeper (GPL): facts only, same as above.
