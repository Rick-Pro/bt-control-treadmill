# PitPat BLE protocol (as used by Stride)

Compiled from open source work listed at the end. The frames below are what Stride sends and parses
(`core/.../protocol/PitPatProtocol.kt`). Ricardo's pad is a PitPat HY-BA06B1 that advertises as "BA06-73F6".
The protocol was verified by others on PitPat-T01 family pads with the same service; the first device test
(docs/plan.md section 8) confirms it on this unit.

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

## Status frame (52 bytes, notified on FBA2)
Offsets are 0-based, values big-endian. The length byte is `34` (52) and the last byte `43`.
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
| 25 | firmware version |
| 26 | flags: `01` Wi-Fi connected; bits `18` = run state (`18` starting countdown, `08` running, `10` paused, `00` stopped); `80` panel shows imperial |
| 27 to 28 | max speed, u16, thousandths of km/h |
| 29 | max incline (0 = none) |
| 47 | bit `01` = beeper on (reported by one implementation, unverified) |
| 50 | XOR checksum of bytes 1 to 49 |
| 51 | `43` |

## Quirks
- If the Bluetooth link drops while the belt moves, **the belt keeps running at the same speed**. The physical
  remote still works. Stride vibrates and shows a banner; it never pretends to have stopped the belt.
- One client at a time. Close the PitPat app.
- Split notifications: with the default 23-byte MTU a 52-byte status arrives in pieces. The reassembler uses the
  length byte and drops data older than 300 ms.
- No handshake or unlock is needed on the FBA0 transport. Other PitPat units use `FF01`/`FF02` or `FFF1`/`FFF2`
  with a different header; those are not supported.

## Sources
- https://github.com/Franrodr10/pitpat-controller (MIT): Kotlin implementation, `docs/PROTOCOL.md`, tests. Ported.
- https://github.com/Razzolate99/pitpat-treadmill (no license): Python frame builder and parser, beeper frames. Facts only.
- https://github.com/sirfergy/HomeAssistantWalkingPad (GPL-3.0): lists the BA06-B1 as supported; single-client note. Facts only.
- https://github.com/azmke/pitpat-treadmill-control (MIT): status start byte capture. Facts only.
