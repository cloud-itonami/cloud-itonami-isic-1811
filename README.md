# cloud-itonami-1811

Open Business Blueprint for **ISIC Rev.5 1811**: printing (commercial
press production including color-managed offset, digital and
flexographic print runs, with finishing and binding).

This repository designs a forkable OSS business for community
printing operations: color-management and print-specification-scope
management, robotics-assisted press-feed, quality-inspection and
finishing/binding, and print-run/quality records — run by a qualified
printer so a print shop keeps its own color-certification and print
history instead of renting a closed print-production platform.

## Scope note: printing, not printing-support services or publishing

`cloud-itonami-isic-1812` ("Service activities related to printing" --
pre-press services such as plate-making, and post-press services such
as binding performed as an independent third-party service, not
run as part of an in-house press operation) remains a separate,
untouched vertical in this fleet's own ISIC coverage. This repository
is deliberately scoped to the press-production business itself:
operating printing presses and integrated finishing/binding
equipment. Printing carries its own quality and compliance regime:
ISO 12647 process-control standards for color management; G7 Master
Certification from Idealliance for print-quality qualification;
OSHA machine-guarding requirements specific to printing-press
operation; environmental/VOC regulations governing ink and solvent
handling (EPA and EU industrial-emissions frameworks); and FSC
chain-of-custody certification where sustainably sourced paper stock
is claimed.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (press-feed,
in-line quality-inspection cameras, finishing/binding assist) operate
under an actor that proposes actions and an independent **Printing
Governor** that gates them. The governor never releases a print run
for delivery itself; `:high`/`:safety-critical` actions (a print run
outside verified color/specification scope, a delivery release
without a completed quality-inspection pass, a quality record without
verified evidence) require human sign-off.

## Core Contract

```text
intake + identity + color/print-specification scope + production order
        |
        v
Printing Advisor -> Printing Governor -> production record, inspection record, release, or human approval
        |
        v
robot actions (gated) + production record + quality record + audit ledger
```

No automated advice can release a print run for delivery the governor
refuses, advance production outside its verified color/specification
scope, or publish a quality record without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `1811`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/cae`](https://github.com/kotoba-lang/cae) — press-setup/color-calibration simulation evidence

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
