# Research — the Renogy Rover and its Modbus protocol

Verified facts about the piece this project does not own: the Renogy Rover 40A MPPT charge
controller, its RS232/RJ12 port and the Modbus register map it exposes. About *it*, never about
us: our choices are `decisions.md`, and a sentence starting "we chose" is a `D_` entry. When a
design argument needs "does the device actually do X?", the answer belongs here and is cited
from wherever it is used, rather than re-derived.

Every claim carries provenance: **[docs]** (stated in the Rover Modbus specification —
`docs/ROVER MODBUS.DOCX.pdf`), **[src]** (read from another implementation), **[verified]** (run
and observed against a real unit — say when), or **[unverified]** (inferred or second-hand; a
hypothesis until a run turns it into a fact). Treat `[unverified]` as exactly that: don't build
a design on one without saying so.

Checked against a Rover 40A unless a claim says otherwise. The device reports its own firmware
and hardware version in `SystemInfo`; name them on any claim that turns out to vary by unit.

---

## The wire: RS232 and the Modbus framing

- The port runs at **9600 baud, 8 data bits, no parity, no flow control**; RTS, CTS, XON and
  XOFF are all off. **[verified]**
- Only function code `0x03` (ReadRegister) is used. A request is 8 bytes — address, `0x03`,
  start register (word), word count (word, `0x0001..0x007D`), CRC16 — and the response is
  address, `0x03`, byte count, the data, CRC16. **[docs]**
- **Register addresses and word counts go out big-endian; the CRC16 goes out and comes back
  low byte first.** **[verified]**
- An error response replaces the function code with `0x83` and carries a one-byte exception code
  in place of the byte count, followed by its own CRC16. **[docs]**
- Responses carry no framing beyond the declared byte count, so a partial read leaves the pipe
  offset and every later response decodes as garbage. Draining before a fresh request is the
  only resynchronisation available. **[verified]**
- A device address must be `0x01..0xF7`. `0x00` is the broadcast address: every slave acts on
  it, none replies. **[docs]**
- Once the port has timed out, subsequent reads keep timing out indefinitely rather than
  recovering; closing and reopening the port clears it. Seen repeatedly on a Raspberry Pi with
  a USB/RS232 adapter. **[verified, solar-controller-client issue 10]**
- Rebranded Rovers speak the same protocol and register map — a Biltema MPPT 20A controller
  works unchanged. **[verified]**

## Registers that do not mean what the specification says

- **The daily counters are not reset at midnight** but at an arbitrary device-local moment —
  9:17am on the author's unit, stable from day to day. Until that moment, every "daily" register
  still describes the previous day. **[verified]** This is what `D_fix_daily_stats` corrects.
- Daily power generation (byte 16 of the block at `0x010B`) and cumulative power generation
  (byte 14 of the block at `0x0115`) are specified as **kWh/10000**, but the values contradict
  both that unit and the manual's own worked example — a 24 V system reporting 2 charging
  amp-hours gives 5 here. They are read as **Wh**, the only unit that makes the figures
  consistent. **[unverified]**
- The specification's examples for the power-status block at `0x0100` omit
  `chargingCurrentToBattery` entirely and place battery and controller temperature as separate
  words at `0x102` and `0x103`. The code instead reads the charging current at `0x102` and both
  temperatures out of the single word at `0x103`, which is what produces sane values — the
  discrepancy is unresolved and carries a `@todo` in `RenogyModbusClient.getPowerStatus`.
  **[unverified]**
- `ChargingState` value 1 (`ChargingActivated`) has no described meaning; the README's table
  leaves it blank. **[unverified]**
