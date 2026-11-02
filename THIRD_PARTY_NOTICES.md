# Third-party notices

## pitpat-controller (MIT)

Source: https://github.com/Franrodr10/pitpat-controller

The Bluetooth protocol layer, the safety rules and the GATT transport of Stride are ported from pitpat-controller,
with the package renamed to `io.github.rickpro.stride` and every string translated to English. Ported files:

- `core/.../protocol/PitPatProtocol.kt`, `TreadmillProtocol.kt`, `FrameReassembler.kt` (and their tests)
- `core/.../control/CommandScheduler.kt`, `SessionController.kt`, `StepEstimator.kt` (and their tests)
- `app/.../ble/BlePermissions.kt`, `BleScanner.kt`, `GattConnection.kt`
- `app/.../session/TreadmillManager.kt`, `Settings.kt`, `Haptics.kt`, `DiagnosticsLog.kt`

Its license, reproduced in full:

```
MIT License

Copyright (c) 2026 Francisco Rodríguez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## pitpat-treadmill-control (MIT)

Source: https://github.com/azmke/pitpat-treadmill-control

The `FF01`/`FF02` transport of Stride follows this project: the characteristic pair, the 4-byte header
`4D <direction> <counter> <length>` with one shared write counter, the heartbeat body, the heartbeat after each notification,
the status layout after stripping the header, and speeds in thousandths of km/h. It is re-implemented in Kotlin from
reading `src/bluetooth_manager.py`, `src/treadmill_controller.py` and `src/treadmill_data.py`; no code is copied.
The notice is included because the design is taken from it. See `core/.../protocol/PitPatLink.kt`,
`PitPatVariant.kt` and `docs/protocol.md`.

Its license, reproduced in full:

```
MIT License

Copyright (c) 2025 Alexander

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Protocol research (facts only, no code copied)

- https://github.com/Razzolate99/pitpat-treadmill (no license file; used for protocol facts only, incl. the beeper frame)
- https://github.com/sirfergy/HomeAssistantWalkingPad (GPL-3.0; not copied)
- qdomyos-zwift and pacekeeper (GPL; second-hand facts only: the `FFFF` service name and speeds in hundredths for some
  pads. Their code was not read or copied.)
